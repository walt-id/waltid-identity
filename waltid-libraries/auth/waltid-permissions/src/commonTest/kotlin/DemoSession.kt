import id.walt.permissions.permissions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DemoSession {

    @Test
    fun demo() = runTest {
        // "userWith", "permissions", etc. is just syntax sugar for this demo

        // waltid.tenant1.subtenant1.resource1
        ("alice" userWith listOf(
            "waltid.superadmin" permissions flowOf(
                "waltid.tenant1:+all",
                "waltid.tenant2:+all",
            ),
            "waltid.hr_department.assistant" permissions flowOf(
                "waltid.hr_department:+create_badge,+revoke_badge",
                "waltid.hr_department.employees:+list",
                "waltid.hr_department.contractors:+list",
                "waltid.hr_department.special:-create_badge,-revoke_badge",
            ),
            "waltid.customer_solutions.support" permissions flowOf(
                "waltid.customer_solutions:+view",
                "waltid.customer_solutions.*.*.issuer:+config",
            )
        )) {
            checkPermissionInsights("waltid.hwr_department.employees.max_mustermann", "create_badge").print()
        }
    }
}
