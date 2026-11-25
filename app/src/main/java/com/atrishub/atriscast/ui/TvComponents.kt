package com.atrishub.atriscast.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
internal fun TvFocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = SurfaceRaised,
    selectedColor: Color = AccentContainer,
    focusedColor: Color = SurfaceFocused,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.02f else 1f, label = "cardScale")
    val bg by animateColorAsState(
        targetValue = when {
            focused -> focusedColor
            selected -> selectedColor
            else -> containerColor
        },
        label = "cardBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> Accent
            selected -> Accent.copy(alpha = 0.55f)
            else -> Border
        },
        label = "cardBorder",
    )
    val borderWidth = if (focused) 2.dp else 1.dp

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bg, shape)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(padding),
    ) {
        content(focused)
    }
}

@Composable
internal fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused && enabled) 1.03f else 1f, label = "btnScale")
    val shape = RoundedCornerShape(14.dp)

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> Surface
            focused && primary -> Accent
            focused -> AccentContainer
            primary -> Color(0xFF17383E)
            else -> SurfaceRaised
        },
        label = "btnBg",
    )
    val contentColor = when {
        !enabled -> MutedText
        focused && primary -> Color(0xFF061613)
        primary -> Accent
        focused -> Color.White
        else -> PrimaryText
    }
    val borderColor = when {
        !enabled -> Border
        focused -> Color.White
        primary -> Accent.copy(alpha = 0.45f)
        else -> BorderStrong
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(container, shape)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

@Composable
internal fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
