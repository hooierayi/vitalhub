package com.smarthealth.vitalhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.VitalColors

@Composable
fun AppTitleBar(title: String, showBack: Boolean, showNotification: Boolean, onBack: () -> Unit) {
    Surface(color = Color.White, shadowElevation = .5.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(60.dp).height(60.dp), contentAlignment = Alignment.Center) {
                if (showBack) IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = VitalColors.TextPrimary, modifier = Modifier.width(25.dp))
                }
            }
            Text(
                title,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = VitalColors.TextPrimary,
            )
            Box(Modifier.width(60.dp).height(60.dp), contentAlignment = Alignment.Center) {
                if (showNotification) IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "通知", tint = VitalColors.TextPrimary, modifier = Modifier.width(24.dp))
                }
            }
        }
    }
}
