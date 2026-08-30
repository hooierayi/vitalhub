package com.smarthealth.vitalhub.debug.dokit.bluetooth

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
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugTrace

class BluetoothDebugDoKitView : AbsDoKitView() {
    private val handler = Handler(Looper.getMainLooper())
    private var gattStatus = "等待连接状态"
    private var gattAddress: String? = null
    private var gattProfile: String? = null
    private lateinit var gattContent: TextView
    private lateinit var rxContent: TextView
    private lateinit var txContent: TextView
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(context: Context) = Unit

    override fun onCreateView(context: Context, rootView: FrameLayout): View =
        createCard(context, "蓝牙链路", COLOR_BLUE) {
            context.startActivity(
                Intent(context, BluetoothDebugActivity::class.java)
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
        params.y = dp(96)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun render() {
        val events = DeviceDebugTrace.snapshot(STAGES)
        events.filter { it.stage == STAGE_GATT }.forEach { event ->
            updateGatt(event.message)
        }
        val latestRx = events.lastOrNull { it.stage == STAGE_RX }?.payload
        val latestTx = events.lastOrNull { it.stage == STAGE_TX }?.payload
        gattContent.text = buildString {
            append("GATT")
            append("\n状态  ")
            append(gattStatus)
            append("\n设备  ")
            append(gattAddress ?: "--")
            append("\n配置  ")
            append(gattProfile ?: "--")
        }
        rxContent.text = "BLE_RX  ${latestRx?.let(::headTailSummary) ?: "暂无接收数据"}"
        txContent.text = "BLE_TX  ${latestTx?.let(::headSummary) ?: "暂无发送数据"}"
    }

    private fun updateGatt(message: String) {
        when {
            message.startsWith("connect address=") -> {
                gattAddress = message.substringAfter("connect address=")
                gattStatus = "连接中"
            }
            message.startsWith("profile ") -> gattProfile = formatProfile(message)
            message == "connected, enabling data channel" -> gattStatus = "已连接，正在启用数据通道"
            message == "notification channel ready" -> gattStatus = "已连接，通知通道就绪"
            message.startsWith("failure=") -> gattStatus = "连接失败：${message.substringAfter("failure=")}"
            message.startsWith("disconnected active=") -> {
                val active = message.substringAfter("disconnected active=").toBooleanStrictOrNull()
                gattStatus = if (active == true) "已主动断开" else "连接已断开"
            }
            message == "disconnect requested" -> gattStatus = "正在主动断开"
            else -> gattStatus = message
        }
    }

    private fun formatProfile(message: String): String {
        val fields = message.removePrefix("profile ")
            .split(", ")
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
            }
            .toMap()
        return listOfNotNull(
            fields["service"]?.let { "S=${it.shortUuid()}" },
            fields["receive"]?.let { "RX=${it.shortUuid()}" },
            fields["write"]?.let { "TX=${it.shortUuid()}" },
            fields["type"],
            fields["descriptorByCharacteristic"]?.let { "描述符=$it" },
        ).joinToString(" · ").ifEmpty { message }
    }

    private fun String.shortUuid(): String =
        if (startsWith("0000", ignoreCase = true) && length >= 8) substring(4, 8).uppercase() else this

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

    private fun headSummary(payload: ByteArray): String = buildString {
        append(payload.size)
        append("B · ")
        append(payload.take(FULL_PREVIEW_BYTES).toHex())
        if (payload.size > FULL_PREVIEW_BYTES) append(" …")
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
        gattContent = TextView(context).apply {
            setTextColor(Color.rgb(31, 41, 55))
            textSize = 10f
            setPadding(0, dp(5), 0, 0)
        }
        addView(gattContent)
        rxContent = rawDataRow(context)
        addView(rxContent)
        txContent = rawDataRow(context)
        addView(txContent)
    }

    private fun rawDataRow(context: Context) = TextView(context).apply {
        setTextColor(Color.rgb(31, 41, 55))
        typeface = Typeface.MONOSPACE
        textSize = 9f
        isSingleLine = true
        setPadding(0, dp(2), 0, 0)
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
        const val STAGE_GATT = "GATT"
        const val STAGE_RX = "BLE_RX_RAW"
        const val STAGE_TX = "BLE_TX_RAW"
        val STAGES = setOf(STAGE_GATT, STAGE_RX, STAGE_TX)
        const val EDGE_PREVIEW_BYTES = 8
        const val FULL_PREVIEW_BYTES = EDGE_PREVIEW_BYTES * 2
        const val REFRESH_INTERVAL_MS = 750L
        const val COLOR_BLUE = 0xFF276EF1.toInt()
    }
}
