package com.smarthealth.vitalhub.debug.dokit.protocol

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugTrace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProtocolDebugActivity : AppCompatActivity() {
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
        title = "协议指令与数据"
        content = TextView(this).apply {
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
            setPadding(24, 16, 24, 24)
            textSize = 12f
        }
        val clear = Button(this).apply {
            text = "清空协议记录"
            setOnClickListener {
                DeviceDebugTrace.clear(PROTOCOL_STAGES)
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
        val events = DeviceDebugTrace.snapshot(PROTOCOL_STAGES).takeLast(MAX_VISIBLE_EVENTS)
        content.text = if (events.isEmpty()) {
            "等待协议拆包、指令或回执…"
        } else {
            events.joinToString("\n") { event ->
                buildString {
                    append("${TIME_FORMAT.format(Date(event.timestampMillis))}  [${event.stage.removeSuffix("_RAW")}] ${event.message}")
                    event.payload?.let { payload ->
                        append("\n")
                        append(payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
                    }
                }
            }
        }
    }

    private companion object {
        val PROTOCOL_STAGES = setOf(
            "BUFFER",
            "PROTOCOL",
            "PROTOCOL_DATA_RAW",
            "RECOVERY",
            "COMMAND",
            "DISPATCH",
        )
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        const val MAX_VISIBLE_EVENTS = 300
        const val REFRESH_INTERVAL_MS = 750L
    }
}
