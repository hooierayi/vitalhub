package com.smarthealth.vitalhub.feature.collection

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.BaseFlowActivity
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.FlowDestinationOwner
import com.smarthealth.vitalhub.core.navi.FlowEntryMode
import com.smarthealth.vitalhub.core.navi.Navigator
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProvider
import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProviderState
import kotlinx.coroutines.launch

internal fun FlowDestination.showsConnectionLostDialog(): Boolean = this in setOf(
    FlowDestination.LIVE_PREVIEW,
    FlowDestination.CLIP_COLLECTION,
    FlowDestination.CONTINUOUS_RECORDING,
)

internal fun FlowDestination.restartsCollectionAfterReconnect(): Boolean = this in setOf(
    FlowDestination.LIVE_PREVIEW,
    FlowDestination.CLIP_COLLECTION,
)

/** Owns the connected device and collection Fragment stack for one collection session. */
@Route(path = Routes.COLLECTION_FLOW)
class CollectionFlowActivity : BaseFlowActivity() {
    private val collectionBluetoothProvider by viewModels<CollectionBluetoothProvider>()
    private var connectionLostDialog: AlertDialog? = null

    private val initialDestination: FlowDestination by lazy {
        intent.getStringExtra(RouteArgs.FLOW_DESTINATION)
            ?.let { value -> FlowDestination.entries.firstOrNull { it.name == value } }
            ?.takeIf(::isCollectionDestination)
            ?: FlowDestination.DEVICE_CONNECTION
    }

    override val initialFragmentPath: String
        get() = if (initialDestination == FlowDestination.DEVICE_CONNECTION) Routes.COLLECTION_FLOW_HOME else Routes.COLLECTION

    override val initialNavigationKey: String
        get() = "$initialFragmentPath|${intent.getStringExtra(RouteArgs.SESSION_ID).orEmpty()}|${initialDestination.name}"

    override fun initialFragmentArguments(): Bundle = Bundle().apply {
        putString(RouteArgs.SESSION_ID, intent.getStringExtra(RouteArgs.SESSION_ID).orEmpty())
        putString(
            RouteArgs.FLOW_ENTRY_MODE,
            intent.getStringExtra(RouteArgs.FLOW_ENTRY_MODE) ?: FlowEntryMode.SEQUENTIAL,
        )
        if (initialFragmentPath == Routes.COLLECTION) {
            putString(RouteArgs.COLLECTION_MODE, initialDestination.collectionMode())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeUnexpectedDisconnection()
    }

    override fun onDestroy() {
        connectionLostDialog?.dismiss()
        connectionLostDialog = null
        super.onDestroy()
    }

    private fun observeUnexpectedDisconnection() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    collectionBluetoothProvider.connectionLostEvents.collect {
                        if (isCollectionPageVisible()) showConnectionLostDialog()
                    }
                }
                launch {
                    collectionBluetoothProvider.uiState.collect(::updateConnectionLostDialog)
                }
            }
        }
    }

    private fun isCollectionPageVisible(): Boolean {
        return currentFlowDestination()?.showsConnectionLostDialog() == true
    }

    private fun currentFlowDestination(): FlowDestination? =
        (supportFragmentManager.primaryNavigationFragment as? FlowDestinationOwner)
            ?.flowDestinationContext
            ?.destination

    private fun showConnectionLostDialog() {
        if (connectionLostDialog?.isShowing == true || isFinishing || isDestroyed) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("蓝牙连接已断开")
            .setMessage("记录仪连接已中断，请退出采集或重新连接设备。")
            .setNegativeButton("退出") { _, _ -> exitToCollectionHome() }
            .setPositiveButton("重新连接", null)
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val restartCollection = currentFlowDestination()
                    ?.restartsCollectionAfterReconnect() == true
                if (!collectionBluetoothProvider.reconnect(restartCollection)) {
                    dialog.setMessage("未找到可重新连接的设备，请退出采集后重新选择设备。")
                }
                updateConnectionLostDialog(collectionBluetoothProvider.uiState.value)
            }
        }
        dialog.setOnDismissListener {
            if (connectionLostDialog === dialog) connectionLostDialog = null
        }
        connectionLostDialog = dialog
        dialog.show()
        updateConnectionLostDialog(collectionBluetoothProvider.uiState.value)
    }

    private fun updateConnectionLostDialog(state: CollectionBluetoothProviderState) {
        val dialog = connectionLostDialog?.takeIf { it.isShowing } ?: return
        val reconnectButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        when {
            state.connectedDeviceId != null -> dialog.dismiss()
            state.connectingDeviceId != null -> {
                dialog.setMessage("正在重新连接记录仪，请稍候…")
                reconnectButton.isEnabled = false
            }
            state.connectionError != null -> {
                dialog.setMessage("重新连接失败：${state.connectionError}\n请确认设备已开启并靠近手机后重试。")
                reconnectButton.isEnabled = true
            }
            else -> reconnectButton.isEnabled = true
        }
    }

    private fun exitToCollectionHome() {
        lifecycleScope.launch {
            runCatching { collectionBluetoothProvider.stopRecording() }
            val hasRetainedHome = supportFragmentManager.fragments.any {
                it is CollectionFlowHomeFragment
            }
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
            if (!hasRetainedHome) {
                Navigator.collection(
                    host = this@CollectionFlowActivity,
                    sessionId = intent.getStringExtra(RouteArgs.SESSION_ID).orEmpty(),
                    destination = FlowDestination.DEVICE_CONNECTION,
                    addToBackStack = false,
                    entryMode = intent.getStringExtra(RouteArgs.FLOW_ENTRY_MODE)
                        ?: FlowEntryMode.SEQUENTIAL,
                )
            }
        }
    }

    private fun FlowDestination.collectionMode(): String = when (this) {
        FlowDestination.CLIP_COLLECTION -> CollectionMode.CLIP
        FlowDestination.CONTINUOUS_RECORDING -> CollectionMode.CONTINUOUS
        else -> CollectionMode.PREVIEW
    }

    private fun isCollectionDestination(destination: FlowDestination): Boolean = destination in setOf(
        FlowDestination.DEVICE_CONNECTION,
        FlowDestination.LIVE_PREVIEW,
        FlowDestination.CLIP_COLLECTION,
        FlowDestination.CONTINUOUS_RECORDING,
    )
}
