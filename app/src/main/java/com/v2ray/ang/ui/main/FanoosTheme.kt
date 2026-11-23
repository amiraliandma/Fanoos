package com.v2ray.ang.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

object FanoosColors {
    val Background = Color(0xFFF5F8FC)
    val Card = Color(0xFFFFFFFF)
    val Primary = Color(0xFF0284C7)
    val PrimaryDark = Color(0xFF075985)
    val Connected = Color(0xFF16A34A)
    val ConnectedDark = Color(0xFF15803D)
    val Amber = Color(0xFFF59E0B)
    val TextMain = Color(0xFF1E293B)
    val TextSub = Color(0xFF64748B)
    val Error = Color(0xFFDC2626)
    val SelectedBg = Color(0xFFE0F2FE)
    val Divider = Color(0xFFE2E8F0)
}

private val FanoosColorScheme = lightColorScheme(
    primary = FanoosColors.Primary,
    onPrimary = Color.White,
    background = FanoosColors.Background,
    onBackground = FanoosColors.TextMain,
    surface = FanoosColors.Card,
    onSurface = FanoosColors.TextMain,
    error = FanoosColors.Error,
)

private val FanoosTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun FanoosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FanoosColorScheme, typography = FanoosTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
