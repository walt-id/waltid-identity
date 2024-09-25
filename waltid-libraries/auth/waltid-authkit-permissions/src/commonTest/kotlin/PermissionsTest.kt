import id.walt.authkit.permissions.FlowPermissionSet
import id.walt.authkit.permissions.Permission
import id.walt.authkit.permissions.PermissionChecker
import id.walt.authkit.permissions.permissions
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PermissionsTest {
    infix fun Boolean.expect(other: Boolean) {
        println("Check: $this == $other")
        check(this == other)
    }
    private fun List<Permission.MinimalPermission>.printable() = if (isNotEmpty()) joinToString() else "None"
    infix fun PermissionChecker.PermissionInsights.expect(result: Boolean) {
        println("$target - $operation: ${this.result} == $result ${if (this.result == result) "✅" else ""} | allows: ${allowedBy.printable()} / denies: ${deniedBy.printable()}")
        check(this.result == result)
    }

    data class TestUser(val name: String, val roles: List<FlowPermissionSet>)

    private suspend infix fun String.userWith(roles: List<FlowPermissionSet>) = TestUser(this, roles)
        .also {
            println("\n== user: $this ==")
            roles.forEachIndexed { roleIndex, flowPermissionSet ->
                println("Role ${roleIndex+1}: ${flowPermissionSet.id}")
                flowPermissionSet.permissions.collectIndexed { permissionIndex, permission ->
                    println("- ${roleIndex+1}.${permissionIndex+1}.: $permission")
                }
            }
        }

    @Test
    fun test1() = runTest {
        val c = PermissionChecker()

        ("alice" userWith listOf(
            "orgA.a" permissions flowOf(
                "orgA.tenant1:+issue,+config",
            ),
            "orgA.tenant1.c" permissions flowOf(
                "orgA.tenant1.sub1:-all"
            ),
            "orgB.superadmin" permissions flowOf(
                "orgB:+all",
            ),
            "orgX.abc" permissions flowOf(
                "orgX.*.issuer:+issue"
            )
        )).apply {
            c.run {
                roles.forEach { applyPermissions(it) }

                println()
                println("Checking permissions:")
                checkPermission("orgA.tenant1.abc", "issue") expect true
                checkPermissionInsights("orgA.tenant1.abc", "issue") expect true

                checkPermissionInsights("orgA.tenant1.sub1.abc", "issue") expect false
                checkPermissionInsights("orgA.tenant1.xyz", "config") expect true
                checkPermissionInsights("orgA.tenant1.xyz", "something-else") expect false
                checkPermissionInsights("orgB.somewhere.someresource", "something") expect true
                checkPermissionInsights("orgX.tenant123456.issuer", "issue") expect true

                println()
            }
        }
    }
}
