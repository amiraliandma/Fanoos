package com.v2ray.ang.enums

import androidx.annotation.StringRes
import com.v2ray.ang.R

enum class NotificationChannelType(
    val channelId: String,
    @StringRes val channelNameRes: Int,
    val notificationId: Int
) {
    CORE_TEST(
        channelId = "fanoos_ping_channel",
        channelNameRes = R.string.notification_channel_test,
        notificationId = 12
    )
}
