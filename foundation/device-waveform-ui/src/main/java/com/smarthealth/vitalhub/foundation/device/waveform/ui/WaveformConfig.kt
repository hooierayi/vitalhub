package com.smarthealth.vitalhub.foundation.device.waveform.ui

import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WaveformRenderMode {
    SWEEP,
    SCROLL,
}

enum class PaperSpeed(val millimetersPerSecond: Float) {
    MM_12_5_PER_SECOND(12.5f),
    MM_25_PER_SECOND(25f),
    MM_50_PER_SECOND(50f),
}

enum class PaperGain(val millimetersPerMillivolt: Float) {
    MM_5_PER_MV(5f),
    MM_10_PER_MV(10f),
    MM_20_PER_MV(20f),
}

fun waveformScaleLabel(paperSpeed: PaperSpeed, gain: PaperGain): String =
    "${paperSpeed.millimetersPerSecond.compactScaleValue()} mm/s · " +
        "${gain.millimetersPerMillivolt.compactScaleValue()} mm/mV"

private fun Float.compactScaleValue(): String =
    if (this % 1f == 0f) toInt().toString() else toString()

enum class WaveformGainMode {
    /** Keep the configured paper gain; only move the drawing origin to the observed signal center. */
    FIXED,

    /** Choose 20, 10, or 5 mm/mV so the recent signal range remains visible. */
    FIT_STANDARD_GAIN,

    /** Continuously fit the recent range to the available height; intended for non-paper signals. */
    FIT_VISIBLE_RANGE,
}

@Immutable
data class SignalCalibration(
    val positiveLimit: Int,
    val negativeLimitMagnitude: Int,
    val fullScaleMillivolts: Float,
) {
    init {
        require(positiveLimit > 0)
        require(negativeLimitMagnitude > 0)
        require(fullScaleMillivolts > 0f)
    }

    fun toMillivolts(sample: Int): Float {
        val limit = if (sample >= 0) positiveLimit else negativeLimitMagnitude
        return sample.toFloat() * fullScaleMillivolts / limit
    }

    fun deltaToMillivolts(sampleDelta: Float): Float =
        sampleDelta * fullScaleMillivolts / positiveLimit
}

object SignalCalibrations {
    val Ecg = SignalCalibration(
        positiveLimit = 32_767,
        negativeLimitMagnitude = 32_768,
        fullScaleMillivolts = 400f,
    )

    val Respiration = SignalCalibration(
        positiveLimit = 8_388_607,
        negativeLimitMagnitude = 8_388_608,
        fullScaleMillivolts = 200f,
    )
}

@Immutable
data class DisplayCalibration(
    val horizontalCorrection: Float = 1f,
    val verticalCorrection: Float = 1f,
) {
    init {
        require(horizontalCorrection > 0f)
        require(verticalCorrection > 0f)
    }
}

@Immutable
data class PhysicalPaperMetrics(
    val pixelsPerMillimeterX: Float,
    val pixelsPerMillimeterY: Float,
) {
    init {
        require(pixelsPerMillimeterX > 0f)
        require(pixelsPerMillimeterY > 0f)
    }
}

fun DisplayMetrics.toPhysicalPaperMetrics(
    calibration: DisplayCalibration = DisplayCalibration(),
): PhysicalPaperMetrics {
    val fallbackDpi = densityDpi.toFloat().coerceAtLeast(1f)
    val horizontalDpi = xdpi.takeIf { it.isFinite() && it > 0f } ?: fallbackDpi
    val verticalDpi = ydpi.takeIf { it.isFinite() && it > 0f } ?: fallbackDpi
    return PhysicalPaperMetrics(
        pixelsPerMillimeterX = horizontalDpi / MILLIMETERS_PER_INCH * calibration.horizontalCorrection,
        pixelsPerMillimeterY = verticalDpi / MILLIMETERS_PER_INCH * calibration.verticalCorrection,
    )
}

@Composable
fun rememberPhysicalPaperMetrics(
    calibration: DisplayCalibration = DisplayCalibration(),
): PhysicalPaperMetrics {
    val metrics = LocalContext.current.resources.displayMetrics
    return remember(
        metrics.xdpi,
        metrics.ydpi,
        metrics.densityDpi,
        calibration,
    ) {
        metrics.toPhysicalPaperMetrics(calibration)
    }
}

@Immutable
data class WaveformPaperConfig(
    val sampleRateHz: Int = 250,
    val paperSpeed: PaperSpeed = PaperSpeed.MM_25_PER_SECOND,
    val gain: PaperGain = PaperGain.MM_10_PER_MV,
    val gainMode: WaveformGainMode = WaveformGainMode.FIXED,
    val renderMode: WaveformRenderMode = WaveformRenderMode.SWEEP,
    val smallGridMillimeters: Float = 1f,
    val majorGridInterval: Int = 5,
    val sweepBlankMillimeters: Float = 1f,
    val baselineFraction: Float = 0.68f,
    val showPaperGrid: Boolean = true,
    val showBaseline: Boolean = true,
    val showCalibrationPulse: Boolean = true,
) {
    init {
        require(sampleRateHz > 0)
        require(smallGridMillimeters > 0f)
        require(majorGridInterval > 0)
        require(sweepBlankMillimeters >= 0f)
        require(baselineFraction in 0f..1f)
    }

    val sampleSpacingMillimeters: Float
        get() = paperSpeed.millimetersPerSecond / sampleRateHz

    val samplesPerSmallGrid: Float
        get() = smallGridMillimeters / sampleSpacingMillimeters
}

@Immutable
data class WaveformPaperStyle(
    val backgroundColor: Color,
    val minorGridColor: Color,
    val majorGridColor: Color,
    val baselineColor: Color,
    val waveformColor: Color,
    val calibrationColor: Color = waveformColor,
    val minorGridStroke: Dp = 0.45.dp,
    val majorGridStroke: Dp = 0.9.dp,
    val waveformStroke: Dp = 1.4.dp,
)

object WaveformPaperStyles {
    val Ecg = WaveformPaperStyle(
        backgroundColor = Color(0xFFFFFBFC),
        minorGridColor = Color(0xFFF4D9DF),
        majorGridColor = Color(0xFFE5A7B3),
        baselineColor = Color(0xFFD6B0B8),
        waveformColor = Color(0xFF087E56),
    )

    val Respiration = WaveformPaperStyle(
        backgroundColor = Color(0xFFFBFCFE),
        minorGridColor = Color(0xFFF4D9DF),
        majorGridColor = Color(0xFFE5A7B3),
        baselineColor = Color(0xFFDCE6F0),
        waveformColor = Color(0xFF0872F5),
        calibrationColor = Color.Transparent,
    )
}

private const val MILLIMETERS_PER_INCH = 25.4f
