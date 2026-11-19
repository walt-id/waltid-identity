import id.walt.gradle.licensee.LicenseePolicies
import id.walt.gradle.licensee.LicenseePolicy
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LicenseePoliciesTest {
    @Test
    fun `only distributed binary and SaaS policies admit LGPL and all reject plain GPL and AGPL`() {
        for (policy in LicenseePolicy.entries) {
            val licenses = LicenseePolicies.spdxIdsFor(policy)
            assertContains(licenses, "Apache-2.0", policy.name)
            assertContains(licenses, "GPL-2.0-with-classpath-exception", policy.name)
            assertFalse("GPL-2.0-only" in licenses, policy.name)
            assertFalse("GPL-3.0-only" in licenses, policy.name)
            assertFalse("AGPL-3.0-only" in licenses, policy.name)
            if (policy == LicenseePolicy.Apache) {
                assertFalse("LGPL-3.0-only" in licenses)
            } else {
                assertContains(licenses, "LGPL-3.0-only")
            }
        }
    }
}
