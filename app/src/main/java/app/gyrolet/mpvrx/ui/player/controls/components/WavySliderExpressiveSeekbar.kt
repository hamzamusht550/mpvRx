package app.gyrolet.mpvrx.ui.player.controls.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.gyrolet.mpvrx.ui.player.SkipSegment
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressiveSeekbar(
    position: Float,
    duration: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    chapters: ImmutableList<Segment> = persistentListOf(),
    skipSegments: ImmutableList<SkipSegment> = persistentListOf(),
    isPlaying: Boolean = true,
    loopStart: Float? = null,
    loopEnd: Float? = null,
    bufferDuration: Float? = null,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
    waveAmplitudeWhenPlaying: Dp = 4.dp,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
) {
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePadding = thumbRadius
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    val currentPositionState = rememberUpdatedState(position)
    val currentDurationState = rememberUpdatedState(duration)
    val progressFractionState = remember {
        derivedStateOf {
            val dur = currentDurationState.value
            if (dur > 0f) (currentPositionState.value / dur).coerceIn(0f, 1f) else 0f
        }
    }
    val progressFraction = progressFractionState.value

    val normalizedValueState = remember {
        derivedStateOf { progressFractionState.value }
    }

    val safeSemanticsStep = 0.01f
    val semanticNormalizedValueState = remember(safeSemanticsStep) {
        derivedStateOf {
            val norm = normalizedValueState.value
            ((norm / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
        }
    }
    val semanticSliderValueState = remember(duration) {
        derivedStateOf {
            semanticNormalizedValueState.value * duration.coerceAtLeast(0f)
        }
    }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking
    var interactionPosition by remember(duration) {
        mutableFloatStateOf(position.coerceIn(0f, duration.coerceAtLeast(0f)))
    }

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isInteracting) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )

    val currentHalfWidth = remember(thumbRadius, strokeWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val radius = thumbRadius
            val halfStroke = strokeWidth * 0.6f
            radius * (1f - fraction) + halfStroke * fraction
        }
    }

    val dynamicGapSize = remember {
        derivedStateOf {
            currentHalfWidth.value + 4.dp
        }
    }

    val renderedNormalizedProgress = remember {
        mutableFloatStateOf(progressFraction)
    }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(position, duration, isInteracting) {
        if (!isInteracting) {
            interactionPosition = position.coerceIn(0f, duration.coerceAtLeast(0f))
        }
    }

    LaunchedEffect(isInteracting, isPlaying) {
        snapshotFlow { normalizedValueState.value }.collectLatest { target ->
            if (isInteracting) {
                return@collectLatest
            }

            val start = renderedNormalizedProgress.floatValue
            if (abs(start - target) > 0.1f) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collectLatest
            }

            val nowNanos = System.nanoTime()
            val intervalMs = if (lastProgressUpdateNanos == 0L) {
                180L
            } else {
                ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
            }
            lastProgressUpdateNanos = nowNanos

            if (abs(start - target) <= 0.0001f) {
                renderedNormalizedProgress.floatValue = target
                return@collectLatest
            }

            val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
            var startFrameNanos = 0L
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                if (startFrameNanos == 0L) startFrameNanos = frameNanos
                val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
                val fraction = (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
                renderedNormalizedProgress.floatValue = start + (target - start) * fraction
                if (fraction >= 1f) break
            }
            renderedNormalizedProgress.floatValue = target
        }
    }

    val visualNormalizedProgress =
        if (isInteracting) {
            if (duration > 0f) (interactionPosition / duration).coerceIn(0f, 1f) else 0f
        } else {
            renderedNormalizedProgress.floatValue
        }

    val containerHeight = max(
        WavyProgressIndicatorDefaults.LinearContainerHeight,
        max(thumbRadius * 2, thumbLineHeightWhenInteracting)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        LinearWavyProgressIndicator(
            progress = { visualNormalizedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp)),
            color = primaryColor,
            trackColor = surfaceVariant,
            stroke = stroke,
            trackStroke = stroke,
            gapSize = dynamicGapSize.value * (1.0f + 0.1573f * animatedAmplitude * animatedAmplitude),
            stopSize = 3.dp,
            amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
            wavelength = wavelength,
            waveSpeed = waveSpeed,
        )

        val renderedProgress = visualNormalizedProgress

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
            val trackStart = edgePaddingPx
            val trackEnd = size.width - edgePaddingPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2

            fun lerp(start: Float, stop: Float, fraction: Float): Float {
                return start + (stop - start) * fraction
            }

            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            val rawThumbX = trackStart + (trackWidth * renderedProgress)
            val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
            val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
            val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(
                    thumbX - currentWidth / 2f,
                    thumbY - currentHeight / 2f
                ),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f)
            )
        }

        if (skipSegments.isNotEmpty() && duration > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val safeDuration = duration.toDouble().coerceAtLeast(0.0001)
                val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                val trackStart = edgePaddingPx
                val trackEnd = size.width - edgePaddingPx
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                val centerY = size.height / 2f
                val overlayHeight = strokeWidthPx
                val topY = centerY - (overlayHeight / 2f)
                
                skipSegments.forEach { segment ->
                    val startFraction = (segment.startSeconds / safeDuration).toFloat().coerceIn(0f, 1f)
                    val endFraction = (segment.endSeconds / safeDuration).toFloat().coerceIn(0f, 1f)
                    val startX = trackStart + (startFraction * trackWidth)
                    val endX = trackStart + (endFraction * trackWidth)
                    if (endX - startX < 1f) return@forEach
                    val color = segment.type.accentColor
                    val fillColor = Color(
                        red = color.red * 0.74f,
                        green = color.green * 0.74f,
                        blue = color.blue * 0.74f,
                        alpha = 0.42f,
                    )
                    val edgeColor = Color(
                        red = color.red * 0.58f,
                        green = color.green * 0.58f,
                        blue = color.blue * 0.58f,
                        alpha = 1f,
                    )
                    drawRect(
                        color = fillColor,
                        topLeft = Offset(startX, topY),
                        size = Size(endX - startX, overlayHeight),
                    )
                    drawLine(
                        color = edgeColor,
                        start = Offset(startX, topY),
                        end = Offset(startX, topY + overlayHeight),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawLine(
                        color = edgeColor,
                        start = Offset(endX, topY),
                        end = Offset(endX, topY + overlayHeight),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }

        if (bufferDuration != null && bufferDuration > 0f && duration > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                val trackStart = edgePaddingPx
                val trackEnd = size.width - edgePaddingPx
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                val playedPx = trackStart + (trackWidth * renderedProgress)
                val bufferPx = (playedPx + (bufferDuration / duration) * trackWidth).coerceIn(playedPx, trackEnd)
                drawLine(
                    color = primaryColor.copy(alpha = 0.45f),
                    start = Offset(playedPx, size.height / 2f),
                    end = Offset(bufferPx, size.height / 2f),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (loopStart != null || loopEnd != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val loopColor = Color(0xFFFFB300)
                val markerWidth = 2.dp.toPx()
                val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                val trackStart = edgePaddingPx
                val trackEnd = size.width - edgePaddingPx
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                val centerY = size.height / 2f
                val markerHalfH = strokeWidthPx / 2f + 6.dp.toPx()
                val overlayHeight = strokeWidthPx
                val topY = centerY - overlayHeight / 2f

                if (loopStart != null && duration > 0f) {
                    val startPx = trackStart + ((loopStart / duration).coerceIn(0f, 1f) * trackWidth)
                    drawLine(
                        color = loopColor,
                        start = Offset(startPx, centerY - markerHalfH),
                        end = Offset(startPx, centerY + markerHalfH),
                        strokeWidth = markerWidth,
                    )
                }

                if (loopEnd != null && duration > 0f) {
                    val endPx = trackStart + ((loopEnd / duration).coerceIn(0f, 1f) * trackWidth)
                    drawLine(
                        color = loopColor,
                        start = Offset(endPx, centerY - markerHalfH),
                        end = Offset(endPx, centerY + markerHalfH),
                        strokeWidth = markerWidth,
                    )
                }

                if (loopStart != null && loopEnd != null && duration > 0f) {
                    val minPx = trackStart + ((minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * trackWidth)
                    val maxPx = trackStart + ((maxOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * trackWidth)
                    drawRect(
                        color = loopColor.copy(alpha = 0.2f),
                        topLeft = Offset(minPx, topY),
                        size = Size(maxPx - minPx, overlayHeight),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(duration, trackEdgePaddingPx) {
                    val safeDuration = duration.coerceAtLeast(0f)
                    if (safeDuration <= 0f) return@pointerInput

                    fun valueForX(rawX: Float): Float {
                        val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                        val trackStart = edgePadding
                        val trackEnd = size.width - edgePadding
                        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                        val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                        return normalized * safeDuration
                    }

                    detectTapGestures(
                        onTap = { offset ->
                            isPointerSeeking = true
                            val targetValue = valueForX(offset.x).coerceIn(0f, safeDuration)
                            interactionPosition = targetValue
                            latestOnValueChange(targetValue)
                            renderedNormalizedProgress.floatValue =
                                if (safeDuration > 0f) (targetValue / safeDuration).coerceIn(0f, 1f) else 0f
                            lastProgressUpdateNanos = System.nanoTime()
                            latestOnValueChangeFinished(targetValue)
                            isPointerSeeking = false
                        }
                    )
                }
                .pointerInput(duration, trackEdgePaddingPx) {
                    val safeDuration = duration.coerceAtLeast(0f)
                    if (safeDuration <= 0f) return@pointerInput

                    fun valueForX(rawX: Float): Float {
                        val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                        val trackStart = edgePadding
                        val trackEnd = size.width - edgePadding
                        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                        val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                        return normalized * safeDuration
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            isPointerSeeking = true
                            val targetValue = valueForX(offset.x).coerceIn(0f, safeDuration)
                            interactionPosition = targetValue
                            latestOnValueChange(targetValue)
                        },
                        onDragEnd = {
                            val finalValue = interactionPosition.coerceIn(0f, safeDuration)
                            renderedNormalizedProgress.floatValue =
                                if (safeDuration > 0f) (finalValue / safeDuration).coerceIn(0f, 1f) else 0f
                            lastProgressUpdateNanos = System.nanoTime()
                            latestOnValueChangeFinished(finalValue)
                            isPointerSeeking = false
                        },
                        onDragCancel = {
                            val finalValue = interactionPosition.coerceIn(0f, safeDuration)
                            renderedNormalizedProgress.floatValue =
                                if (safeDuration > 0f) (finalValue / safeDuration).coerceIn(0f, 1f) else 0f
                            lastProgressUpdateNanos = System.nanoTime()
                            latestOnValueChangeFinished(finalValue)
                            isPointerSeeking = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val targetValue = valueForX(change.position.x).coerceIn(0f, safeDuration)
                            interactionPosition = targetValue
                            latestOnValueChange(targetValue)
                        }
                    )
                }
        )
    }
}
