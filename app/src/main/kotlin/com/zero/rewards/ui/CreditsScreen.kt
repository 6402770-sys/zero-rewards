package com.zero.rewards.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zero.rewards.BuildConfig
import com.zero.rewards.R
import com.zero.rewards.ui.theme.ZeroTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object Credits {
    const val DISPLAY_NAME = "Zero"
    const val GITHUB_USER = "6402770-sys"
    const val GITHUB_URL = "https://github.com/$GITHUB_USER"
}

@Composable
fun CreditsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ZeroTopBar(title = "Credits", onBack = onDone) },
    ) { padding ->
        StaggeredColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            spacing = 24.dp,
        ) {
            AppHero()
            Column {
                SectionLabel("Developer", Modifier.padding(start = 4.dp, bottom = 10.dp))
                DeveloperCard(onOpen = { openUrl(context, Credits.GITHUB_URL) })
            }
            Column {
                SectionLabel("Design", Modifier.padding(start = 4.dp, bottom = 10.dp))
                ClaudeCard()
            }
            Text(
                "Thanks for using Zero Rewards.",
                style = MaterialTheme.typography.bodySmall,
                color = ZeroTheme.extras.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppHero() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
    ) {
        ZeroMark(size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text("Zero Rewards", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = ZeroTheme.extras.muted,
        )
    }
}

@Composable
private fun DeveloperCard(onOpen: () -> Unit) {
    val extras = ZeroTheme.extras
    val avatar = rememberGitHubAvatar(Credits.GITHUB_USER)
    val source = remember { MutableInteractionSource() }
    ZeroCard(Modifier.pressScale(source, 0.98f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onOpen)
                .padding(16.dp),
        ) {
            Crossfade(targetState = avatar, animationSpec = tween(400), label = "avatar") { image ->
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "GitHub avatar of ${Credits.DISPLAY_NAME}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(1.dp, extras.hairlineStrong, CircleShape),
                    )
                } else {
                    Monogram(Credits.DISPLAY_NAME, active = true, highlighted = false, modifier = Modifier.size(56.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Credits.DISPLAY_NAME, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Developer · GitHub",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.muted,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open GitHub profile",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ClaudeCard() {
    val glow = ZeroTheme.extras.accentGlow
    ZeroCard {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(glow, Color.Transparent),
                            center = Offset(size.width / 2, size.height * 0.55f),
                            radius = size.width * 0.55f,
                        ),
                    )
                },
        ) {
            ClaudeSprite()
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text("Claude", style = MaterialTheme.typography.titleMedium)
            Text(
                "Code, UI design & animations · by Anthropic",
                style = MaterialTheme.typography.bodySmall,
                color = ZeroTheme.extras.muted,
            )
        }
    }
}

/** Floats gently with a shadow that breathes underneath, and hops when tapped. */
@Composable
private fun ClaudeSprite() {
    val scope = rememberCoroutineScope()
    val float = rememberInfiniteTransition(label = "claude-float")
    val bob by float.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = ZeroMotion.Emphasized), RepeatMode.Reverse),
        label = "claude-bob",
    )
    val hop = remember { Animatable(0f) }
    val source = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = source,
            indication = null,
            onClickLabel = "Make Claude hop",
        ) {
            scope.launch {
                hop.animateTo(1f, tween(170, easing = ZeroMotion.EmphasizedDecelerate))
                hop.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
            }
        },
    ) {
        Image(
            painter = painterResource(R.drawable.credits_claude),
            contentDescription = "Claude, holding a coffee next to a laptop",
            modifier = Modifier
                .width(208.dp)
                .graphicsLayer {
                    translationY = -(bob * 6.dp.toPx()) - hop.value * 30.dp.toPx()
                    val squash = 1f + hop.value * 0.04f
                    scaleY = squash
                    scaleX = 2f - squash
                },
        )
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.width(150.dp).height(12.dp)) {
            val lift = (bob * 6f + hop.value * 30f) / 36f
            val scale = 1f - lift * 0.35f
            val w = size.width * scale
            val h = size.height * scale
            drawOval(
                color = Color.Black.copy(alpha = 0.22f * (1f - lift * 0.5f)),
                topLeft = Offset((size.width - w) / 2, (size.height - h) / 2),
                size = Size(w, h),
            )
        }
    }
}

/** In memory only: the avatar is fetched once per app launch and never written to disk. */
private object AvatarCache {
    var image: ImageBitmap? = null
}

@Composable
private fun rememberGitHubAvatar(user: String): ImageBitmap? {
    var image by remember { mutableStateOf(AvatarCache.image) }
    LaunchedEffect(user) {
        if (image != null) return@LaunchedEffect
        image = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL("https://github.com/$user.png?size=200").openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                try {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }?.also { AvatarCache.image = it }
    }
    return image
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
    }
}
