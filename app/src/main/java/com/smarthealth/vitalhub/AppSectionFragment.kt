package com.smarthealth.vitalhub

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarthealth.vitalhub.core.navi.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment

class AppSectionFragment : BaseFlowFragment(), BottomNavigationDestination, AppBarDestination {
    private val viewModel by viewModels<AppSectionViewModel>()
    override val bottomNavigationKey: String get() = arguments?.getString(ARG_SECTION) ?: BottomNavigationKeys.RECORDS
    // MainActivity reads this before the Fragment is attached, so it must stay argument-only.
    override val appBarTitle: String get() = when (bottomNavigationKey) {
        BottomNavigationKeys.REPORTS -> "健康报告"
        BottomNavigationKeys.PROFILE -> "我的"
        else -> "采集记录"
    }
    override val showAppBarBack = false

    @Composable
    override fun ScreenContent() = AppSectionScreen(viewModel.uiState.collectAsStateWithLifecycle().value)

    companion object {
        const val ARG_SECTION = "appBottomSection"
        fun newInstance(key: String) = AppSectionFragment().apply { arguments = Bundle().apply { putString(ARG_SECTION, key) } }
    }
}
