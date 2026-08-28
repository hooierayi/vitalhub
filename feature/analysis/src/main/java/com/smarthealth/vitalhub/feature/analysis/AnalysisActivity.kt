package com.smarthealth.vitalhub.feature.analysis

import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.BaseFlowActivity
import com.smarthealth.vitalhub.core.navi.Routes

@Route(path = Routes.ANALYSIS)
class AnalysisActivity : BaseFlowActivity() {
    override val initialFragmentPath = Routes.ANALYSIS_FRAGMENT
}
