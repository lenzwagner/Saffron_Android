package com.zephron.app.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zephron.app.R
import com.zephron.app.ui.theme.LocalAppColors

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    // Expressive logo: The new icon paths inside a circular gradient orb
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(colors.gradient)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Zephron Logo",
            tint = Color.White,
            modifier = Modifier.fillMaxSize(0.65f)
        )
    }
}

/** Circular avatar — shows profile photo if available, otherwise the first letter. */
@Composable
fun PartnerAvatar(
    label: String?,
    accent: Color,
    size: androidx.compose.ui.unit.Dp = 30.dp,
    photoUrl: String? = null
) {
    Surface(shape = CircleShape, color = accent, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = label?.trim()?.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.46f).sp
                )
            }
        }
    }
}

@Composable
fun ExpressiveCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(checked, label = "checkbox")
    
    val checkScale by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "scale"
    ) { if (it) 1f else 0.8f }
    
    val circleColor by transition.animateColor(label = "color") { if (it) color else color.copy(alpha = 0.1f) }
    val borderColor by transition.animateColor(label = "border") { if (it) color else color.copy(alpha = 0.4f) }

    Box(
        modifier = modifier
            .size(26.dp)
            .scale(checkScale)
            .clip(CircleShape)
            .background(circleColor)
            .border(1.5.dp, borderColor, CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
