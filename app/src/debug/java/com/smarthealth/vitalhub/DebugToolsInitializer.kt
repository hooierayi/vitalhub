package com.smarthealth.vitalhub

import android.app.Application
import com.didichuxing.doraemonkit.DoKit
import com.smarthealth.vitalhub.debug.dokit.bluetooth.BluetoothDoKit
import com.smarthealth.vitalhub.debug.dokit.protocol.ProtocolDoKit
import com.smarthealth.vitalhub.debug.dokit.waveform.WaveformDoKit
import com.smarthealth.vitalhub.feature.analysis.debug.AnalysisMockDoKit

internal object DebugToolsInitializer {
    fun init(application: Application) {
        DoKit.Builder(application)
            .customKits(
                linkedMapOf(
                    "VitalHub 设备调试" to listOf(
                        BluetoothDoKit(),
                        ProtocolDoKit(),
                        WaveformDoKit(),
                    ),
                    "VitalHub 网络调试" to listOf(
                        AnalysisMockDoKit(),
                    ),
                ),
            )
            .disableUpload()
            .alwaysShowMainIcon(true)
            .build()
    }
}
