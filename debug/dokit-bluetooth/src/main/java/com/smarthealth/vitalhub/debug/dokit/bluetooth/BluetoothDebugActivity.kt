package com.smarthealth.vitalhub.debug.dokit.bluetooth

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
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugEvent
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugTrace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BluetoothDebugActivity : AppCompatActivity() {
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
        title = "蓝牙连接与原始收发"
        content = TextView(this).apply {
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
            setTextColor(Color.rgb(31, 41, 55))
            setPadding(24, 16, 24, 24)
            textSize = 12f
        }
        val clear = Button(this).apply {
            text = "清空蓝牙记录"
            setOnClickListener {
                DeviceDebugTrace.clear(BLUETOOTH_STAGES)
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
        val events = DeviceDebugTrace.snapshot(BLUETOOTH_STAGES).takeLast(MAX_VISIBLE_EVENTS)
        content.text = if (events.isEmpty()) {
            "等待设备连接或数据收发…"
        } else {
            events.joinToString("\n\n", transform = ::formatEvent)
        }
    }

    private fun formatEvent(event: DeviceDebugEvent): String = buildString {
        append(TIME_FORMAT.format(Date(event.timestampMillis)))
        append("  [")
        append(event.stage.removeSuffix("_RAW"))
        append("] ")
        append(event.message)
        event.payload?.let { payload ->
            append('\n')
            append(payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
        }
    }

    private companion object {
        val BLUETOOTH_STAGES = setOf("GATT", "BLE_RX_RAW", "BLE_TX_RAW")
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        const val MAX_VISIBLE_EVENTS = 80
        const val REFRESH_INTERVAL_MS = 750L
    }
}
