package com.smarthealth.vitalhub.feature.collection

import android.os.Bundle
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.BaseFlowActivity
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.core.navi.Routes

/** Owns the connected device and collection Fragment stack for one collection session. */
@Route(path = Routes.COLLECTION_FLOW)
class CollectionFlowActivity : BaseFlowActivity() {
    private val initialDestination: FlowDestination by lazy {
        intent.getStringExtra(RouteArgs.FLOW_DESTINATION)
            ?.let { value -> FlowDestination.entries.firstOrNull { it.name == value } }
            ?.takeIf(::isCollectionDestination)
            ?: FlowDestination.DEVICE_CONNECTION
    }

    override val initialFragmentPath: String
        get() = if (initialDestination == FlowDestination.DEVICE_CONNECTION) Routes.COLLECTION_FLOW_HOME else Routes.COLLECTION

    override val initialNavigationKey: String
        get() = "$initialFragmentPath|${intent.getStringExtra(RouteArgs.SESSION_ID).orEmpty()}|${initialDestination.name}"

    override fun initialFragmentArguments(): Bundle = Bundle().apply {
        putString(RouteArgs.SESSION_ID, intent.getStringExtra(RouteArgs.SESSION_ID).orEmpty())
        if (initialFragmentPath == Routes.COLLECTION) {
            putString(RouteArgs.COLLECTION_MODE, initialDestination.collectionMode())
        }
    }

    private fun FlowDestination.collectionMode(): String = when (this) {
        FlowDestination.CLIP_COLLECTION -> CollectionMode.CLIP
        FlowDestination.CONTINUOUS_RECORDING -> CollectionMode.CONTINUOUS
        else -> CollectionMode.PREVIEW
    }

    private fun isCollectionDestination(destination: FlowDestination): Boolean = destination in setOf(
        FlowDestination.DEVICE_CONNECTION,
        FlowDestination.LIVE_PREVIEW,
        FlowDestination.CLIP_COLLECTION,
        FlowDestination.CONTINUOUS_RECORDING,
    )
}
