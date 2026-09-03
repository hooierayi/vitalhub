package com.smarthealth.vitalhub.feature.analysis.debug

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.didichuxing.doraemonkit.kit.core.AbsDoKitView
import com.didichuxing.doraemonkit.kit.core.DoKitViewLayoutParams

class AnalysisMockDoKitView : AbsDoKitView() {
    override fun onCreate(context: Context) {
        AnalysisMockPreference.initialize(context.applicationContext)
    }

    override fun onCreateView(context: Context, rootView: FrameLayout): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.argb(248, 255, 255, 255))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COLOR_TEAL)
            }

            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "分析结果 Mock"
                    setTextColor(COLOR_TEAL)
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = 15f
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = "×"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setPadding(dp(12), dp(2), dp(2), dp(2))
                    setOnClickListener { detach() }
                })
            })

            addView(Switch(context).apply {
                text = "拦截分析接口并返回完整 Markdown"
                textSize = 13f
                setTextColor(Color.rgb(31, 41, 55))
                isChecked = AnalysisMockPreference.isEnabled()
                setPadding(0, dp(12), 0, dp(4))
                setOnCheckedChangeListener { _, enabled ->
                    AnalysisMockPreference.setEnabled(enabled)
                    Toast.makeText(
                        context,
                        if (enabled) "分析 Mock 已开启，重新进入分析页生效" else "分析 Mock 已关闭",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            })

            addView(TextView(context).apply {
                text = "默认关闭；关闭时使用真实服务环境。"
                textSize = 11f
                setTextColor(Color.GRAY)
            })
        }

    override fun onViewCreated(rootView: FrameLayout) = Unit

    override fun initDokitViewLayoutParams(params: DoKitViewLayoutParams) {
        params.width = resources?.displayMetrics?.widthPixels
            ?: DoKitViewLayoutParams.MATCH_PARENT
        params.height = DoKitViewLayoutParams.WRAP_CONTENT
        params.gravity = Gravity.START or Gravity.TOP
        params.x = 0
        params.y = dp(96)
    }

    private fun dp(value: Int): Int =
        (value * (resources?.displayMetrics?.density ?: 1f)).toInt()

    private companion object {
        const val COLOR_TEAL = 0xFF0F9F9A.toInt()
    }
}
