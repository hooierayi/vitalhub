package com.smarthealth.vitalhub

import androidx.lifecycle.ViewModel
import com.smarthealth.vitalhub.core.navi.AppBarDestination
import com.smarthealth.vitalhub.core.navi.BottomNavigationDestination
import com.smarthealth.vitalhub.core.navi.BottomNavigationKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppShellUiState(
    val selectedBottomKey: String = BottomNavigationKeys.COLLECTION,
    val appBarTitle: String = "VitalHub",
    val showAppBarBack: Boolean = false,
    val showNotificationAction: Boolean = false,
)

class AppShellViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppShellUiState())
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    fun updateDestination(bottom: BottomNavigationDestination?, appBar: AppBarDestination?) {
        _uiState.value = _uiState.value.copy(
            selectedBottomKey = bottom?.bottomNavigationKey ?: _uiState.value.selectedBottomKey,
            appBarTitle = appBar?.appBarTitle ?: _uiState.value.appBarTitle,
            showAppBarBack = appBar?.showAppBarBack ?: _uiState.value.showAppBarBack,
            showNotificationAction = appBar?.showNotificationAction ?: _uiState.value.showNotificationAction,
        )
    }
}
