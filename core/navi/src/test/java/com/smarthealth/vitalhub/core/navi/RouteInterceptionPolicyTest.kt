package com.smarthealth.vitalhub.core.navi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteInterceptionPolicyTest {
    @Test
    fun usesTheLatestDynamicallyInjectedRouteRules() {
        val guardedRoutes = mutableSetOf<String>()
        RouteInterceptionPolicy.configure { it in guardedRoutes }

        assertFalse(RouteInterceptionPolicy.requiresInterception(Routes.DEVICE))

        guardedRoutes += Routes.DEVICE
        assertTrue(RouteInterceptionPolicy.requiresInterception(Routes.DEVICE))
    }
}
