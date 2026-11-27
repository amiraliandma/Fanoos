package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

private data class GuideContent(
    @DrawableRes val pose: Int,
    val text: String,
    val isError: Boolean = false,
    val showImageInBubbleRow: Boolean = true,
)

private fun guideContent(guideState: GuideState, hasInternetError: Boolean): GuideContent =
    when (guideState) {
        GuideState.STEP_1_UPDATE_SUB -> GuideContent(
            pose = R.drawable.mascot_point,
            text = "سلام! قدم اول: روی «بروزرسانی لیست» بزن تا سرورها دریافت بشن",
        )

        GuideState.UPDATING_SUB -> GuideContent(
            pose = R.drawable.mascot_point,
            text = "دارم لیست سرورها رو میگیرم، یه لحظه صبر کن...",
        )

        GuideState.UPDATE_FAILED_CHECK_INTERNET -> GuideContent(
            pose = R.drawable.mascot_worried,
            text = if (hasInternetError) {
                "انگار اینترنتت وصل نیست! اتصال اینترنت رو بررسی کن و دوباره «بروزرسانی لیست» رو بزن"
            } else {
                "لیست دریافت نشد؛ اتصال اینترنت رو بررسی کن لطفاً و دوباره امتحان کن"
            },
            isError = true,
        )

        GuideState.STEP_2_TEST_PING -> GuideContent(
            pose = R.drawable.mascot_point,
            text = "عالیه! قدم دوم: حالا «تست پینگ» رو بزن تا بهترین سرور پیدا بشه",
        )

        GuideState.TESTING_PING -> GuideContent(
            pose = R.drawable.mascot_point,
            text = "دارم سرورها رو تست میکنم، کمی صبر کن...",
        )

        GuideState.STEP_3_READY_BEHIND_CONNECT -> GuideContent(
            pose = R.drawable.mascot_peek,
            text = "همهچی آمادهست! قدم آخر: دکمهٔ بزرگ «اتصال» رو بزن",
            showImageInBubbleRow = false,
        )

        GuideState.PING_ALL_FAILED_RETRY -> GuideContent(
            pose = R.drawable.mascot_worried,
            text = "هیچ سروری جواب نداد! اینترنت رو بررسی کن و دوباره «تست پینگ» رو بزن",
            isError = true,
        )

        GuideState.CONNECTED_SITTING_NEXT_TO_BUTTON -> GuideContent(
            pose = R.drawable.mascot_sit,
            text = "وصل شدی! خیالت راحت، من همینجا کنارتم",
            showImageInBubbleRow = false,
        )

        GuideState.DISCONNECTED_SITTING_RECONNECT -> GuideContent(
            pose = R.drawable.mascot_sit,
            text = "اتصال قطع شد. هر وقت خواستی دوباره دکمهٔ «اتصال» رو بزن",
            showImageInBubbleRow = false,
        )
    }

@Composable
private fun rememberBobOffset(amplitudePx: Float = 10f): Float {
    val transition = rememberInfiniteTransition(label = "mascotBob")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = amplitudePx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mascotBobOffset",
    )
    return offset
}

@Composable
private fun MascotImage(
    @DrawableRes res: Int,
    height: Dp,
    bobPx: Float,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .graphicsLayer { translationY = -bobPx },
    )
}

@Composable
private fun SpeechBubble(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (isError) Color(0xFFFFF1F2) else FanoosColors.Card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, if (isError) FanoosColors.Error else FanoosColors.Primary),
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = if (isError) FanoosColors.Error else FanoosColors.TextMain,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Mascot + speech bubble row that explains the next step to the user. */
@Composable
fun MascotGuideBubble(
    guideState: GuideState,
    hasInternetError: Boolean,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = guideState to hasInternetError,
        label = "mascotGuide",
        modifier = modifier,
    ) { (gs, offline) ->
        val content = guideContent(gs, offline)
        val bob = rememberBobOffset()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (content.showImageInBubbleRow) {
                MascotImage(content.pose, 104.dp, bob)
            }
            SpeechBubble(
                text = content.text,
                isError = content.isError,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Mascot peeking over the Connect button, pointing at it with both hands. */
@Composable
fun MascotPeek(modifier: Modifier = Modifier) {
    val bob = rememberBobOffset(amplitudePx = 8f)
    MascotImage(R.drawable.mascot_peek, 120.dp, bob, modifier = modifier)
}

/** Mascot sitting cross-legged next to the Connect button. */
@Composable
fun MascotSit(modifier: Modifier = Modifier) {
    MascotImage(R.drawable.mascot_sit, 84.dp, 0f, modifier = modifier)
}
