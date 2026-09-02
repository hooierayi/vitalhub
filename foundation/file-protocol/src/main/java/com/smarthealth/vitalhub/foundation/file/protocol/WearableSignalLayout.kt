package com.smarthealth.vitalhub.foundation.file.protocol

/** Immutable signal metadata written once when a DICOM instance is finalized. */
data class WearableSignalLayout(
    val ecg: EcgLayout = EcgLayout(),
    val respiration: RespirationLayout = RespirationLayout(),
    val motion: MotionLayout = MotionLayout(),
    val skinTemperature: MetricLayout = MetricLayout.signedTemperature(),
    val ambientTemperature: MetricLayout = MetricLayout.signedTemperature(),
    val humidity: MetricLayout = MetricLayout.signedHumidity(),
    val sweat: SweatLayout = SweatLayout(),
)

data class EcgLayout(
    val samplingFrequencyHz: Double = 250.0,
    val samplesPerFrame: Int = 250,
    val bitsAllocated: Int = 16,
    val bitsStored: Int = 16,
    val sampleInterpretation: DicomSampleInterpretation = DicomSampleInterpretation.SS,
    val channelLabel: String = "ECG",
    /** The recorder protocol does not currently identify a concrete ECG lead. */
    val channelSource: DicomCode = DicomCode(
        value = "2:0",
        scheme = "MDC",
        meaning = "Unspecified lead",
    ),
    val minimumSampleValue: Int = -32_768,
    val maximumSampleValue: Int = 32_767,
    val calibration: WaveformCalibration = WaveformCalibration(
        sensitivity = 400.0 / 32_767.0,
        unit = DicomCode(value = "mV", scheme = "UCUM", meaning = "millivolt"),
    ),
    val presentation: WaveformPresentation = WaveformPresentation(
        horizontalMillimetersPerSecond = 25.0,
        verticalMillimetersPerPhysicalUnit = 10.0,
        channelPosition = 0.5,
        recommendedDisplayCielab = listOf(30_476, 22_741, 36_412),
    ),
)

data class RespirationLayout(
    val samplingFrequencyHz: Double = 250.0,
    val samplesPerFrame: Int = 250,
    val sourceBitsAllocated: Int = 24,
    val sourceByteOrder: SignalByteOrder = SignalByteOrder.BIG_ENDIAN,
    val dicomBitsAllocated: Int = 32,
    val dicomBitsStored: Int = 24,
    val sampleInterpretation: DicomSampleInterpretation = DicomSampleInterpretation.SL,
    val channelLabel: String = "RESP",
    val channelSource: DicomCode = DicomCode(
        value = "109117",
        scheme = "DCM",
        meaning = "Respiration Waveform",
    ),
    val minimumSampleValue: Int = -8_388_608,
    val maximumSampleValue: Int = 8_388_607,
    val calibration: WaveformCalibration = WaveformCalibration(
        sensitivity = 200.0 / 8_388_607.0,
        unit = DicomCode(value = "mV", scheme = "UCUM", meaning = "millivolt"),
    ),
)

data class WaveformCalibration(
    val sensitivity: Double,
    val unit: DicomCode,
    val correctionFactor: Double = 1.0,
    val baseline: Double = 0.0,
)

/** Fixed DICOM presentation recommendation. Dynamic AUTO gain is intentionally omitted. */
data class WaveformPresentation(
    val horizontalMillimetersPerSecond: Double,
    /** Display height assigned to one physical unit from [WaveformCalibration.unit]. */
    val verticalMillimetersPerPhysicalUnit: Double,
    val channelPosition: Double,
    /** DICOM PCS-Values encoded CIELab triplet. */
    val recommendedDisplayCielab: List<Int>,
) {
    init {
        require(horizontalMillimetersPerSecond > 0.0)
        require(verticalMillimetersPerPhysicalUnit > 0.0)
        require(channelPosition in 0.0..1.0)
        require(recommendedDisplayCielab.size == 3)
        require(recommendedDisplayCielab.all { it in 0..65_535 })
    }
}

data class MotionLayout(
    val samplingFrequencyHz: Double = 5.0,
    val samplesPerFrame: Int = 5,
    val channelOrder: List<MotionChannel> = listOf(
        MotionChannel.GYRO_X,
        MotionChannel.GYRO_Y,
        MotionChannel.GYRO_Z,
        MotionChannel.ACC_X,
        MotionChannel.ACC_Y,
        MotionChannel.ACC_Z,
    ),
    val bitsAllocated: Int = 16,
    val bitsStored: Int = 16,
    val sampleInterpretation: DicomSampleInterpretation = DicomSampleInterpretation.SS,
    val unit: String = "raw",
)

data class MetricLayout(
    val samplingFrequencyHz: Double,
    val bitsAllocated: Int,
    val bitsStored: Int,
    val sampleInterpretation: DicomSampleInterpretation,
    val scale: Double,
    val unit: String,
) {
    companion object {
        fun signedTemperature(): MetricLayout = MetricLayout(
            samplingFrequencyHz = 1.0,
            bitsAllocated = 16,
            bitsStored = 16,
            sampleInterpretation = DicomSampleInterpretation.SS,
            scale = 0.01,
            unit = "degC",
        )

        fun signedHumidity(): MetricLayout = MetricLayout(
            samplingFrequencyHz = 1.0,
            bitsAllocated = 16,
            bitsStored = 16,
            sampleInterpretation = DicomSampleInterpretation.SS,
            scale = 0.01,
            unit = "%",
        )
    }
}

data class SweatLayout(
    val samplingFrequencyHz: Double = 1.0,
    val bitsAllocated: Int = 8,
    val bitsStored: Int = 8,
    val sampleInterpretation: DicomSampleInterpretation = DicomSampleInterpretation.UB,
    val codebookVersion: Int = 1,
)

data class DicomCode(
    val value: String,
    val scheme: String,
    val meaning: String,
)

enum class SignalByteOrder { BIG_ENDIAN, LITTLE_ENDIAN }

enum class DicomSampleInterpretation {
    SS,
    SL,
    US,
    UB,
}

enum class MotionChannel {
    GYRO_X,
    GYRO_Y,
    GYRO_Z,
    ACC_X,
    ACC_Y,
    ACC_Z,
}
