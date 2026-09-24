package com.aerotech.upieasy.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.aerotech.upieasy.ui.theme.BrandGradientEnd
import com.aerotech.upieasy.ui.theme.BrandGradientStart

@Composable
fun UserAvatar(
    avatarUrl: String?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    fontSizeRatio: Float = 0.42f
) {
    val initial = (name?.trim()?.take(1) ?: "M").uppercase()
    val textSize = (size.value * fontSizeRatio).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name ?: "User Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    AvatarInitials(initial, textSize)
                },
                error = {
                    AvatarInitials(initial, textSize)
                }
            )
        } else {
            AvatarInitials(initial, textSize)
        }
    }
}

@Composable
private fun AvatarInitials(initial: String, textSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(BrandGradientStart, BrandGradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = textSize
        )
    }
}
