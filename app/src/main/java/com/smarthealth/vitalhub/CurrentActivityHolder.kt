package com.smarthealth.vitalhub

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** Supplies the current visible FragmentActivity to dynamically injected platform services. */
object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    @Volatile
    var activity: FragmentActivity? = null
        private set

    override fun onActivityResumed(activity: Activity) {
        this.activity = activity as? FragmentActivity
    }

    override fun onActivityPaused(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }
}
