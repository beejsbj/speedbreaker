package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SpeedBreakerScreen(
    appName: String,
    durationSeconds: Int,
    category: com.example.data.AppCategory,
    openCount: Int,
    customQuote: String?,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    var remainingTime by remember { mutableIntStateOf(durationSeconds) }
    var elapsedTime by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf("Breathe In") }

    BackHandler {
        // Prevent back navigation
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedTime++
            if (remainingTime > 0) remainingTime--
        }
    }

    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(scale) {
        phase = if (scale > 1.10f) "Hold..." else "Exhale"
    }

    val bgColor = Color(0xFFF9F8F6)
    val textColor = Color(0xFF141414)
    val primaryColor = Color(0xFF141414)
    val secondaryTextColor = Color(0xFF767676)
    val surfaceColor = Color(0xFFEBEBEB)

    val headerAlpha by animateFloatAsState(targetValue = if (elapsedTime >= 2) 1f else 0f, tween(2000), label = "header")
    val quoteAlpha by animateFloatAsState(targetValue = if (elapsedTime >= 8) 1f else 0f, tween(2000), label = "quote")
    val actionsAlpha by animateFloatAsState(targetValue = if (elapsedTime >= 11) 1f else 0f, tween(2000), label = "actions")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(headerAlpha)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(1.dp, Color(0xFFE0E0E0), CircleShape)
                    .background(Color.Transparent, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).background(primaryColor, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${appName.uppercase()} PAUSED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.em,
                    color = textColor
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Main Content Area
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (category == com.example.data.AppCategory.REELS) {
                Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.alpha(quoteAlpha)) {
                    Text(
                        text = openCount.toString(),
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Transparent,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.1f),
                                offset = Offset(0f, 20f),
                                blurRadius = 20f
                            )
                        ),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Text(
                        text = openCount.toString(),
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryColor.copy(alpha = 0.03f),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Text(
                        text = "REELS WATCHED",
                        fontSize = 10.sp,
                        letterSpacing = 0.2.em,
                        color = secondaryTextColor,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.offset(y = 120.dp)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Breathing Ring
            Box(
                modifier = Modifier
                    .size(192.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = primaryColor.copy(alpha = 0.8f),
                    trackColor = primaryColor.copy(alpha = 0.05f),
                    strokeWidth = 2.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = phase,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        color = textColor
                    )
                    Text(
                        text = "Focus",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                }
                // Dots representing progress
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-10).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(4.dp).background(primaryColor, CircleShape))
                    Box(modifier = Modifier.size(4.dp).background(primaryColor, CircleShape))
                    Box(modifier = Modifier.size(4.dp).background(surfaceColor, CircleShape))
                    Box(modifier = Modifier.size(4.dp).background(surfaceColor, CircleShape))
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Quote
            val displayQuote = customQuote.takeIf { !it.isNullOrBlank() } 
                ?: "The cost of a thing is the amount of what I call life which is required to be exchanged for it."
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(quoteAlpha)
            ) {
                Text(
                    text = "\"$displayQuote\"",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                if (customQuote.isNullOrBlank()) {
                    Text(
                        text = "— Henry David Thoreau",
                        fontSize = 10.sp,
                        letterSpacing = 0.1.em,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Interventions / Footer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, 
            modifier = Modifier.fillMaxWidth().alpha(actionsAlpha)
        ) {
            Text(
                text = "INSTEAD, MAYBE...",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                letterSpacing = 0.15.em,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InkButton(
                    icon = "✨",
                    title = "Journal",
                    subtitle = "Daily Reflection",
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                )
                InkButton(
                    icon = "☁️",
                    title = "Meditate",
                    subtitle = "Headspace app",
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            InkButtonRow(
                icon = "📖",
                title = "Continue Reading: Atomic Habits",
                trailing = "82%",
                onClick = onCancel
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (remainingTime > 0) {
                val mins = remainingTime / 60
                val secs = remainingTime % 60
                Text(
                    text = "EXIT BLOCKED FOR $mins:${secs.toString().padStart(2, '0')}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.5f),
                    letterSpacing = 0.05.em
                )
            } else {
                TextButton(onClick = onComplete) {
                    Text("CONTINUE TO APP", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.1.em)
                }
            }
        }
    }
}

@Composable
fun InkButton(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF141414))
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF767676))
        }
    }
}

@Composable
fun InkButtonRow(
    icon: String,
    title: String,
    trailing: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF141414))
        }
        Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF141414))
    }
}
