package com.smarthealth.vitalhub.debug.dokit.waveform

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.didichuxing.doraemonkit.kit.core.AbsDoKitView
import com.didichuxing.doraemonkit.kit.core.DoKitViewLayoutParams
import com.smarthealth.vitalhub.foundation.device.waveform.ui.WaveformBufferDebugSnapshot
import com.smarthealth.vitalhub.foundation.device.waveform.ui.WaveformDebugRegistry

class WaveformDebugDoKitView : AbsDoKitView() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ecgSection: WaveformSectionViews
    private lateinit var respirationSection: WaveformSectionViews
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(context: Context) = Unit

    override fun onCreateView(context: Context, rootView: FrameLayout): View =
        createCard(context, "波形缓冲", COLOR_GREEN) {
            context.startActivity(
                Intent(context, WaveformDebugActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

    override fun onViewCreated(rootView: FrameLayout) {
        render()
        handler.post(refresh)
    }

    override fun initDokitViewLayoutParams(params: DoKitViewLayoutParams) {
        params.width = resources?.displayMetrics?.widthPixels
            ?: DoKitViewLayoutParams.MATCH_PARENT
        params.height = DoKitViewLayoutParams.WRAP_CONTENT
        params.gravity = Gravity.START or Gravity.TOP
        params.x = 0
        params.y = dp(404)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun render() {
        val snapshots = WaveformDebugRegistry.snapshot().associateBy { it.label }
        renderSection(ecgSection, snapshots[LABEL_ECG])
        renderSection(respirationSection, snapshots[LABEL_RESPIRATION])
    }

    private fun renderSection(
        section: WaveformSectionViews,
        snapshot: WaveformBufferDebugSnapshot?,
    ) {
        section.summary.text = if (snapshot == null) {
            "${section.label}\n缓冲  --/-- · 总写入 -- · 覆盖 --\n游标  起点 -- · 绘制 -- · 待绘制 --\n视口  中心 -- · 范围 -- · min/max --/--"
        } else {
            buildString {
                append(section.label)
                append("\n缓冲  ${snapshot.storedSamples}/${snapshot.capacity}")
                append(" · 总写入 ${snapshot.totalSamples} · 覆盖 ${snapshot.overwrittenSamples}")
                append("\n游标  起点 ${snapshot.firstOrdinal}")
                append(" · 绘制 ${snapshot.renderedNextOrdinal} · 待绘制 ${snapshot.pendingSamples}")
                append("\n视口  中心 ${"%.1f".format(snapshot.viewportCenter)}")
                append(" · 范围 ${"%.1f".format(snapshot.viewportRange)}")
                append(" · min/max ${snapshot.latestMinimum}/${snapshot.latestMaximum}")
            }
        }
        section.data.text = if (snapshot == null || snapshot.latestPreview.isEmpty()) {
            "数据  暂无采样"
        } else {
            "数据  ${snapshot.latestAppendCount}点 · ${formatPreview(snapshot)}"
        }
    }

    private fun formatPreview(snapshot: WaveformBufferDebugSnapshot): String {
        val preview = snapshot.latestPreview
        return if (snapshot.latestAppendCount <= VISIBLE_SAMPLE_COUNT) {
            preview.joinToString(" ")
        } else {
            preview.take(SAMPLE_EDGE_COUNT).joinToString(" ") +
                " … " + preview.takeLast(SAMPLE_EDGE_COUNT).joinToString(" ")
        }
    }

    private fun createCard(
        context: Context,
        title: String,
        accent: Int,
        openDetails: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(10))
        elevation = dp(8).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.argb(242, 255, 255, 255))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), accent)
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = title
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 14f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(action(context, "详情", openDetails))
            addView(action(context, "×") { detach() })
        })
        ecgSection = createSection(context, LABEL_ECG, topPadding = 5)
        addView(ecgSection.root)
        respirationSection = createSection(context, LABEL_RESPIRATION, topPadding = 7)
        addView(respirationSection.root)
    }

    private fun createSection(
        context: Context,
        label: String,
        topPadding: Int,
    ): WaveformSectionViews {
        val summary = TextView(context).apply {
            setTextColor(Color.rgb(31, 41, 55))
            textSize = 9.5f
        }
        val data = TextView(context).apply {
            setTextColor(Color.rgb(31, 41, 55))
            typeface = Typeface.MONOSPACE
            textSize = 9f
            isSingleLine = true
            setPadding(0, dp(2), 0, 0)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(topPadding), 0, 0)
            addView(summary)
            addView(data)
        }
        return WaveformSectionViews(label, root, summary, data)
    }

    private fun action(context: Context, label: String, click: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 12f
        setTextColor(Color.DKGRAY)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(4), dp(4))
        setOnClickListener { click() }
    }

    private fun dp(value: Int): Int =
        (value * (resources?.displayMetrics?.density ?: 1f)).toInt()

    private companion object {
        const val LABEL_ECG = "ECG"
        const val LABEL_RESPIRATION = "呼吸"
        const val SAMPLE_EDGE_COUNT = 4
        const val VISIBLE_SAMPLE_COUNT = SAMPLE_EDGE_COUNT * 2
        const val REFRESH_INTERVAL_MS = 500L
        const val COLOR_GREEN = 0xFF16A085.toInt()
    }

    private data class WaveformSectionViews(
        val label: String,
        val root: LinearLayout,
        val summary: TextView,
        val data: TextView,
    )
}
