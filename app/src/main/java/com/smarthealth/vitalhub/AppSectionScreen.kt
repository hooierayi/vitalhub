package com.smarthealth.vitalhub

import androidx.compose.runtime.Composable
import com.smarthealth.vitalhub.core.ui.*

@Composable
fun AppSectionScreen(state: AppSectionUiState) {
    FlowPage(bottomBarSafe = true) {
        SectionTitle(state.section)
        InfoCard {
            InfoLine(state.description, strong = true)
            InfoLine("当前为导航模块第一版占位内容，后续接入本地任务库和服务端数据。")
        }
    }
}
