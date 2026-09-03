package com.smarthealth.vitalhub.feature.analysis.debug

import android.content.Context
internal object AnalysisMockPreference {
    private const val PREFERENCE_NAME = "vitalhub_analysis_debug"
    private const val KEY_MARKDOWN_MOCK_ENABLED = "markdown_mock_enabled"

    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        AnalysisMockConfig.setEnabled(
            appContext.preferences().getBoolean(KEY_MARKDOWN_MOCK_ENABLED, false),
        )
    }

    fun isEnabled(): Boolean = AnalysisMockConfig.enabled

    fun setEnabled(enabled: Boolean) {
        val context = checkNotNull(applicationContext) {
            "AnalysisMockPreference must be initialized before use"
        }
        context.preferences()
            .edit()
            .putBoolean(KEY_MARKDOWN_MOCK_ENABLED, enabled)
            .apply()
        AnalysisMockConfig.setEnabled(enabled)
    }

    private fun Context.preferences() =
        getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
}
