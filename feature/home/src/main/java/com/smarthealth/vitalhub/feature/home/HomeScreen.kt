package com.smarthealth.vitalhub.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.*
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfo

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartQuestionnaire: () -> Unit,
    onEditUserInfo: () -> Unit,
    onContinueStep: (Int) -> Unit,
) {
    FlowPage(scrollable = false, navigationSafe = false, bottomBarSafe = true) {
        PatientCard(state.user, onEditUserInfo)
        Spacer(Modifier.height(12.dp))
        DeviceStatusCard(state.device)
        SectionTitle("采集流程", "${state.completedSteps}/${state.steps.size} 步完成", top = 15.dp)
        InfoCard(padding = PaddingValues(vertical = 5.dp, horizontal = 14.dp), spacing = 0.dp) {
            state.steps.forEach { ProcessStep(it) { onContinueStep(it.number) } }
        }
        state.progressError?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp, color = VitalColors.Danger)
        }
        Spacer(Modifier.weight(1f))
        FullWidthButton(
            label = if (state.user == null) "填写用户信息" else "填写采集前问卷",
            onClick = if (state.user == null) onEditUserInfo else onStartQuestionnaire,
        )
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun PatientCard(user: UserInfo?, onEditUserInfo: () -> Unit) {
    InfoCard(padding = PaddingValues(15.dp), spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(55.dp).background(VitalColors.TealPale, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(18.dp).background(VitalColors.Teal, CircleShape))
                    Box(Modifier.size(31.dp, 15.dp).background(VitalColors.Teal, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (user == null) {
                    Text("用户信息未填写", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VitalColors.TextPrimary)
                    Text("请先填写姓名、性别和年龄", fontSize = 13.sp, color = VitalColors.TextSecondary)
                } else {
                    Text("${user.name}   ${user.gender.displayName}   ${user.age}岁", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VitalColors.TextPrimary)
                }
            }
            if (user == null) {
                Text(
                    "去填写",
                    modifier = Modifier.clickable(onClick = onEditUserInfo).padding(vertical = 10.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = VitalColors.Teal,
                )
            } else {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑用户信息",
                    tint = VitalColors.TextSecondary,
                    modifier = Modifier.size(21.dp).clickable(onClick = onEditUserInfo),
                )
            }
        }
    }
}

private val Gender.displayName: String
    get() = when (this) {
        Gender.MALE -> "男"
        Gender.FEMALE -> "女"
        Gender.UNSPECIFIED -> ""
    }

@Composable
private fun DeviceStatusCard(device: DeviceSummary) {
    InfoCard(padding = PaddingValues(15.dp), spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp, 60.dp).border(1.dp, VitalColors.TextSecondary, RoundedCornerShape(8.dp)))
            Column(Modifier.padding(start = 18.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${device.name}连接状态", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
                Text(if (device.connected) "设备已连接" else "请先连接设备", fontSize = 13.sp, color = VitalColors.TextSecondary)
            }
            Text(if (device.connected) "已连接" else "未连接", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (device.connected) VitalColors.Success else VitalColors.Danger)
        }
    }
}

@Composable
private fun ProcessStep(step: CollectionStep, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(63.dp)
            .clickable(enabled = step.enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val circleModifier = Modifier.size(35.dp).let { modifier ->
            if (step.completed) {
                modifier.background(VitalColors.Teal, CircleShape)
            } else {
                modifier.background(Color.Transparent, CircleShape).border(1.5.dp, VitalColors.Teal, CircleShape)
            }
        }
        Box(circleModifier, contentAlignment = Alignment.Center) {
            Text(
                step.number.toString(),
                color = if (step.completed) Color.White else VitalColors.Teal,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
            Text(step.subtitle, fontSize = 12.sp, color = VitalColors.TextSecondary)
        }
        if (step.completed || step.enabled) {
            Text(
                if (step.completed) "✓" else "›",
                fontSize = 25.sp,
                color = if (step.completed) VitalColors.Success else VitalColors.TextMuted,
            )
        }
    }
}
