package com.smarthealth.vitalhub.core.navi

import android.app.Activity
import android.os.Build

/** Window-level transitions shared by every ARouter feature Activity. */
internal object FlowActivityTransitions {
    fun configure(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.flow_activity_open_enter,
                R.anim.flow_activity_open_exit,
            )
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.flow_activity_close_enter,
                R.anim.flow_activity_close_exit,
            )
        } else {
            applyForwardLegacy(activity)
        }
    }

    fun applyAfterNavigation(activity: Activity, returning: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (returning) applyReturnLegacy(activity) else applyForwardLegacy(activity)
    }

    fun applyAfterFinish(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            applyReturnLegacy(activity)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyForwardLegacy(activity: Activity) {
        activity.overridePendingTransition(R.anim.flow_activity_open_enter, R.anim.flow_activity_open_exit)
    }

    @Suppress("DEPRECATION")
    private fun applyReturnLegacy(activity: Activity) {
        activity.overridePendingTransition(R.anim.flow_activity_close_enter, R.anim.flow_activity_close_exit)
    }
}
