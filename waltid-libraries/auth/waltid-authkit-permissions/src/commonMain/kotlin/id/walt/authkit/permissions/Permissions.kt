package id.walt.authkit.permissions

import id.walt.authkit.permissions.Permission.MinimalPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable


private infix fun String.permissions(permissions: List<String>) =
    FlowPermissionSet.fromPermissionStringsFlow(this, permissions.asFlow())
private infix fun String.permissions(permissions: Flow<String>) =
    FlowPermissionSet.fromPermissionStringsFlow(this, permissions)

data class TestUser(val name: String, val roles: List<FlowPermissionSet>) {
    /*fun checkAccessTo(resource: String, action: String): Boolean {
        val accessInsights = canAccessResourceInsights(roles, action, PermissionedResource(resource))
        val accessInsightStr = accessInsights.map { (k, v) ->
            "YES, due to permission(s) [${v.joinToString()}] of role \"${k.id}\" (${k.grantPermissions})"
        }.let { if (it.isEmpty()) "NO" else it.joinToString() }

        println("\"$action\" on \"$resource\":\n -> $accessInsightStr")
        return accessInsights.isNotEmpty()
    }*/

    infix fun Boolean.expect(other: Boolean) = check(this == other)
}

private infix fun String.userWith(roles: List<FlowPermissionSet>) = TestUser(this, roles).also { println("\n== user: $this ==") }


class PermissionChecker {

    val allowTrie = PermissionTrie()
    val denyTrie = PermissionTrie()

    fun applyPermissions(set: PermissionSet) {
        set.grantPermissions.forEach {
            allowTrie.storePermission(it.target, it.action)
        }
        set.denyPermissions.forEach {
            denyTrie.storePermission(it.target, it.action)
        }
    }

    suspend fun applyPermissions(set: FlowPermissionSet) {
        set.permissions.collect {
            when (it.operation) {
                PermissionOperation.ADD -> {
                    allowTrie.storePermission(it.target, it.action)
                    delay(1000)
                    println("ALLOW-TRIE added $it")
                }
                PermissionOperation.REMOVE -> {
                    denyTrie.storePermission(it.target, it.action)
                    delay(1000)
                    println("DENY-TRIE added $it")
                }
            }
        }
    }

    /**
     * Check if operation is allowed on target, and permission is not denied on target
     * @param target Target to check for operation
     * @param operation Operation to check for target
     */
    fun checkPermission(target: PermissionedResourceTarget, operation: String): Boolean {
        val isAllowed = allowTrie.hasAnyMatching(target, operation)

        if (!isAllowed) {
            return false
        }

        val isDenied = denyTrie.hasAnyMatching(target, operation)

        return !isDenied
    }

    @Serializable
    data class PermissionInsights(
        val allowedBy: List<MinimalPermission>,
        val deniedBy: List<MinimalPermission>,
        val result: Boolean,
    )

    fun checkPermissionInsights(target: PermissionedResourceTarget, operation: String): PermissionInsights {
        val allowedBy = allowTrie.findAllMatching(target, operation)
        val deniedBy = denyTrie.findAllMatching(target, operation)

        val result = allowedBy.isNotEmpty() && deniedBy.isEmpty()

        return PermissionInsights(allowedBy, deniedBy, result)
    }
}

/*




 */

suspend fun main() {

    val c = PermissionChecker()

    ("alice" userWith listOf(
        "a" permissions flowOf(
            "orgA.tenant1:+issue,+config"
        ),
        "c" permissions flowOf(
            "orgA.tenant1.sub1:-all"
        ),
        "b" permissions flowOf(
            "orgB.tenant2.resource2:+issue"
        )
    )).apply {
        this.roles.forEach {
            c.applyPermissions(it)
        }
    }

}

/*
suspend fun main3() {
    */
/*
     * Alice is allowed to operate the diploma issuers of the
     * - engineering sciences departments of University1,
     * - colleges of University2
     * - private (non-universitygroup) UniversityX
     *//*

    ("alice" userWith listOf(
        "universitygroup.university1.engineering_sciences.diploma_manager" permissions listOf(
            "universitygroup.university1.departement_informatics.diploma_issuer1:+issue,+config",
            "universitygroup.university1.departement_informatics.diploma_issuer2:+issue,+config",
            "universitygroup.university1.departement_biosystems.diploma_issuer:+issue,+config",
            "universitygroup.university1.departement_material_sc.diploma_issuer:+issue,+config",
            "universitygroup.university1.departement_electrical_eng.diploma_issuer:+issue,+config",
            "universitygroup.university1.departement_mechanical_eng.diploma_issuer:+issue,+config",
        ),
        "universitygroup.university2.colleges_diploma_manager" permissions listOf(
            "universitygroup.university2.college_humanities.diploma_issuer1:+issue,+config",
            "universitygroup.university2.college_technology.diploma_issuer1:+issue,+config"
        ),
        "universityX.diploma_issuer" permissions listOf(
            "universityX.diploma_issuer1:+issue",
            "universityX.diploma_issuer2:+issue",
            "universityX.diploma_issuer3:+issue",
        )
    )).apply {
        checkAccessTo("universitygroup.university1.departement_informatics.diploma_issuer1", "issue") expect true
        checkAccessTo("universitygroup.university2.college_humanities.diploma_issuer1", "config") expect true
    }

    */
/*
     * Bob is allowed to:
     * - operate the certificate courses issuer
     *//*

    ("bob" userWith listOf(
        "universityX.user.bob" permissions listOf(
            "universityX.certificate_courses.investment_banking.certificate_issuer:+issue",
        )
    )).apply {
        checkAccessTo("universitygroup.university1.departement_informatics.diploma_issuer1", "issue") expect false

        checkAccessTo("universityX.certificate_courses.commodities_hedging.certificate_issuer", "issue") expect false
        checkAccessTo("universityX.certificate_courses.investment_banking.certificate_issuer", "issue") expect true
    }

    */
/*
     * Charlie:
     * -
     *//*

    ("charlie" userWith listOf(
        "universityX.certificate_issuer" permissions listOf(
            "universityX.certificate_courses.*.certificate_issuer:+issue"
        )
    )).apply {
        checkAccessTo("universityX.certificate_courses.commodities_hedging.certificate_issuer", "issue") expect true
        checkAccessTo("universityX.certificate_courses.investment_banking.certificate_issuer", "issue") expect true
    }


    */
/*
     Role to give:  [  Guest  ]
     *//*



    */
/*
     * Diana:
     * - has full authority over all services belonging to University2
     * - view (read-only) configuration of the Informatics department of University1
     *//*

    ("diana" userWith listOf(
        "universitygroup.university2.superadmin" permissions listOf(
            "universitygroup.university2:+all"
        ),
        "universitygroup.!user.diana" permissions listOf(
            "universitygroup:+xyz"
        ),
        "universitygroup.university1.departement_informatics.config_guest" permissions listOf(
            "universitygroup.university1.departement_informatics:+view"
        )
    )).apply {
        checkAccessTo("universitygroup.university1.departement_informatics.diploma_issuer1", "issue") expect false
        checkAccessTo("universitygroup.university1.departement_informatics.diploma_issuer1", "view") expect true


        checkAccessTo("universitygroup.university2.tenant${Random.nextInt()}", "delete") expect true
    }
}
*/


/*
suspend fun main2() {
    val globalRoles = listOf(
        "customerAbc.owner" permissions listOf("customerAbc:+all,-download"),
        "customerXyz.owner" permissions listOf("customerXyz:+all"),
        "customerXyz.tenant1.administrator" permissions listOf("customerXyz.tenant1:+all"),
        "customerAbc.tenant1.issuer2.key_manager" permissions listOf("customerAbc.tenant1.issuer2.keys:+all"),
        "customerAbc.tenant2.contractor" permissions listOf("customerAbc.tenant2:+all", "customerAbc.tenant2.*.keys:-download")
    )

    globalRoles.forEach {
        println(it)
    }

    val resources = listOf(
        "customerAbc",
        "customerXyz",
        "customerAbc.tenant1",
        "customerAbc.tenant1.issuer1",
        "customerAbc.tenant1.issuer2",
        "customerAbc.tenant2.issuer2.keys.key1",
        "customerAbc.tenant2.issuer2.keys.key2",
        "customerAbc.tenant2.issuer2.user.key1",
        "customerAbc.tenant2.issuer2.keys.subkeys.key1",
        "customerXyz.tenant2"
    ).map { PermissionedResource(it) }


    infix fun String.testUserOf(roles: List<String>): TestUser {
        val effectiveRoles = roles.map { roleName -> globalRoles.first { it.id == roleName } }
        return TestUser(this, effectiveRoles)
    }

    val alice = "alice" testUserOf listOf(
        "customerAbc.owner"
    )
    val bob = "bob" testUserOf listOf(
        "customerXyz.tenant1.administrator",
        "customerAbc.tenant2.contractor"
    )
    val charles = "charles" testUserOf listOf("customerAbc.tenant1.issuer2.key_manager")

    val users = listOf(alice, bob, charles)

    users.forEach { user ->
        println("== User ${user.name} ==")
        user.roles.forEach { role -> println("- $role") }

        resources.forEach { resource ->
            val accessInsights = canAccessResourceInsights(user.roles, "view", resource)
            val accessInsightStr = accessInsights.map { (k, v) ->
                "YES, due to ${v.joinToString()} of ${k.id} (${k.grantPermissions})"
            }.let { if (it.isEmpty()) "NO" else it.joinToString() }

            println("${user.name} on ${resource.id}: $accessInsightStr")
        }
    }

}

fun canAccessResourceInsights(
    roles: List<PermissionSet>,
    operation: String,
    resource: PermissionedResource,
): Map<PermissionSet, List<Permission>> {
    val allowCause = roles.associateWith { role ->
        role.grantPermissions.filter { p ->
            if (p.target.targets(resource)
            //.also { println("Does ${p.target} target ${resource.id}? $it") }
            ) {
                p.action == operation || p.action == "all"
            } else false
        }
    }.filterValues { it.isNotEmpty() }

    return allowCause
}

fun canAccessResource(roles: List<PermissionSet>, operation: String, resource: PermissionedResource): Boolean {
    return roles.any {
        it.grantPermissions.any {
            if (it.target.targets(resource)) {
                it.action == operation || it.action == "all"
            } else false
        }
    }
}

*/
