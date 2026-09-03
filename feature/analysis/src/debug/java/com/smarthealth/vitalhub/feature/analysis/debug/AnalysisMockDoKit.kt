package com.smarthealth.vitalhub.feature.analysis.debug

import android.app.Activity
import android.content.Context
import com.didichuxing.doraemonkit.DoKit
import com.didichuxing.doraemonkit.kit.AbstractKit
import com.smarthealth.vitalhub.feature.analysis.R

class AnalysisMockDoKit : AbstractKit() {
    override val name: Int = R.string.dokit_analysis_mock_name
    override val icon: Int = android.R.drawable.ic_menu_manage

    override fun onClickWithReturn(activity: Activity): Boolean {
        AnalysisMockPreference.initialize(activity.applicationContext)
        DoKit.launchFloating(AnalysisMockDoKitView::class.java)
        return true
    }

    override fun onAppInit(context: Context?) {
        context?.applicationContext?.let(AnalysisMockPreference::initialize)
    }
}
