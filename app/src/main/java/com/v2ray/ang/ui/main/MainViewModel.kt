package com.v2ray.ang.ui.main

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServerRow(
    val guid: String,
    val name: String,
    val protocol: String,
    val pingMs: Long,
    val isSelected: Boolean,
)

/** Mascot guide steps shown on the main screen. */
enum class GuideState {
    STEP_1_UPDATE_SUB,
    UPDATING_SUB,
    UPDATE_FAILED_CHECK_INTERNET,
    STEP_2_TEST_PING,
    TESTING_PING,
    STEP_3_READY_BEHIND_CONNECT,
    PING_ALL_FAILED_RETRY,
    CONNECTED_SITTING_NEXT_TO_BUTTON,
    DISCONNECTED_SITTING_RECONNECT,
}

data class MainUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val servers: List<ServerRow> = emptyList(),
    val selectedGuid: String? = null,
    val selectedServerName: String = "",
    val isUpdatingSub: Boolean = false,
    val isTestingPing: Boolean = false,
    val pingProgressText: String? = null,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false,
    val guideState: GuideState = GuideState.STEP_1_UPDATE_SUB,
    val hasInternetError: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var statusClearJob: Job? = null
    private var receiverRegistered = false

    /** In-memory only: resets when the app process is restarted. */
    private var hasConnectedInSession: Boolean = false

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val key = intent?.getIntExtra("key", 0) ?: return
            when (key) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    hasConnectedInSession = true
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            isConnecting = false,
                            guideState = GuideState.CONNECTED_SITTING_NEXT_TO_BUTTON,
                        )
                    }
                    if (key == AppConfig.MSG_STATE_START_SUCCESS) {
                        showStatus("اتصال با موفقیت برقرار شد")
                    }
                }

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            guideState = if (hasConnectedInSession) {
                                GuideState.DISCONNECTED_SITTING_RECONNECT
                            } else {
                                it.guideState
                            },
                        )
                    }
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            guideState = if (hasConnectedInSession) {
                                GuideState.DISCONNECTED_SITTING_RECONNECT
                            } else {
                                GuideState.STEP_3_READY_BEHIND_CONNECT
                            },
                        )
                    }
                    val detail = intent.getStringExtra("content")?.trim().orEmpty()
                    val msg = if (detail.isNotEmpty()) {
                        "اتصال برقرار نشد ($detail)"
                    } else {
                        "اتصال برقرار نشد؛ سرور دیگری را امتحان کنید"
                    }
                    showStatus(msg, isError = true)
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val progress = intent.getStringExtra("content")
                    _uiState.update { it.copy(pingProgressText = progress) }
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val result = intent.serializable<RealPingResult>("content") ?: return
                    _uiState.update { state ->
                        val updated = state.servers.map { row ->
                            if (row.guid == result.guid) row.copy(pingMs = result.delayMillis) else row
                        }
                        state.copy(servers = updated)
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    _uiState.update { it.copy(isTestingPing = false, pingProgressText = null) }
                    reloadServers(autoPickFastest = !_uiState.value.isConnected)
                    val healthyCount = _uiState.value.servers.count { it.pingMs > 0L }
                    if (!_uiState.value.isConnected) {
                        if (healthyCount > 0) {
                            _uiState.update { it.copy(guideState = GuideState.STEP_3_READY_BEHIND_CONNECT) }
                            showStatus("تست پینگ کامل شد؛ بهترین سرور انتخاب شد")
                        } else {
                            _uiState.update { it.copy(guideState = GuideState.PING_ALL_FAILED_RETRY) }
                            showStatus(
                                "هیچ سروری پاسخ نداد؛ اینترنت را بررسی و دوباره تست کنید",
                                isError = true
                            )
                        }
                    } else {
                        showStatus("تست پینگ کامل شد و سریعترین سرورها بالا آمدند")
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> {
                    _uiState.update {
                        it.copy(
                            isTestingPing = false,
                            pingProgressText = null,
                            guideState = if (it.guideState == GuideState.TESTING_PING) {
                                GuideState.STEP_2_TEST_PING
                            } else {
                                it.guideState
                            },
                        )
                    }
                }
            }
        }
    }

    init {
        registerReceiver()
        SettingsManager.ensureFanoosSubscription()
        reloadServers()
        syncServiceState()
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(
            getApplication(),
            messageReceiver,
            filter,
            Utils.receiverFlags()
        )
        receiverRegistered = true
    }

    fun syncServiceState() {
        MessageHelper.sendMsg2ServiceForResult(
            getApplication(),
            AppConfig.MSG_REGISTER_CLIENT,
            ""
        ) { handled ->
            if (!handled) {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        guideState = if (
                            hasConnectedInSession &&
                            it.guideState == GuideState.CONNECTED_SITTING_NEXT_TO_BUTTON
                        ) {
                            GuideState.DISCONNECTED_SITTING_RECONNECT
                        } else {
                            it.guideState
                        },
                    )
                }
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun reloadServers(autoPickFastest: Boolean = false) {
        val guids = MmkvManager.decodeServerList(AppConfig.FANOOS_SUB_ID)
        var selectedGuid = MmkvManager.getSelectServer()

        if (autoPickFastest && guids.isNotEmpty()) {
            val fastest = guids.firstOrNull { guid ->
                val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                delay > 0L
            }
            if (fastest != null) {
                selectedGuid = fastest
                MmkvManager.setSelectServer(fastest)
            }
        }

        if (selectedGuid.isNullOrEmpty() || selectedGuid !in guids) {
            selectedGuid = guids.firstOrNull()
            if (selectedGuid != null) {
                MmkvManager.setSelectServer(selectedGuid)
            }
        }

        val rows = guids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            val label = when (profile.configType) {
                EConfigType.VLESS -> "VLESS"
                EConfigType.VMESS -> "VMess"
                EConfigType.SHADOWSOCKS -> "Shadowsocks"
                EConfigType.HYSTERIA2 -> "Hysteria2"
                EConfigType.TROJAN -> "Trojan"
                EConfigType.WIREGUARD -> "WireGuard"
                else -> profile.configType.name
            }
            ServerRow(
                guid = guid,
                name = profile.remarks.ifBlank { "سرور" },
                protocol = label,
                pingMs = delay,
                isSelected = guid == selectedGuid,
            )
        }

        val selectedName = rows.firstOrNull { it.isSelected }?.name.orEmpty()
        _uiState.update {
            it.copy(
                servers = rows,
                selectedGuid = selectedGuid,
                selectedServerName = selectedName,
            )
        }
    }

    fun selectServer(guid: String) {
        if (guid == _uiState.value.selectedGuid) return
        MmkvManager.setSelectServer(guid)
        reloadServers()

        if (_uiState.value.isConnected) {
            _uiState.update { it.copy(isConnecting = true) }
            LauncherManager.restartService(getApplication())
            showStatus("در حال تغییر سرور...")
        }
    }

    fun startVpn() {
        val guid = _uiState.value.selectedGuid ?: _uiState.value.servers.firstOrNull()?.guid
        if (guid == null) {
            showStatus("ابتدا روی «بروزرسانی لیست» بزنید تا سرورها دریافت شوند", isError = true)
            return
        }
        MmkvManager.setSelectServer(guid)
        _uiState.update { it.copy(isConnecting = true) }
        val started = LauncherManager.startService(getApplication(), guid)
        if (!started) {
            _uiState.update { it.copy(isConnecting = false, isConnected = false) }
            showStatus("شروع اتصال ناموفق بود", isError = true)
        }
    }

    fun stopVpn() {
        if (_uiState.value.isConnected) hasConnectedInSession = true
        _uiState.update {
            it.copy(
                isConnecting = false,
                guideState = if (hasConnectedInSession) {
                    GuideState.DISCONNECTED_SITTING_RECONNECT
                } else {
                    it.guideState
                },
            )
        }
        LauncherManager.stopService(getApplication())
        showStatus("اتصال قطع شد")
    }

    fun updateSubscription(silentStart: Boolean = false) {
        if (_uiState.value.isUpdatingSub) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingSub = true,
                    hasInternetError = false,
                    guideState = if (!it.isConnected) GuideState.UPDATING_SUB else it.guideState,
                )
            }
            if (!silentStart) {
                showStatus("در حال دریافت لیست جدید سرورها...")
            }

            val result = withContext(Dispatchers.IO) {
                SettingsManager.ensureFanoosSubscription()
                val sub = MmkvManager.decodeSubscription(AppConfig.FANOOS_SUB_ID)
                if (sub != null) {
                    AngConfigManager.updateConfigViaSub(
                        SubscriptionCache(AppConfig.FANOOS_SUB_ID, sub)
                    )
                } else {
                    null
                }
            }

            _uiState.update { it.copy(isUpdatingSub = false) }
            reloadServers()

            if (result != null && result.configCount > 0) {
                if (!_uiState.value.isConnected) {
                    _uiState.update { it.copy(guideState = GuideState.STEP_2_TEST_PING) }
                }
                showStatus("${result.configCount} سرور با موفقیت بروزرسانی شد")
            } else {
                val offline = !isInternetAvailable()
                if (!_uiState.value.isConnected) {
                    _uiState.update {
                        it.copy(
                            guideState = GuideState.UPDATE_FAILED_CHECK_INTERNET,
                            hasInternetError = offline,
                        )
                    }
                }
                showStatus("اتصال اینترنت رو بررسی کن لطفاً", isError = true)
            }
        }
    }

    fun togglePingTest() {
        val ctx = getApplication<Application>()
        if (_uiState.value.isTestingPing) {
            MessageHelper.sendMsg2TestService(
                ctx,
                TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
            )
            _uiState.update {
                it.copy(
                    isTestingPing = false,
                    pingProgressText = null,
                    guideState = if (it.guideState == GuideState.TESTING_PING) {
                        GuideState.STEP_2_TEST_PING
                    } else {
                        it.guideState
                    },
                )
            }
            return
        }

        val guids = MmkvManager.decodeServerList(AppConfig.FANOOS_SUB_ID)
        if (guids.isEmpty()) {
            if (!_uiState.value.isConnected) {
                _uiState.update { it.copy(guideState = GuideState.STEP_1_UPDATE_SUB) }
            }
            showStatus("لیست سرورها خالی است؛ ابتدا بروزرسانی کنید", isError = true)
            return
        }

        MmkvManager.clearAllTestDelayResults(guids)
        reloadServers()
        _uiState.update {
            it.copy(
                isTestingPing = true,
                pingProgressText = null,
                guideState = if (!it.isConnected) GuideState.TESTING_PING else it.guideState,
            )
        }

        MessageHelper.sendMsg2TestService(
            ctx,
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START,
                subscriptionId = AppConfig.FANOOS_SUB_ID,
                serverGuids = guids
            )
        )
    }

    private fun showStatus(message: String, isError: Boolean = false) {
        statusClearJob?.cancel()
        _uiState.update { it.copy(statusMessage = message, isStatusError = isError) }
        statusClearJob = viewModelScope.launch {
            delay(3500L)
            _uiState.update {
                if (it.statusMessage == message) it.copy(statusMessage = null) else it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (receiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(messageReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }
}
