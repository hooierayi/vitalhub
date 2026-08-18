package com.smarthealth.vitalhub.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.FlowPage
import com.smarthealth.vitalhub.core.ui.FullWidthButton
import com.smarthealth.vitalhub.core.ui.InfoCard
import com.smarthealth.vitalhub.core.ui.VitalColors
import com.smarthealth.vitalhub.provider.user.Gender

@Composable
fun UserInfoEditScreen(
    state: UserInfoEditUiState,
    onNameChanged: (String) -> Unit,
    onGenderChanged: (Gender) -> Unit,
    onAgeChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    FlowPage(scrollable = false, bottomBarSafe = false) {
        InfoCard(padding = PaddingValues(18.dp), spacing = 4.dp) {
            Text("采集登记", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = VitalColors.Teal)
            Text("用户信息", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = VitalColors.TextPrimary)
            Text("姓名、性别、年龄用于本地采集记录", fontSize = 14.sp, color = VitalColors.TextSecondary)
        }
        Spacer(Modifier.height(16.dp))
        InfoCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0.dp) {
            FormField("姓名", state.name, "请输入姓名", onNameChanged)
            FormDivider()
            GenderField(state.gender, onGenderChanged)
            FormDivider()
            FormField(
                label = "年龄",
                value = state.age,
                placeholder = "请输入年龄",
                onValueChanged = onAgeChanged,
                keyboardType = KeyboardType.Number,
            )
        }
        state.validationError?.let {
            Text(it, modifier = Modifier.padding(top = 10.dp, start = 2.dp), fontSize = 13.sp, color = VitalColors.Danger)
        }
        Spacer(Modifier.weight(1f))
        FullWidthButton("保存信息", onClick = onSave)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    placeholder: String,
    onValueChanged: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(92.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
        BasicTextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 16.sp, color = VitalColors.TextPrimary),
            decorationBox = { input ->
                Box {
                    if (value.isBlank()) Text(placeholder, fontSize = 16.sp, color = VitalColors.TextMuted)
                    input()
                }
            },
        )
    }
}

@Composable
private fun GenderField(selectedGender: Gender?, onGenderChanged: (Gender) -> Unit) {
    Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("性别", modifier = Modifier.width(92.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenderOption("男", Gender.MALE, selectedGender, onGenderChanged)
            GenderOption("女", Gender.FEMALE, selectedGender, onGenderChanged)
        }
    }
}

@Composable
private fun GenderOption(
    label: String,
    gender: Gender,
    selectedGender: Gender?,
    onSelected: (Gender) -> Unit,
) {
    val selected = gender == selectedGender
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = label,
        modifier = Modifier
            .background(if (selected) VitalColors.TealPale else Color.White, shape)
            .border(1.dp, if (selected) VitalColors.Teal else VitalColors.Border, shape)
            .clickable { onSelected(gender) }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) VitalColors.Teal else VitalColors.TextSecondary,
    )
}

@Composable
private fun FormDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(VitalColors.Border))
}
