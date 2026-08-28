package com.smarthealth.vitalhub.feature.user

import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.BaseFlowActivity
import com.smarthealth.vitalhub.core.navi.Routes

/** ARouter Activity entry; the feature UI remains hosted by a Fragment. */
@Route(path = Routes.USER_INFO_EDIT)
class UserActivity : BaseFlowActivity() {
    override val initialFragmentPath = Routes.USER_INFO_EDIT_FRAGMENT
}
