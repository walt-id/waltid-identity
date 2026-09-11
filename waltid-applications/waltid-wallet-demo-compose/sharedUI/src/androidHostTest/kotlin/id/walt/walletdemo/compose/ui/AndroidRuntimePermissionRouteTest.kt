package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidRuntimePermissionRouteTest {
    private val permissions = listOf("scan", "connect", "advertise")

    @Test
    fun `granted permissions require no system surface`() {
        assertEquals(
            AndroidRuntimePermissionRoute.Granted,
            route(granted = permissions.toSet()),
        )
    }

    @Test
    fun `permissions without a recorded decision use the runtime prompt`() {
        assertEquals(
            AndroidRuntimePermissionRoute.Request,
            route(),
        )
    }

    @Test
    fun `re-requestable denial uses the runtime prompt`() {
        assertEquals(
            AndroidRuntimePermissionRoute.Request,
            route(requested = permissions.toSet(), rationale = permissions.toSet()),
        )
    }

    @Test
    fun `user fixed denial opens application settings`() {
        assertEquals(
            AndroidRuntimePermissionRoute.OpenSettings,
            route(requested = permissions.toSet()),
        )
    }

    @Test
    fun `one user fixed permission sends the grouped request to settings`() {
        assertEquals(
            AndroidRuntimePermissionRoute.OpenSettings,
            route(
                granted = setOf("scan"),
                requested = permissions.toSet(),
                rationale = setOf("connect"),
            ),
        )
    }

    private fun route(
        granted: Set<String> = emptySet(),
        requested: Set<String> = emptySet(),
        rationale: Set<String> = emptySet(),
    ): AndroidRuntimePermissionRoute = androidRuntimePermissionRoute(
        permissions = permissions,
        isGranted = { it in granted },
        wasRequested = { it in requested },
        shouldShowRationale = { it in rationale },
    )
}
