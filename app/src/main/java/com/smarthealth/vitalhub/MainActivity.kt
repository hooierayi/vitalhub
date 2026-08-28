package com.smarthealth.vitalhub

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.AppBarDestination
import com.smarthealth.vitalhub.core.navi.BottomNavigationDestination
import com.smarthealth.vitalhub.core.navi.BottomNavigationKeys
import com.smarthealth.vitalhub.core.navi.FlowNavigationHost
import com.smarthealth.vitalhub.core.navi.FlowNavigationGate
import com.smarthealth.vitalhub.core.navi.FlowNavigationRequest
import com.smarthealth.vitalhub.core.navi.FlowNavigationResult
import com.smarthealth.vitalhub.core.navi.Navigator
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.ui.VitalHubTheme

/** Home Activity. It owns the root navigation chrome and the home Fragment stack. */
@Route(path = Routes.APP_HOME)
class MainActivity : AppCompatActivity(), FlowNavigationHost {
    private val viewModel by lazy { ViewModelProvider(this)[AppShellViewModel::class.java] }
    private lateinit var bottomBar: ComposeView
    private var bottomBarTargetVisible = true
    private val navigationGate = FlowNavigationGate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdgeWindow()
        setContentView(createRootView())
        observeVisibleFragment()
        if (savedInstanceState == null) Navigator.home(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Navigator.home(this, clearBackStack = true)
    }

    private fun createRootView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(248, 250, 252))
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                VitalHubTheme {
                    val state = viewModel.uiState.collectAsStateWithLifecycle().value
                    AppTitleBar(
                        title = state.appBarTitle,
                        showBack = state.showAppBarBack,
                        showNotification = state.showNotificationAction,
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        addView(FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(248, 250, 252))
            addView(FrameLayout(context).apply {
                id = R.id.main_fragment_container
                setBackgroundColor(Color.rgb(248, 250, 252))
            }, FrameLayout.LayoutParams(-1, -1))
            bottomBar = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    VitalHubTheme {
                        val state = viewModel.uiState.collectAsStateWithLifecycle().value
                        AppBottomNavigation(
                            selectedKey = state.selectedBottomKey,
                            onSelected = ::selectBottomDestination,
                        )
                    }
                }
            }
            addView(bottomBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun configureEdgeToEdgeWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun selectBottomDestination(key: String) {
        if (key == viewModel.uiState.value.selectedBottomKey && supportFragmentManager.backStackEntryCount == 0) return
        when (key) {
            BottomNavigationKeys.COLLECTION -> Navigator.home(this)
            else -> show(
                FlowNavigationRequest(
                    key = "section|$key",
                    fragment = AppSectionFragment.newInstance(key),
                    addToBackStack = false,
                    clearBackStack = true,
                ),
            )
        }
    }

    private fun observeVisibleFragment() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
                    updateChrome(fragment, animateBottomBar = true)
                    // A FragmentTransaction added to the back stack cannot use
                    // runOnCommit. Resuming the replacement Fragment is the first
                    // lifecycle point at which this transaction is complete.
                    fragment.tag?.let(navigationGate::finish)
                }
            },
            false,
        )
    }

    override fun show(request: FlowNavigationRequest): FlowNavigationResult {
        val navigationResult = navigationGate.begin(request.key)
        if (navigationResult != FlowNavigationResult.Navigated) return navigationResult
        if (request.clearBackStack) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        // Update the app chrome before replacing the content. The old implementation
        // hid the bottom bar while both fragments were fading through transparency,
        // briefly exposing the window background as a black frame.
        updateChrome(request.fragment, animateBottomBar = true)
        return try {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                )
                .replace(R.id.main_fragment_container, request.fragment, request.key)
                .apply { if (request.addToBackStack) addToBackStack(request.key) }
                .commit()
            FlowNavigationResult.Navigated
        } catch (error: Throwable) {
            navigationGate.finish(request.key)
            throw error
        }
    }

    private fun updateChrome(fragment: Fragment, animateBottomBar: Boolean = false) {
        val destination = fragment as? BottomNavigationDestination
        setBottomBarVisible(destination != null, animateBottomBar)
        viewModel.updateDestination(destination, fragment as? AppBarDestination)
    }

    private fun setBottomBarVisible(visible: Boolean, animate: Boolean) {
        if (visible == bottomBarTargetVisible) return
        bottomBarTargetVisible = visible
        bottomBar.animate().cancel()
        if (!animate) {
            bottomBar.visibility = if (visible) View.VISIBLE else View.GONE
            bottomBar.alpha = 1f
            bottomBar.translationY = 0f
            return
        }
        val offset = resources.displayMetrics.density * 12f
        if (visible) {
            bottomBar.visibility = View.VISIBLE
            bottomBar.alpha = 0f
            bottomBar.translationY = offset
            bottomBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start()
        } else {
            bottomBar.animate()
                .alpha(0f)
                .translationY(offset)
                .setDuration(220L)
                .withEndAction {
                    if (!bottomBarTargetVisible) bottomBar.visibility = View.GONE
                    bottomBar.alpha = 1f
                    bottomBar.translationY = 0f
                }
                .start()
        }
    }
}
