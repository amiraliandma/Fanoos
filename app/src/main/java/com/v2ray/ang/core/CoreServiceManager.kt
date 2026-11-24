package com.v2ray.ang.core

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.delay
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.NetworkMonitor
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.lang.ref.SoftReference
import java.net.InetSocketAddress

object CoreServiceManager {

    private val coreController: CoreController by lazy { CoreNativeManager.newCoreController(CoreCallback()) }
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null

    @Volatile
    private var networkMonitor: NetworkMonitor? = null
    private val connectionTestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isReloading = false

    private var currentVpnInterface: ParcelFileDescriptor? = null

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            CoreNativeManager.initCoreEnv(service)
            if (service != null && processFinder == null) {
                processFinder = XrayProcessFinder(service)
                coreController.registerProcessFinder(processFinder)
            }
        }

    fun isRunning() = coreController.isRunning

    fun isServiceRunning() = ReloadOutcome.serviceRuns(
        coreRunning = isRunning(),
        reloading = isReloading,
        stoppedMeanwhile = networkMonitor == null,
    )

    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        if (isRunning()) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService() ?: run {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        return try {
            doStartCoreLoop(service, vpnInterface)
            true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
            NotificationManager.cancelNotification()
            false
        }
    }

    private class StartFailure(message: String) : RuntimeException(message)

    private fun userFacingReason(e: Exception): String = if (e is StartFailure) e.message.orEmpty() else ""

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
        ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())

        currentVpnInterface = vpnInterface
        launchCore(service, vpnInterface)
        startNetworkMonitor(service)
    }

    @Throws(Exception::class)
    private fun launchCore(
        service: Service,
        vpnInterface: ParcelFileDescriptor?,
        isReload: Boolean = false,
        goesOn: () -> Boolean = { true },
    ): Boolean {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        val result = CoreConfigManager.getV2rayConfig(service, guid)
        if (!result.status) {
            if (result.localizedError) throw StartFailure(result.errorMessage)
            error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
        }
        if (!goesOn()) return false

        if (!isReload) {
            MessageHelper.sendMsg2TestService(service, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL))
        }

        currentConfig = config
        val tunFd = vpnInterface?.fd ?: 0
        NotificationManager.showNotification(currentConfig)
        coreController.startLoop(result.content, tunFd)

        if (!isRunning()) {
            error("Core failed to start")
        }

        if (!isReload) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        }
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
        return true
    }

    fun stopCoreLoop(): Boolean {
        connectionTestScope.coroutineContext.cancelChildren()
        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null
        currentVpnInterface = null
        NotificationManager.cancelNotification()

        if (isRunning()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
            }
        }

        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    private fun startNetworkMonitor(service: Service) {
        if (networkMonitor != null) return

        val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val runMode = service.javaClass.simpleName
        networkMonitor = NetworkMonitor(
            connectivity = connectivity,
            onUnderlyingNetworksChanged = { networks -> serviceControl?.get()?.setUnderlyingNetworks(networks) },
            claimHandover = { monitor -> claimReload(askedBy = monitor) },
            onHandover = { monitor -> reloadCore(askedBy = monitor) },
            describe = { "$runMode, guid=${MmkvManager.getSelectServer()}" },
        ).also { it.register() }
    }

    private fun claimReload(askedBy: NetworkMonitor): Boolean =
        ReloadOutcome.handoverReloads(askedBy, current = networkMonitor, coreRunning = isRunning(), reloading = isReloading)
            .also { if (it) isReloading = true }

    private fun reloadCore(askedBy: NetworkMonitor): Boolean {
        val service = getService() ?: run {
            isReloading = false
            return false
        }

        try {
            val tunFd = currentVpnInterface
            connectionTestScope.coroutineContext.cancelChildren()
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload start...")
            coreController.stopLoop()
            launchCore(
                service,
                tunFd,
                isReload = true,
                goesOn = { ReloadOutcome.goesOn(askedBy = askedBy, current = networkMonitor) }
            )
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: reload failed: $message", e)
            if (networkMonitor === askedBy) {
                MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, userFacingReason(e))
            }
        } finally {
            isReloading = false
        }

        return when (ReloadOutcome.of(coreRunning = isRunning(), stoppedMeanwhile = networkMonitor == null)) {
            ReloadOutcome.KEEP_RUNNING -> true
            ReloadOutcome.STOP_SERVICE -> {
                stopServiceAfterFailedReload(askedBy)
                false
            }
            ReloadOutcome.RELEASE_CORES -> {
                releaseAfterStoppedReload(service)
                false
            }
        }
    }

    private fun stopServiceAfterFailedReload(monitor: NetworkMonitor) {
        val control = serviceControl?.get() ?: return
        val service = control.getService()
        ContextCompat.getMainExecutor(service).execute {
            if (isRunning() || networkMonitor !== monitor || serviceControl?.get() !== control) return@execute
            control.stopService()
        }
    }

    private fun releaseAfterStoppedReload(service: Service) {
        ContextCompat.getMainExecutor(service).execute {
            if (networkMonitor != null) return@execute
            NotificationManager.cancelNotification()
            try {
                coreController.stopLoop()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop core after stopped reload", e)
            }
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        }
    }

    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        if (!isRunning()) return emptyList()
        val payload = coreController.queryAllOutboundTrafficStats()
        val result = ArrayList<OutboundTrafficStat>()
        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach
            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach
            val value = parts[2].toLongOrNull() ?: return@forEach
            result.add(OutboundTrafficStat(tag = parts[0], direction = parts[1], value = value))
        }
        return result
    }

    private fun measureV2rayDelay(requestId: String) {
        val service = getService() ?: return
        if (!isRunning() || isReloading) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            return
        }

        connectionTestScope.coroutineContext.cancelChildren()
        connectionTestScope.launch {
            var time = -1L
            var errorStr = ""
            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                errorStr = e.message?.substringAfter("\":").orEmpty()
            }
            if (time == -1L) {
                ensureActive()
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    errorStr = e.message?.substringAfter("\":").orEmpty()
                }
            }
            ensureActive()
            val result = ConnectionTestResult(delayMillis = time, errorMessage = errorStr)
            withContext(Dispatchers.Main.immediate) {
                if (isRunning()) {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_RESULT, result, requestId)
                } else {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
                }
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            }
        }
    }

    private fun getService(): Service? = serviceControl?.get()?.getService()

    private class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(
            network: String,
            srcIP: String,
            srcPort: Long,
            destIP: String,
            destPort: Long
        ): Long {
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }
            if (destIP.isBlank() || destPort == 0L) return -1L
            return try {
                cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
            } catch (_: Exception) {
                -1L
            }
        }
    }

    private class ReceiveMessageHandler : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                    val msg = if (isServiceRunning()) AppConfig.MSG_STATE_RUNNING else AppConfig.MSG_STATE_NOT_RUNNING
                    MessageHelper.sendMsg2UI(serviceControl.getService(), msg, "")
                }

                AppConfig.MSG_STATE_STOP -> {
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            serviceControl.stopService()
                            delay(400L)
                            LauncherManager.startService(serviceControl.getService())
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                    measureV2rayDelay(intent.getStringExtra("content").orEmpty())
                }
            }
        }
    }
}
