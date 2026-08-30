package com.smarthealth.vitalhub.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared title bar used by both the root app shell and feature Activity shells. */
@Composable
fun FlowTitleBar(
    title: String,
    showBack: Boolean,
    showNotification: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onBack: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = .5.dp) {
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().height(60.dp),
        ) {
            Box(
                Modifier.width(60.dp).height(60.dp).align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = VitalColors.TextPrimary,
                            modifier = Modifier.width(25.dp),
                        )
                    }
                }
            }
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = VitalColors.TextPrimary,
            )
            Box(
                Modifier.width(if (actionLabel == null) 60.dp else 124.dp)
                    .height(60.dp)
                    .align(Alignment.CenterEnd),
                contentAlignment = if (actionLabel == null) Alignment.Center else Alignment.CenterEnd,
            ) {
                if (actionLabel != null) {
                    TextButton(onClick = onAction, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(
                            text = actionLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = VitalColors.Teal,
                            maxLines = 1,
                        )
                    }
                } else if (showNotification) {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "通知",
                            tint = VitalColors.TextPrimary,
                            modifier = Modifier.width(24.dp),
                        )
                    }
                }
            }
        }
    }
}
