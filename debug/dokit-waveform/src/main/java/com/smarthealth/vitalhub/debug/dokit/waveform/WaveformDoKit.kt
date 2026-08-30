package com.smarthealth.vitalhub.debug.dokit.waveform

import android.app.Activity
import android.content.Context
import com.didichuxing.doraemonkit.DoKit
import com.didichuxing.doraemonkit.kit.AbstractKit
import com.smarthealth.vitalhub.foundation.device.waveform.ui.WaveformDebugRegistry

class WaveformDoKit : AbstractKit() {
    override val name: Int = R.string.dokit_waveform_name
    override val icon: Int = R.drawable.ic_dokit_waveform

    override fun onClickWithReturn(activity: Activity): Boolean {
        DoKit.launchFloating(WaveformDebugDoKitView::class.java)
        return true
    }

    override fun onAppInit(context: Context?) {
        WaveformDebugRegistry.setEnabled(true)
    }
}
