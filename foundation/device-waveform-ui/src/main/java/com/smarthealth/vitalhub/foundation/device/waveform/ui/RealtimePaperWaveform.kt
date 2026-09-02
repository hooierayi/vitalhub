package com.smarthealth.vitalhub.foundation.device.waveform.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun RealtimePaperWaveform(
    state: RealtimeWaveformState,
    signalCalibration: SignalCalibration,
    modifier: Modifier = Modifier,
    paperConfig: WaveformPaperConfig = WaveformPaperConfig(),
    physicalMetrics: PhysicalPaperMetrics = rememberPhysicalPaperMetrics(),
    style: WaveformPaperStyle = WaveformPaperStyles.Ecg,
) {
    LaunchedEffect(state, paperConfig.sampleRateHz) {
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = ((frameNanos - previousFrameNanos) / NANOS_PER_SECOND)
                .toFloat()
                .coerceIn(0f, MAX_RENDER_STEP_SECONDS)
            previousFrameNanos = frameNanos
            state.advanceRendering(elapsedSeconds, paperConfig.sampleRateHz)
        }
    }
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_VARIABLE")
        val observedRevision = state.revision
        val viewportRangeSamples = state.read { viewportRangeSamples }
        val resolvedGainMillimetersPerMillivolt = resolveGainMillimetersPerMillivolt(
            config = paperConfig,
            calibration = signalCalibration,
            metrics = physicalMetrics,
            canvasHeight = size.height,
            viewportRangeSamples = viewportRangeSamples,
        )
        drawRect(style.backgroundColor)
        drawPaperBackground(paperConfig, physicalMetrics, style)
        clipRect {
            when (paperConfig.renderMode) {
                WaveformRenderMode.SWEEP -> drawSweepWaveform(
                    state = state,
                    paperConfig = paperConfig,
                    physicalMetrics = physicalMetrics,
                    signalCalibration = signalCalibration,
                    resolvedGainMillimetersPerMillivolt = resolvedGainMillimetersPerMillivolt,
                    style = style,
                )
                WaveformRenderMode.SCROLL -> drawScrollingWaveform(
                    state = state,
                    paperConfig = paperConfig,
                    physicalMetrics = physicalMetrics,
                    signalCalibration = signalCalibration,
                    resolvedGainMillimetersPerMillivolt = resolvedGainMillimetersPerMillivolt,
                    style = style,
                )
            }
            if (paperConfig.showCalibrationPulse && style.calibrationColor.alpha > 0f) {
                drawCalibrationPulse(
                    paperConfig,
                    resolvedGainMillimetersPerMillivolt,
                    physicalMetrics,
                    style,
                )
            }
        }
    }
}

@Composable
fun EcgWaveform(
    state: RealtimeWaveformState,
    modifier: Modifier = Modifier,
    renderMode: WaveformRenderMode = WaveformRenderMode.SWEEP,
    paperSpeed: PaperSpeed = PaperSpeed.MM_25_PER_SECOND,
    gain: PaperGain = PaperGain.MM_10_PER_MV,
    gainMode: WaveformGainMode = WaveformGainMode.FIXED,
    showCalibrationPulse: Boolean = true,
    physicalMetrics: PhysicalPaperMetrics = rememberPhysicalPaperMetrics(),
) {
    RealtimePaperWaveform(
        state = state,
        signalCalibration = SignalCalibrations.Ecg,
        modifier = modifier,
        paperConfig = WaveformPaperConfig(
            paperSpeed = paperSpeed,
            gain = gain,
            gainMode = gainMode,
            renderMode = renderMode,
            baselineFraction = 0.5f,
            showCalibrationPulse = showCalibrationPulse,
        ),
        physicalMetrics = physicalMetrics,
        style = WaveformPaperStyles.Ecg,
    )
}

@Composable
fun RespirationWaveform(
    state: RealtimeWaveformState,
    modifier: Modifier = Modifier,
    renderMode: WaveformRenderMode = WaveformRenderMode.SWEEP,
    paperSpeed: PaperSpeed = PaperSpeed.MM_6_25_PER_SECOND,
    gain: PaperGain = PaperGain.MM_10_PER_MV,
    gainMode: WaveformGainMode = WaveformGainMode.FIT_VISIBLE_RANGE,
    physicalMetrics: PhysicalPaperMetrics = rememberPhysicalPaperMetrics(),
) {
    RealtimePaperWaveform(
        state = state,
        signalCalibration = SignalCalibrations.Respiration,
        modifier = modifier,
        paperConfig = WaveformPaperConfig(
            paperSpeed = paperSpeed,
            gain = gain,
            gainMode = gainMode,
            renderMode = renderMode,
            baselineFraction = 0.5f,
            showPaperGrid = false,
            showCalibrationPulse = false,
        ),
        physicalMetrics = physicalMetrics,
        style = WaveformPaperStyles.Respiration,
    )
}

private fun DrawScope.drawPaperBackground(
    config: WaveformPaperConfig,
    metrics: PhysicalPaperMetrics,
    style: WaveformPaperStyle,
) {
    if (config.showPaperGrid) {
        val xStep = config.smallGridMillimeters * metrics.pixelsPerMillimeterX
        val yStep = config.smallGridMillimeters * metrics.pixelsPerMillimeterY
        var index = 0
        var x = 0f
        while (x <= size.width) {
            val major = index % config.majorGridInterval == 0
            drawLine(
                color = if (major) style.majorGridColor else style.minorGridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = (if (major) style.majorGridStroke else style.minorGridStroke).toPx(),
            )
            index++
            x = index * xStep
        }
        index = 0
        var y = 0f
        while (y <= size.height) {
            val major = index % config.majorGridInterval == 0
            drawLine(
                color = if (major) style.majorGridColor else style.minorGridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = (if (major) style.majorGridStroke else style.minorGridStroke).toPx(),
            )
            index++
            y = index * yStep
        }
    }
    if (config.showBaseline) {
        val baseline = size.height * config.baselineFraction
        drawLine(
            style.baselineColor,
            Offset(0f, baseline),
            Offset(size.width, baseline),
            style.minorGridStroke.toPx(),
        )
    }
}

private fun DrawScope.drawScrollingWaveform(
    state: RealtimeWaveformState,
    paperConfig: WaveformPaperConfig,
    physicalMetrics: PhysicalPaperMetrics,
    signalCalibration: SignalCalibration,
    resolvedGainMillimetersPerMillivolt: Float,
    style: WaveformPaperStyle,
) {
    val sampleStep = paperConfig.sampleSpacingMillimeters * physicalMetrics.pixelsPerMillimeterX
    if (sampleStep <= 0f) return
    val visibleSamples = max(2, floor(size.width / sampleStep).toInt() + 1)
    val path = Path()
    val baseline = size.height * paperConfig.baselineFraction
    state.read {
        if (nextOrdinal <= firstOrdinal) return@read
        val centerSample = viewportCenterSample
        val newestOrdinal = nextOrdinal - 1
        val startOrdinal = maxOf(firstOrdinal, nextOrdinal - visibleSamples)
        var drawing = false
        for (ordinal in startOrdinal..newestOrdinal) {
            val x = size.width - (newestOrdinal - ordinal).toFloat() * sampleStep
            val y = sampleY(
                sample = sampleAt(ordinal),
                centerSample = centerSample,
                baseline = baseline,
                gainMillimetersPerMillivolt = resolvedGainMillimetersPerMillivolt,
                metrics = physicalMetrics,
                calibration = signalCalibration,
            )
            if (!drawing) {
                path.moveTo(x, y)
                drawing = true
            } else {
                path.lineTo(x, y)
            }
        }
    }
    drawPath(path, style.waveformColor, style = Stroke(style.waveformStroke.toPx()))
}

private fun DrawScope.drawSweepWaveform(
    state: RealtimeWaveformState,
    paperConfig: WaveformPaperConfig,
    physicalMetrics: PhysicalPaperMetrics,
    signalCalibration: SignalCalibration,
    resolvedGainMillimetersPerMillivolt: Float,
    style: WaveformPaperStyle,
) {
    val sampleStep = paperConfig.sampleSpacingMillimeters * physicalMetrics.pixelsPerMillimeterX
    if (sampleStep <= 0f) return
    val visibleSamples = max(2, floor(size.width / sampleStep).toInt() + 1)
    val blankSamples = ceil(paperConfig.sweepBlankMillimeters / paperConfig.sampleSpacingMillimeters)
        .toInt()
        .coerceAtMost(visibleSamples - 1)
    val path = Path()
    val baseline = size.height * paperConfig.baselineFraction
    state.read {
        if (nextOrdinal <= firstOrdinal) return@read
        val centerSample = viewportCenterSample
        val newestOrdinal = nextOrdinal - 1
        val cursorSlot = (newestOrdinal % visibleSamples).toInt()
        val nextSlot = (nextOrdinal % visibleSamples).toInt()
        var previousOrdinal: Long? = null
        for (slot in 0 until visibleSamples) {
            val blankDistance = (slot - nextSlot + visibleSamples) % visibleSamples
            if (blankDistance < blankSamples) {
                previousOrdinal = null
                continue
            }
            val distanceBack = if (slot <= cursorSlot) {
                cursorSlot - slot
            } else {
                cursorSlot + visibleSamples - slot
            }
            val ordinal = newestOrdinal - distanceBack
            if (!contains(ordinal)) {
                previousOrdinal = null
                continue
            }
            val x = slot * sampleStep
            val y = sampleY(
                sample = sampleAt(ordinal),
                centerSample = centerSample,
                baseline = baseline,
                gainMillimetersPerMillivolt = resolvedGainMillimetersPerMillivolt,
                metrics = physicalMetrics,
                calibration = signalCalibration,
            )
            val connectsChronologically = previousOrdinal?.plus(1) == ordinal
            if (!connectsChronologically) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            previousOrdinal = ordinal
        }
    }
    drawPath(path, style.waveformColor, style = Stroke(style.waveformStroke.toPx()))
}

private fun sampleY(
    sample: Int,
    centerSample: Float,
    baseline: Float,
    gainMillimetersPerMillivolt: Float,
    metrics: PhysicalPaperMetrics,
    calibration: SignalCalibration,
): Float {
    val millivolts = calibration.deltaToMillivolts(sample - centerSample)
    val millimeters = millivolts * gainMillimetersPerMillivolt
    return baseline - millimeters * metrics.pixelsPerMillimeterY
}

private fun DrawScope.drawCalibrationPulse(
    config: WaveformPaperConfig,
    gainMillimetersPerMillivolt: Float,
    metrics: PhysicalPaperMetrics,
    style: WaveformPaperStyle,
) {
    val baseline = size.height - CALIBRATION_BOTTOM_MARGIN_MILLIMETERS * metrics.pixelsPerMillimeterY
    val startX = metrics.pixelsPerMillimeterX
    val pulseWidth = config.paperSpeed.millimetersPerSecond * CALIBRATION_PULSE_SECONDS *
        metrics.pixelsPerMillimeterX
    val pulseHeight = gainMillimetersPerMillivolt * metrics.pixelsPerMillimeterY
    val path = Path().apply {
        moveTo(0f, baseline)
        lineTo(startX, baseline)
        lineTo(startX, baseline - pulseHeight)
        lineTo(startX + pulseWidth, baseline - pulseHeight)
        lineTo(startX + pulseWidth, baseline)
        lineTo(startX + pulseWidth + metrics.pixelsPerMillimeterX, baseline)
    }
    drawPath(path, style.calibrationColor, style = Stroke(style.waveformStroke.toPx()))
}

private fun resolveGainMillimetersPerMillivolt(
    config: WaveformPaperConfig,
    calibration: SignalCalibration,
    metrics: PhysicalPaperMetrics,
    canvasHeight: Float,
    viewportRangeSamples: Float,
): Float {
    if (config.gainMode == WaveformGainMode.FIXED || viewportRangeSamples <= 0f) {
        return config.gain.millimetersPerMillivolt
    }
    val baseline = canvasHeight * config.baselineFraction
    val usableHeight = 2f * min(baseline, canvasHeight - baseline) * VIEWPORT_HEIGHT_USAGE
    val rangeMillivolts = calibration.deltaToMillivolts(viewportRangeSamples)
    return when (config.gainMode) {
        WaveformGainMode.FIXED -> config.gain.millimetersPerMillivolt
        WaveformGainMode.FIT_STANDARD_GAIN -> AUTO_GAIN_CANDIDATES.firstOrNull { gain ->
            rangeMillivolts * gain.millimetersPerMillivolt * metrics.pixelsPerMillimeterY <= usableHeight
        }?.millimetersPerMillivolt ?: PaperGain.MM_5_PER_MV.millimetersPerMillivolt
        WaveformGainMode.FIT_VISIBLE_RANGE ->
            usableHeight / (rangeMillivolts * metrics.pixelsPerMillimeterY)
    }
}

private const val CALIBRATION_PULSE_SECONDS = 0.2f
private const val CALIBRATION_BOTTOM_MARGIN_MILLIMETERS = 1f
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MAX_RENDER_STEP_SECONDS = 0.1f
private const val VIEWPORT_HEIGHT_USAGE = 0.9f
private val AUTO_GAIN_CANDIDATES = listOf(
    PaperGain.MM_20_PER_MV,
    PaperGain.MM_10_PER_MV,
    PaperGain.MM_5_PER_MV,
)
