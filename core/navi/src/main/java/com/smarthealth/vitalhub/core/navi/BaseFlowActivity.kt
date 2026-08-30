package com.smarthealth.vitalhub.core.navi

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.smarthealth.vitalhub.core.ui.FlowTitleBar
import com.smarthealth.vitalhub.core.ui.VitalHubTheme

/** Shared Activity shell for feature-owned Fragment flows. */
abstract class BaseFlowActivity : AppCompatActivity(), FlowNavigationHost {
    private val navigationGate = FlowNavigationGate()
    private var appBarState by mutableStateOf(AppBarState())

    protected abstract val initialFragmentPath: String
    protected open val initialNavigationKey: String get() = initialFragmentPath
    protected open fun initialFragmentArguments(): Bundle = intent.extras ?: Bundle()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FlowActivityTransitions.configure(this)
        configureWindow()
        setContentView(createRootView())
        observeVisibleFragment()
        configureBackNavigation()
        if (savedInstanceState == null) {
            Navigator.fragment(
                host = this,
                path = initialFragmentPath,
                arguments = initialFragmentArguments(),
                key = initialNavigationKey,
                addToBackStack = false,
            )
        }
    }

    protected open fun onRootBackPressed(): Boolean = false

    override fun finish() {
        super.finish()
        FlowActivityTransitions.applyAfterFinish(this)
    }

    private fun createRootView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(248, 250, 252))
        addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    VitalHubTheme {
                        FlowTitleBar(
                            title = appBarState.title,
                            showBack = appBarState.showBack,
                            actionLabel = appBarState.actionLabel,
                            onAction = appBarState.onAction,
                            onBack = { onBackPressedDispatcher.onBackPressed() },
                        )
                    }
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        addView(
            FrameLayout(context).apply {
                id = R.id.flow_fragment_container
                setBackgroundColor(Color.rgb(248, 250, 252))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
    }

    private fun configureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else if (!onRootBackPressed()) {
                    finish()
                }
            }
        })
    }

    private fun observeVisibleFragment() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
                    if (fragment.id != R.id.flow_fragment_container) return
                    updateAppBar(fragment)
                    fragment.tag?.let(navigationGate::finish)
                }
            },
            false,
        )
    }

    override fun show(request: FlowNavigationRequest): FlowNavigationResult {
        val result = navigationGate.begin(request.key)
        if (result != FlowNavigationResult.Navigated) return result
        if (request.clearBackStack) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        updateAppBar(request.fragment)
        return try {
            val transaction = supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                )
            if (request.addToBackStack) {
                supportFragmentManager.findFragmentById(R.id.flow_fragment_container)?.let { current ->
                    transaction
                        .setMaxLifecycle(current, Lifecycle.State.STARTED)
                        .hide(current)
                }
                transaction
                    .add(R.id.flow_fragment_container, request.fragment, request.key)
                    .setPrimaryNavigationFragment(request.fragment)
                    .addToBackStack(request.key)
            } else {
                transaction
                    .replace(R.id.flow_fragment_container, request.fragment, request.key)
                    .setPrimaryNavigationFragment(request.fragment)
            }
            transaction.commit()
            FlowNavigationResult.Navigated
        } catch (error: Throwable) {
            navigationGate.finish(request.key)
            throw error
        }
    }

    private fun updateAppBar(fragment: Fragment) {
        val destination = fragment as? AppBarDestination
        val actionDestination = fragment as? AppBarActionDestination
        appBarState = AppBarState(
            title = destination?.appBarTitle.orEmpty(),
            showBack = destination?.showAppBarBack ?: true,
            actionLabel = actionDestination?.appBarActionLabel,
            onAction = actionDestination?.let { { it.onAppBarAction() } } ?: {},
        )
    }

    private data class AppBarState(
        val title: String = "",
        val showBack: Boolean = true,
        val actionLabel: String? = null,
        val onAction: () -> Unit = {},
    )
}
