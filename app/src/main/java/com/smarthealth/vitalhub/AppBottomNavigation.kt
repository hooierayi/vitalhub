package com.smarthealth.vitalhub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.navigation.BottomNavigationKeys
import com.smarthealth.vitalhub.core.ui.VitalColors

private data class BottomItem(val key: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(BottomNavigationKeys.COLLECTION, "采集", Icons.Default.Home),
    BottomItem(BottomNavigationKeys.RECORDS, "记录", Icons.Outlined.CheckCircle),
    BottomItem(BottomNavigationKeys.REPORTS, "报告", Icons.Outlined.Info),
    BottomItem(BottomNavigationKeys.PROFILE, "我的", Icons.Default.Person),
)

@Composable
fun AppBottomNavigation(selectedKey: String, onSelected: (String) -> Unit) {
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(71.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomItems.forEach { item ->
                val color = if (selectedKey == item.key) VitalColors.Teal else VitalColors.TextSecondary
                Column(
                    Modifier.weight(1f).height(71.dp).clickable { onSelected(item.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, item.label, tint = color, modifier = Modifier.height(25.dp))
                    Text(item.label, fontSize = 13.sp, color = color)
                }
            }
        }
    }
}
