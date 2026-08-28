package com.smarthealth.vitalhub.core.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimePermissionTest {
    @Test
    fun permissionDefinitionIsSuppliedByTheEmbeddingApplication() {
        val permission = com.smarthealth.vitalhub.core.permission.model.RuntimePermission(
            id = "test.permission",
            description = "测试权限",
            requestCode = 1,
            permissionsProvider = { sdk -> if (sdk >= 31) listOf("new") else listOf("old") },
        )

        assertEquals(listOf("old"), permission.permissionsForSdk(30))
        assertEquals(listOf("new"), permission.permissionsForSdk(31))
    }
}
