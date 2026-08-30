package com.smarthealth.vitalhub.debug.dokit.waveform

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.smarthealth.vitalhub.foundation.device.waveform.ui.WaveformBufferDebugSnapshot
import com.smarthealth.vitalhub.foundation.device.waveform.ui.WaveformDebugRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaveformDebugActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: TextView
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "画图环形缓冲"
        content = TextView(this).apply {
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
            setTextColor(Color.rgb(31, 41, 55))
            setPadding(24, 16, 24, 24)
            textSize = 13f
        }
        val clear = Button(this).apply {
            text = "清空调试快照"
            setOnClickListener {
                WaveformDebugRegistry.clear()
                render()
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(clear, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
    }

    override fun onStart() {
        super.onStart()
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    private fun render() {
        val snapshots = WaveformDebugRegistry.snapshot()
        content.text = if (snapshots.isEmpty()) {
            "等待 ECG / 呼吸波形缓冲创建并写入数据…"
        } else {
            snapshots.joinToString("\n\n", transform = ::formatSnapshot)
        }
    }

    private fun formatSnapshot(snapshot: WaveformBufferDebugSnapshot): String = buildString {
        append(snapshot.label)
        append("  更新 ")
        append(TIME_FORMAT.format(Date(snapshot.updatedAtMillis)))
        append("\n容量/已存: ${snapshot.capacity}/${snapshot.storedSamples}")
        append("  总写入: ${snapshot.totalSamples}")
        append("  覆盖: ${snapshot.overwrittenSamples}")
        append("\n可读起点: ${snapshot.firstOrdinal}")
        append("  绘制游标: ${snapshot.renderedNextOrdinal}")
        append("  待绘制: ${snapshot.pendingSamples}")
        append("\n视口中心/范围: ${"%.1f".format(snapshot.viewportCenter)}/${"%.1f".format(snapshot.viewportRange)}")
        append("\n最近追加: ${snapshot.latestAppendCount} 点")
        append("  min/max: ${snapshot.latestMinimum}/${snapshot.latestMaximum}")
        append("\n预览: ${formatPreview(snapshot)}")
    }

    private fun formatPreview(snapshot: WaveformBufferDebugSnapshot): String {
        val preview = snapshot.latestPreview
        if (snapshot.latestAppendCount <= preview.size) return preview.joinToString()
        val edgeSize = preview.size / 2
        return preview.take(edgeSize).joinToString() +
            " … " + preview.takeLast(edgeSize).joinToString()
    }

    private companion object {
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        const val REFRESH_INTERVAL_MS = 500L
    }
}
