package com.v2ray.ang.dto

import android.content.Context
import androidx.annotation.StringRes
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileEntry
import com.v2ray.ang.enums.CoreResolvedType

data class CoreConfigContext(
    val context: Context,
    val guid: String,
    val isCustom: Boolean = false,
    val resolvedOutbounds: List<ResolvedOutbound> = emptyList(),
    val routingDomainRules: List<RoutingDomainRule> = emptyList(),
    val unresolvedTarget: UnresolvedName? = null,
) {
    data class ResolvedOutbound(
        val tag: String,
        val entry: ProfileEntry,
        val resolvedEntries: List<ProfileEntry>,
        val resolvedType: CoreResolvedType,
        val unresolvedHop: UnresolvedName? = null,
    )

    data class UnresolvedName(val name: String, val reason: Reason) {
        enum class Reason(@StringRes val message: Int) {
            NOT_FOUND(R.string.toast_services_failure),
            SEVERAL(R.string.toast_services_failure),
            NO_SERVER(R.string.toast_services_failure),
            GROUP_AS_FALLBACK(R.string.toast_services_failure),
            CUSTOM_AS_FALLBACK(R.string.toast_services_failure),
            GROUP_AS_HOP(R.string.toast_services_failure),
            CHAIN_AS_HOP(R.string.toast_services_failure),
            CUSTOM_AS_HOP(R.string.toast_services_failure),
        }
    }

    data class RoutingDomainRule(
        val domain: List<String>,
        val outboundTag: String,
    )
}
