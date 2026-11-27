package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(
    state: MainUiState,
    onConnectClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onPingClick: () -> Unit,
    onServerClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FanoosColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Top-center: Big Connect / Disconnect button (with mascot when relevant)
        ConnectSection(
            isConnected = state.isConnected,
            isConnecting = state.isConnecting,
            selectedServerName = state.selectedServerName,
            guideState = state.guideState,
            onConnectClick = onConnectClick,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Mascot guide: tells the user what to do next
        MascotGuideBubble(
            guideState = state.guideState,
            hasInternetError = state.hasInternetError,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Status banner (if any)
        if (!state.statusMessage.isNullOrBlank()) {
            Surface(
                color = if (state.isStatusError) Color(0xFFFEE2E2) else Color(0xFFE0F2FE),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.statusMessage,
                    color = if (state.isStatusError) FanoosColors.Error else FanoosColors.PrimaryDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Middle: Two side-by-side buttons (Update Sub & Ping Test)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onUpdateClick,
                enabled = !state.isUpdatingSub,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, FanoosColors.Primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = FanoosColors.Card,
                    contentColor = FanoosColors.PrimaryDark,
                ),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (state.isUpdatingSub) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = FanoosColors.Primary,
                    )
                } else {
                    Text(
                        text = "بروزرسانی لیست",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            Button(
                onClick = onPingClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isTestingPing) FanoosColors.Amber else FanoosColors.Primary,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (state.isTestingPing) {
                        state.pingProgressText?.let { "توقف ($it)" } ?: "در حال تست..."
                    } else {
                        "تست پینگ"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Bottom-center: Server list
        if (state.servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.isUpdatingSub) {
                        "در حال دریافت لیست سرورها..."
                    } else {
                        "لیست سرورها خالی است.\nروی «بروزرسانی لیست» بزنید."
                    },
                    color = FanoosColors.TextSub,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.servers, key = { it.guid }) { server ->
                    ServerItemCard(
                        server = server,
                        onClick = { onServerClick(server.guid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectSection(
    isConnected: Boolean,
    isConnecting: Boolean,
    selectedServerName: String,
    guideState: GuideState,
    onConnectClick: () -> Unit,
) {
    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> FanoosColors.Connected
            isConnecting -> FanoosColors.Amber
            else -> FanoosColors.Primary
        },
        label = "connectButtonColor",
    )

    val showPeek = guideState == GuideState.STEP_3_READY_BEHIND_CONNECT && !isConnecting
    val showSit = guideState == GuideState.CONNECTED_SITTING_NEXT_TO_BUTTON ||
        guideState == GuideState.DISCONNECTED_SITTING_RECONNECT

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "فانوس",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = FanoosColors.PrimaryDark,
        )

        Spacer(modifier = Modifier.height(if (showPeek) 4.dp else 16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showPeek) 228.dp else 164.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(164.dp)
                    .shadow(10.dp, CircleShape)
                    .background(buttonColor, CircleShape)
                    .clickable(onClick = onConnectClick),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "در حال اتصال...",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = if (isConnected) "متصل" else "اتصال",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isConnected) "برای قطع ضربه بزنید" else "برای شروع ضربه بزنید",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // Mascot peeking over the top of the button, pointing down at it
            if (showPeek) {
                MascotPeek(modifier = Modifier.align(Alignment.TopCenter))
            }

            // Mascot sitting on the left of the button, pointing right at it
            if (showSit) {
                MascotSit(modifier = Modifier.align(AbsoluteAlignment.BottomLeft))
            }
        }

        if (selectedServerName.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "سرور انتخابشده: $selectedServerName",
                color = FanoosColors.TextSub,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServerItemCard(
    server: ServerRow,
    onClick: () -> Unit,
) {
    val bgColor = if (server.isSelected) FanoosColors.SelectedBg else FanoosColors.Card
    val border = if (server.isSelected) {
        BorderStroke(2.dp, FanoosColors.Primary)
    } else {
        BorderStroke(1.dp, FanoosColors.Divider)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    color = FanoosColors.TextMain,
                    fontSize = 18.sp,
                    fontWeight = if (server.isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = server.protocol,
                    color = FanoosColors.TextSub,
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.padding(horizontal = 6.dp))

            PingBadge(pingMs = server.pingMs)
        }
    }
}

@Composable
private fun PingBadge(pingMs: Long) {
    val (text, color) = when {
        pingMs > 0L -> "${pingMs} ms" to when {
            pingMs < 400 -> FanoosColors.ConnectedDark
            pingMs < 1000 -> FanoosColors.Amber
            else -> FanoosColors.Error
        }
        pingMs < 0L -> "ناموفق" to FanoosColors.Error
        else -> "—" to FanoosColors.TextSub
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
