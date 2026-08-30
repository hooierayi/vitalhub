package com.smarthealth.vitalhub.debug.dokit.protocol

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
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugEvent
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugTrace

class ProtocolDebugDoKitView : AbsDoKitView() {
    private val handler = Handler(Looper.getMainLooper())
    private val latestEventByStage = mutableMapOf<String, DeviceDebugEvent>()
    private val rowViews = mutableMapOf<String, TextView>()
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(context: Context) = Unit

    override fun onCreateView(context: Context, rootView: FrameLayout): View =
        createCard(context, "协议交互", COLOR_PURPLE) {
            context.startActivity(
                Intent(context, ProtocolDebugActivity::class.java)
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
        params.y = dp(250)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun render() {
        DeviceDebugTrace.snapshot(STAGES).forEach { event ->
            latestEventByStage[event.stage] = event
        }
        DISPLAY_ROWS.forEach { row ->
            val event = latestEventByStage[row.stage]
            val summary = event?.payload?.let(::headTailSummary)
                ?: event?.message
                ?: row.emptyText
            rowViews[row.stage]?.text = "${row.label.padEnd(9)} $summary"
        }
    }

    private fun headTailSummary(payload: ByteArray): String = buildString {
        append(payload.size)
        append("B · ")
        if (payload.size <= FULL_PREVIEW_BYTES) {
            append(payload.toHex())
        } else {
            append(payload.take(EDGE_PREVIEW_BYTES).toHex())
            append(" … ")
            append(payload.takeLast(EDGE_PREVIEW_BYTES).toHex())
        }
    }

    private fun Iterable<Byte>.toHex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun ByteArray.toHex(): String = asIterable().toHex()

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
        DISPLAY_ROWS.forEachIndexed { index, row ->
            val rowView = TextView(context).apply {
                setTextColor(Color.rgb(31, 41, 55))
                typeface = Typeface.MONOSPACE
                textSize = 9f
                isSingleLine = true
                setPadding(0, dp(if (index == 0) 5 else 2), 0, 0)
            }
            rowViews[row.stage] = rowView
            addView(rowView)
        }
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
        val STAGES = setOf(
            "BUFFER",
            "PROTOCOL",
            "PROTOCOL_DATA_RAW",
            "RECOVERY",
            "COMMAND",
            "DISPATCH",
        )
        val DISPLAY_ROWS = listOf(
            DisplayRow("BUFFER", "BUFFER", "等待缓冲数据"),
            DisplayRow("PROTOCOL", "PROTOCOL", "等待拆包数据"),
            DisplayRow("PROTOCOL_DATA_RAW", "DATA", "暂无协议包数据"),
            DisplayRow("RECOVERY", "RECOVERY", "暂无恢复事件"),
            DisplayRow("COMMAND", "COMMAND", "等待指令交互"),
            DisplayRow("DISPATCH", "DISPATCH", "等待数据分发"),
        )
        const val EDGE_PREVIEW_BYTES = 8
        const val FULL_PREVIEW_BYTES = EDGE_PREVIEW_BYTES * 2
        const val REFRESH_INTERVAL_MS = 750L
        const val COLOR_PURPLE = 0xFF8B5CF6.toInt()
    }

    private data class DisplayRow(
        val stage: String,
        val label: String,
        val emptyText: String,
    )
}
