package com.hairconsultant.app.ui.haircolor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hairconsultant.app.ui.theme.CameraScrim
import com.hairconsultant.app.ui.theme.EaseInOutStrong
import com.hairconsultant.app.ui.theme.EaseOutStrong
import com.hairconsultant.app.ui.theme.LocalShopColors
import com.hairconsultant.app.ui.theme.OnShell
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The Color Studio's controls on one translucent plate over the camera or photo, in the same
 * language as [com.hairconsultant.app.ui.facescan.TryOnStrip]: the shade being worn as the title,
 * the two shade families, the shades, and how strongly the dye is applied.
 *
 * @param ready false until the hair has been found; shades can't be picked before then.
 * @param hint what the plate is waiting for, shown in place of the shade count.
 */
@Composable
fun ShadePlate(
    ui: ColorStudioUiState,
    ready: Boolean,
    hint: String?,
    onFamily: (ShadeFamily) -> Unit,
    onShade: (HairShade?) -> Unit,
    onIntensity: (Float) -> Unit,
    onTitleLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shop = LocalShopColors.current
    val shades = remember(ui.family) { HairShades.of(ui.family) }
    // Shades drop in one after another the first time the plate opens, never again after that.
    var firstOpen by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(600)
        firstOpen = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraScrim, MaterialTheme.shapes.large)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onTitleLongPress() }) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            AnimatedContent(
                targetState = ui.shade?.name,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f),
                label = "shade title"
            ) { name ->
                Text(
                    text = (name?.let { "$it on you" } ?: "Your own color").uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = shop.onShell,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = hint ?: "${shades.size} shades",
                style = MaterialTheme.typography.bodySmall,
                color = shop.onShellMuted,
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
            )
        }

        FamilySwitch(
            selected = ui.family,
            onSelect = onFamily,
            modifier = Modifier.padding(start = 14.dp, top = 10.dp)
        )

        val nudge = with(LocalDensity.current) { 8.dp.roundToPx() }
        AnimatedContent(
            targetState = ui.family,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(150)) + slideInHorizontally(tween(150, easing = EaseOutStrong)) { direction * nudge }) togetherWith
                    fadeOut(tween(100))
            },
            modifier = Modifier.padding(top = 10.dp),
            label = "shade family"
        ) { family ->
            val familyShades = remember(family) { HairShades.of(family) }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                item(key = "own") {
                    ShadeTile(
                        name = "Your own color",
                        brush = null,
                        selected = ui.shade == null,
                        enabled = ready,
                        appearAfterMs = if (firstOpen) 0 else null,
                        onClick = { onShade(null) }
                    )
                }
                itemsIndexed(familyShades, key = { _, shade -> shade.id }) { index, shade ->
                    ShadeTile(
                        name = shade.name,
                        brush = remember(shade) { shade.strandBrush() },
                        selected = ui.shade?.id == shade.id,
                        enabled = ready,
                        appearAfterMs = if (firstOpen && index < 8) (index + 1) * 30 else null,
                        onClick = { onShade(shade) }
                    )
                }
            }
        }

        val haptics = LocalHapticFeedback.current
        val sliderEnabled = ready && ui.shade != null
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Intensity", style = MaterialTheme.typography.bodySmall, color = shop.onShellMuted)
            Slider(
                value = ui.intensity,
                onValueChange = { value ->
                    // A tick at either end, so the stop is felt without looking away from the hair.
                    if ((value == 0f || value == 1f) && value != ui.intensity) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onIntensity(value)
                },
                enabled = sliderEnabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = if (sliderEnabled) "Color intensity" else "Color intensity, pick a shade first"
                    },
                colors = SliderDefaults.colors(
                    thumbColor = OnShell,
                    activeTrackColor = OnShell,
                    inactiveTrackColor = OnShell.copy(alpha = 0.25f),
                    disabledThumbColor = OnShell.copy(alpha = 0.35f),
                    disabledActiveTrackColor = OnShell.copy(alpha = 0.25f),
                    disabledInactiveTrackColor = OnShell.copy(alpha = 0.12f)
                )
            )
            Text(
                text = "${(ui.intensity * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = shop.onShellMuted,
                modifier = Modifier.width(36.dp)
            )
        }
    }
}

/** Natural | Fashion: two tabs with one indicator that slides between them. */
@Composable
private fun FamilySwitch(selected: ShadeFamily, onSelect: (ShadeFamily) -> Unit, modifier: Modifier = Modifier) {
    val shop = LocalShopColors.current
    val families = ShadeFamily.entries
    BoxWithConstraints(
        modifier = modifier
            .width(220.dp)
            .height(48.dp)
            .background(OnShell.copy(alpha = 0.08f), MaterialTheme.shapes.small)
    ) {
        val tabWidth = maxWidth / families.size
        val indicatorX by animateDpAsState(tabWidth * selected.ordinal, tween(200, easing = EaseInOutStrong), label = "family indicator")
        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .background(shop.indicator, MaterialTheme.shapes.extraSmall)
        )
        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            families.forEach { family ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = family == selected, role = Role.Tab, onClick = { onSelect(family) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = family.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (family == selected) shop.onShell else shop.onShellMuted
                    )
                }
            }
        }
    }
}

/**
 * One shade, as a tile like the haircut tiles in the try-on strip. [brush] null is the "your own
 * color" tile, drawn as a struck-through well.
 *
 * @param appearAfterMs delay before the tile drops in, or null to be there from the start.
 */
@Composable
private fun ShadeTile(
    name: String,
    brush: Brush?,
    selected: Boolean,
    enabled: Boolean,
    appearAfterMs: Int?,
    onClick: () -> Unit
) {
    val shop = LocalShopColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120, easing = EaseOutStrong), label = "tile press")
    val ring by animateDpAsState(if (selected) 3.dp else 1.5.dp, tween(150), label = "tile ring")
    val appear = remember { Animatable(if (appearAfterMs == null) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (appearAfterMs != null) {
            delay(appearAfterMs.toLong())
            appear.animateTo(1f, tween(220, easing = EaseOutStrong))
        }
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = appear.value * if (enabled) 1f else 0.45f
                translationY = (1f - appear.value) * 8.dp.toPx()
            }
            .semantics {
                this.selected = selected
                contentDescription = if (enabled) name else "$name, available once your hair is found"
            },
        shape = MaterialTheme.shapes.medium,
        color = shop.imageWell,
        border = BorderStroke(ring, if (selected) OnShell else shop.shellLine)
    ) {
        val line = shop.onShellMuted
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (brush != null) Modifier.background(brush) else Modifier.drawBehind {
                    drawLine(
                        color = line,
                        start = Offset(size.width * 0.28f, size.height * 0.72f),
                        end = Offset(size.width * 0.72f, size.height * 0.28f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            )
        )
    }
}

/** The shade as a lit strand, lighter at the crown than at the ends, so a tile reads as hair and not paint. */
private fun HairShade.strandBrush(): Brush {
    fun at(lightness: Float): Color {
        val rgb = HairColorMath.oklabToSrgb(lightness.coerceIn(0f, 1f), a, b)
        return Color(rgb[0], rgb[1], rgb[2])
    }
    return Brush.verticalGradient(listOf(at(l + 0.12f), at(l), at(l - 0.1f)))
}
