package com.smarthealth.vitalhub.feature.collection

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.smarthealth.vitalhub.core.ui.*
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.feature.collection.shared.DeviceConnectionOperation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class CardPlacement(val position: Offset, val size: IntSize)

private data class DeviceSwap(
    val fromAvailable: BluetoothKitDevice,
    val toAvailable: BluetoothKitDevice,
    val fromPlacement: CardPlacement,
    val toPlacement: CardPlacement,
) {
}

@Composable
fun CollectionFlowHomeScreen(
    state: CollectionFlowHomeUiState,
    onProjectOnlyChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val availableDevice = state.availableDevice()
    val availableDeviceConnected = availableDevice?.key == state.connectedDeviceId
    val otherDevices = state.otherDevices()
    val coroutineScope = rememberCoroutineScope()
    val swapProgress = remember { Animatable(0f) }
    var activeSwap by remember { mutableStateOf<DeviceSwap?>(null) }
    var rootWindowPosition by remember { mutableStateOf(Offset.Zero) }
    val cardPlacements = remember { mutableStateMapOf<String, CardPlacement>() }

    fun measuredCardModifier(deviceId: String): Modifier {
        val measuredModifier = Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            val placement = CardPlacement(
                position = bounds.topLeft - rootWindowPosition,
                size = coordinates.size,
            )
            if (cardPlacements[deviceId] != placement) cardPlacements[deviceId] = placement
        }
        return if (
            activeSwap?.fromAvailable?.key == deviceId &&
            availableDevice?.key == deviceId
        ) {
            measuredModifier.graphicsLayer {
                alpha = (1f - swapProgress.value / 0.18f).coerceIn(0f, 1f)
            }
        } else {
            measuredModifier
        }
    }

    fun startDeviceSwap(targetDeviceId: String) {
        val currentAvailableId = availableDevice?.key ?: return
        if (
            activeSwap != null ||
            state.deviceOperation != null ||
            availableDeviceConnected ||
            currentAvailableId == targetDeviceId
        ) return
        val targetDevice = otherDevices.firstOrNull { it.key == targetDeviceId } ?: return
        val fromPlacement = cardPlacements[currentAvailableId] ?: return
        val toPlacement = cardPlacements[targetDeviceId] ?: return
        coroutineScope.launch {
            activeSwap = DeviceSwap(
                fromAvailable = availableDevice,
                toAvailable = targetDevice,
                fromPlacement = fromPlacement,
                toPlacement = toPlacement,
            )
            swapProgress.snapTo(0f)
            swapProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
            onDeviceSelected(targetDeviceId)
            withFrameNanos { }
            withFrameNanos { }
            activeSwap = null
            swapProgress.snapTo(0f)
        }
    }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            rootWindowPosition = coordinates.boundsInWindow().topLeft
        },
    ) {
        FlowPage(scrollable = false) {
        ScanBanner(state.scanning)
        Row(Modifier.fillMaxWidth().height(55.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("仅显示本项目设备", Modifier.weight(1f), fontSize = 15.sp, color = VitalColors.TextPrimary)
            Switch(state.projectOnly, onProjectOnlyChanged, modifier = Modifier.size(49.dp, 30.dp), colors = SwitchDefaults.colors(checkedTrackColor = VitalColors.Teal, checkedThumbColor = Color.White))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("可用设备", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
            RefreshButton(scanning = state.scanning, onRefresh = onRefresh)
        }
        if (availableDevice == null) {
            EmptyDevices(if (state.scanning) "正在查找可用设备" else "未发现可用设备")
        } else {
            DeviceCard(
                device = availableDevice,
                remembered = availableDevice.key == state.lastConnectedDevice?.key,
                modifier = measuredCardModifier(availableDevice.key),
                operation = state.deviceOperation,
                connected = availableDeviceConnected,
                selectAction = if (availableDeviceConnected) onContinue else null,
                primaryAction = {
                    if (activeSwap == null) {
                        if (availableDeviceConnected) {
                            onDisconnect(availableDevice.key)
                        } else {
                            onConnect(availableDevice.key)
                        }
                    }
                },
            )
        }
        state.connectionError?.let { Text(it, Modifier.padding(top = 8.dp), color = VitalColors.Danger, fontSize = 14.sp) }
        state.flowError?.let { Text(it, color = VitalColors.Danger, fontSize = 14.sp) }
        SectionTitle("其他设备", top = 16.dp)
        if (otherDevices.isEmpty()) {
            EmptyDevices("未发现其他设备", Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(
                    items = otherDevices,
                    key = { index, _ -> "other-device-slot-$index" },
                ) { _, device ->
                    val displayedDevice = activeSwap
                        ?.takeIf { it.toAvailable.key == device.key }
                        ?.fromAvailable
                        ?: device
                    DeviceCard(
                        device = displayedDevice,
                        remembered = displayedDevice.key == state.lastConnectedDevice?.key,
                        modifier = measuredCardModifier(device.key),
                        selectAction = if (availableDeviceConnected) null else {
                            { startDeviceSwap(device.key) }
                        },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(VitalColors.Border))
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalArrangement = Arrangement.Center) {
            Text("未找到设备？", fontSize = 14.sp, color = VitalColors.TextSecondary); Text(" 查看帮助", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VitalColors.Teal)
        }
        }
        activeSwap?.let { swap ->
            DeviceSwapOverlay(
                swap = swap,
                progress = { swapProgress.value },
                rememberedDeviceId = state.lastConnectedDevice?.key,
            )
        }
    }
}

@Composable
private fun RefreshButton(scanning: Boolean, onRefresh: () -> Unit) {
    val rotation = if (scanning) {
        val transition = rememberInfiniteTransition(label = "device refresh rotation")
        val animatedRotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "device refresh rotation angle",
        )
        animatedRotation
    } else {
        0f
    }
    IconButton(onClick = onRefresh, enabled = !scanning, modifier = Modifier.size(32.dp)) {
        Icon(
            Icons.Default.Refresh,
            if (scanning) "正在扫描" else "刷新",
            tint = if (scanning) VitalColors.TextMuted else VitalColors.TextSecondary,
            modifier = Modifier.size(21.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun EmptyDevices(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 21.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, textAlign = TextAlign.Center, fontSize = 14.sp, color = VitalColors.TextSecondary)
    }
}

@Composable
private fun ScanBanner(scanning: Boolean) {
    Row(Modifier.fillMaxWidth().background(VitalColors.BluePale, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ScanningSignalIcon(scanning, Modifier.padding(end = 14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (scanning) "正在扫描附近的设备…" else "扫描已完成", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
            Text("请将设备靠近手机", fontSize = 13.sp, color = VitalColors.TextSecondary)
        }
    }
}

@Composable
private fun ScanningSignalIcon(scanning: Boolean, modifier: Modifier = Modifier) {
    val progress = if (scanning) {
        val transition = rememberInfiniteTransition(label = "device scan signal")
        val animatedProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(1_500), repeatMode = RepeatMode.Restart),
            label = "device scan signal pulse",
        )
        animatedProgress
    } else {
        0f
    }
    val signalColor = Color(0xFF62A9FF)
    Canvas(
        modifier
            .size(46.dp)
            .semantics { contentDescription = if (scanning) "正在扫描附近设备" else "设备扫描已完成" },
    ) {
        drawCircle(signalColor, radius = 4.dp.toPx())
        repeat(3) { index ->
            if (scanning) {
                val phase = (progress + index / 3f) % 1f
                drawCircle(
                    color = signalColor.copy(alpha = (1f - phase) * 0.8f),
                    radius = size.minDimension * (0.16f + phase * 0.34f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else {
                drawCircle(
                    color = signalColor.copy(alpha = 0.8f - index * 0.18f),
                    radius = size.minDimension * (0.18f + index * 0.12f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BluetoothKitDevice,
    remembered: Boolean,
    modifier: Modifier = Modifier,
    operation: DeviceConnectionOperation? = null,
    connected: Boolean = false,
    connectProgress: Float = 1f,
    selectAction: (() -> Unit)? = null,
    primaryAction: (() -> Unit)? = null,
) {
    val cardModifier = if (selectAction == null) modifier else modifier.clickable(onClick = selectAction)
    InfoCard(
        modifier = cardModifier,
        borderColor = Color(0xFF98D5D2),
        padding = PaddingValues(13.dp),
        spacing = 0.dp,
    ) {
        DeviceSummary(
            device = device,
            remembered = remembered,
            connected = connected,
        )
        primaryAction?.let { action ->
            val progress = connectProgress.coerceIn(0f, 1f)
            if (progress > 0f) {
                Spacer(Modifier.height(12.dp * progress))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(51.dp * progress)
                        .clipToBounds(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth().graphicsLayer {
                            alpha = progress
                            scaleY = 0.9f + progress * 0.1f
                        },
                    ) {
                        FullWidthButton(
                            label = when {
                                operation == DeviceConnectionOperation.DISCONNECTING -> "断开中…"
                                connected -> "断开设备"
                                operation == DeviceConnectionOperation.CONNECTING -> "连接中…"
                                else -> "连接"
                            },
                            style = if (connected) FlowButtonStyle.DANGER else FlowButtonStyle.PRIMARY,
                            onClick = action,
                            loading = operation != null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@SuppressLint("MissingPermission")
private fun DeviceSummary(
    device: BluetoothKitDevice,
    remembered: Boolean,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.height(70.dp)) {
        Box(Modifier.size(40.dp, 58.dp).border(1.dp, VitalColors.TextSecondary, RoundedCornerShape(7.dp)))
        Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.bluetoothDevice?.name?.takeIf(String::isNotBlank) ?: "-",
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VitalColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (device.isProjectDevice()) Text("本项目设备", Modifier.padding(start = 10.dp).background(VitalColors.TealPale, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = VitalColors.Teal)
                }
                if (remembered) {
                    Text(
                        text = if (connected) "已连接" else "已掉线",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                if (connected) VitalColors.TealPale else VitalColors.Danger.copy(alpha = 0.1f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = if (connected) VitalColors.Success else VitalColors.Danger,
                    )
                }
            }
            Text(
                "设备地址：${device.key.takeIf(String::isNotBlank) ?: "-"}",
                fontSize = 13.sp,
                color = VitalColors.TextSecondary,
            )
            Text(signalText(device.scanSignalDbm()), fontSize = 13.sp, color = VitalColors.TextSecondary)
        }
    }
}

@Composable
private fun DeviceSwapOverlay(
    swap: DeviceSwap,
    progress: () -> Float,
    rememberedDeviceId: String?,
) {
    val density = LocalDensity.current
    DeviceCard(
        device = swap.toAvailable,
        remembered = swap.toAvailable.key == rememberedDeviceId,
        connectProgress = ((progress() - 0.72f) / 0.28f).coerceIn(0f, 1f),
        modifier = Modifier
            .zIndex(3f)
            .offset {
                val movementProgress = (progress() / 0.78f).coerceIn(0f, 1f)
                movingOffset(swap.toPlacement.position, swap.fromPlacement.position, movementProgress)
            }
            .width(with(density) { swap.toPlacement.size.width.toDp() })
            .graphicsLayer {
                val scale = 0.98f + progress() * 0.02f
                scaleX = scale
                scaleY = scale
            },
        primaryAction = {},
    )
}

private fun movingOffset(start: Offset, end: Offset, progress: Float): IntOffset = IntOffset(
    x = (start.x + (end.x - start.x) * progress).roundToInt(),
    y = (start.y + (end.y - start.y) * progress).roundToInt(),
)

private fun signalText(signalDbm: Int?): String = when {
    signalDbm == null -> "信号：-"
    signalDbm >= -60 -> "信号：强    $signalDbm dBm"
    signalDbm >= -75 -> "信号：中    $signalDbm dBm"
    else -> "信号：弱    $signalDbm dBm"
}

private fun BluetoothKitDevice.scanSignalDbm(): Int? =
    (this as? BluetoothGattDevice)?.rssi?.takeIf { timestampNanos > 0L }
