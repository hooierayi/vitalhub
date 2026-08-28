package com.smarthealth.vitalhub.feature.questionnaire

import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.BaseFlowActivity
import com.smarthealth.vitalhub.core.navi.Navigator
import com.smarthealth.vitalhub.core.navi.QuestionnairePhase
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.core.navi.Routes

@Route(path = Routes.QUESTIONNAIRE)
class QuestionnaireActivity : BaseFlowActivity() {
    override val initialFragmentPath = Routes.QUESTIONNAIRE_FRAGMENT

    override fun onRootBackPressed(): Boolean {
        if (intent.getStringExtra(RouteArgs.QUESTIONNAIRE_PHASE) != QuestionnairePhase.POST) return false
        Navigator.returnHome(this)
        return true
    }
}
