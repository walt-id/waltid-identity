package id.walt.authkit.permission

import kotlinx.serialization.Serializable
import kotlin.random.Random

/*

= Accounts
    - gewisse Roles
    - gewisse Permissions

= Organization
    / Roles
        - certain Permissions
    - Tenant
        - Service
            - Service resource
            -// absolue path: NEOM.tenant1.issuer1.key1
    - Tenant
        - Tenant


//PermissionedResource

// permission set -> list of permissions
permissionSets = {
    NEOM.owner = ["NEOM:+all"]
    NEOM.total_neom_admin = ["NEOM.*:+all"]
    NEOM.iss_adm = ["NEOM.tenant1.issuer1", "NEOM.tenant2.issuer"]
    NEOM.iss_viewer = ["NEOM.tenant1.issuer1+view", "NEOM.tenant1.issuer2+view"]
    NEOM.tenant1.contractors = ["NEOM.tenant1+manage"]
    CHQED.superadmin = ["cheqd.*+all"]
    CHEQD.no_private = ["cheqd.private1-all", "cheqd.private2-all"]

}

val account = {
    Alice: {
        permission_sets =
    }
}

 */

data class PermissionedResource(val id: String) {
    internal val path = id.split(".")

    override fun toString(): String {
        return path.joinToString(".")
    }
}

/*
 handle .* and stuff
 */
@Serializable
data class PermissionedResourceTarget(val id: String) {
    private val path = id.split(".")

    override fun toString(): String {
        return path.joinToString(".")
    }

    fun targets(resource: PermissionedResource): Boolean {
        if (id == resource.id) return true

        if (resource.path.size >= path.size) {
            path.forEachIndexed { i, s ->
                if (resource.path[i] != s && s != "*") return false
            }

            return true
        } else return false
    }
}

@Serializable
enum class PermissionOperation(val symbol: Char) {
    ADD('+'),
    REMOVE('-');

    companion object {
        private val operationMapping = enumValues<PermissionOperation>().associateBy { it.symbol }

        fun from(char: Char) = operationMapping[char] ?: error("Invalid operation: $char, allowed operations: ${entries.map { it.symbol }}")
    }
}

@Serializable
data class Permission(val target: PermissionedResourceTarget, val permission: String, val operation: PermissionOperation) {


    override fun toString(): String {
        return "$target${operation.symbol}$permission"
    }

    companion object {
        fun parseFromPermissionString(string: String): List<Permission> {
            check(':' in string) { "No ':' in string: $string" }

            val (targetString, permissionStrings) = string.split(':').toMutableList()
            val target = PermissionedResourceTarget(targetString)

            val permissions = permissionStrings.split(",").map { permissionString ->
                Permission(target, permissionString.drop(1), PermissionOperation.from(permissionString[0]))
            }

            return permissions
        }
    }
}


@Serializable
data class PermissionSet(val id: String, val permissions: List<Permission>) {
    constructor(permissionStrings: List<String>, id: String) : this(
        id,
        permissionStrings.flatMap { Permission.parseFromPermissionString(it) })

    override fun toString(): String = "[PermissionSet \"$id\": ${permissions.joinToString()}]"
}


private infix fun String.permissions(permissions: List<String>) = PermissionSet(permissions, this)

data class TestUser(val name: String, val roles: List<PermissionSet>) {
    fun checkAccessTo(resource: String, operation: String): Boolean {
        val accessInsights = canAccessResourceInsights(roles, operation, PermissionedResource(resource))
        val accessInsightStr = accessInsights.map { (k, v) ->
            "YES, due to permission(s) [${v.joinToString()}] of role \"${k.id}\" (${k.permissions})"
        }.let { if (it.isEmpty()) "NO" else it.joinToString() }

        println("\"$operation\" on \"$resource\":\n -> $accessInsightStr")
        return accessInsights.isNotEmpty()
    }

    infix fun Boolean.expect(other: Boolean) = check(this == other)
}

private infix fun String.userWith(roles: List<PermissionSet>) = TestUser(this, roles).also { println("\n== user: $this ==") }


fun main() {
    /*
     * Alice is allowed to operate the diploma issuers of the
     * - engineering sciences departments of ETH Zürich,
     * - colleges of ETH Lausanne
     * - private (non-SWITCH) SBS University
     */
    ("alice" userWith listOf(
        "switch.eth_zurich.engineering_sciences.diploma_manager" permissions listOf(
            "switch.eth_zurich.departement_informatics.diploma_issuer1:+issue,+config",
            "switch.eth_zurich.departement_informatics.diploma_issuer2:+issue,+config",
            "switch.eth_zurich.departement_biosystems.diploma_issuer:+issue,+config",
            "switch.eth_zurich.departement_material_sc.diploma_issuer:+issue,+config",
            "switch.eth_zurich.departement_electrical_eng.diploma_issuer:+issue,+config",
            "switch.eth_zurich.departement_mechanical_eng.diploma_issuer:+issue,+config",
        ),
        "switch.eth_lausanne.colleges_diploma_manager" permissions listOf(
            "switch.eth_lausanne.college_humanities.diploma_issuer1:+issue,+config",
            "switch.eth_lausanne.college_technology.diploma_issuer1:+issue,+config"
        ),
        "sbs.swiss_business_school.diploma_issuer" permissions listOf(
            "sbs.swiss_business_school.diploma_issuer1:+issue",
            "sbs.swiss_business_school.diploma_issuer2:+issue",
            "sbs.swiss_business_school.diploma_issuer3:+issue",
        )
    )).apply {
        checkAccessTo("switch.eth_zurich.departement_informatics.diploma_issuer1", "issue") expect true
        checkAccessTo("switch.eth_lausanne.college_humanities.diploma_issuer1", "config") expect true
    }

    /*
     * Bob is allowed to:
     * - operate the certificate courses issuer
     */
    ("bob" userWith listOf(
        "sbs.user.bob" permissions listOf(
            "sbs.swiss_business_school.certificate_courses.investment_banking.certificate_issuer:+issue",
        )
    )).apply {
        checkAccessTo("switch.eth_zurich.departement_informatics.diploma_issuer1", "issue") expect false

        checkAccessTo("sbs.swiss_business_school.certificate_courses.commodities_hedging.certificate_issuer", "issue") expect false
        checkAccessTo("sbs.swiss_business_school.certificate_courses.investment_banking.certificate_issuer", "issue") expect true
    }

    /*
     * Charlie:
     * -
     */
    ("charlie" userWith listOf(
        "sbs.certificate_issuer" permissions listOf(
            "sbs.swiss_business_school.certificate_courses.*.certificate_issuer:+issue"
        )
    )).apply {
        checkAccessTo("sbs.swiss_business_school.certificate_courses.commodities_hedging.certificate_issuer", "issue") expect true
        checkAccessTo("sbs.swiss_business_school.certificate_courses.investment_banking.certificate_issuer", "issue") expect true
    }

    /*
     * Diana:
     * - has full authority over all services belonging to ETH Lausanne
     * - view (read-only) configuration of the Informatics department of ETH Zürich
     */
    ("diana" userWith listOf(
        "switch.eth_lausanne.superadmin" permissions listOf(
            "switch.eth_lausanne:+all"
        ),
        "switch.eth_zurich.departement_informatics.config_guest" permissions listOf(
            "switch.eth_zurich.departement_informatics:+view"
        )
    )).apply {
        checkAccessTo("switch.eth_zurich.departement_informatics.diploma_issuer1", "issue") expect false
        checkAccessTo("switch.eth_zurich.departement_informatics.diploma_issuer1", "view") expect true


        checkAccessTo("switch.eth_lausanne.tenant${Random.nextInt()}", "delete") expect true
    }
}


fun main2() {
    val globalRoles = listOf(
        "NEOM.owner" permissions listOf("NEOM:+all,-download"),
        "cheqd.owner" permissions listOf("cheqd:+all"),
        "cheqd.tenant1.administrator" permissions listOf("cheqd.tenant1:+all"),
        "NEOM.tenant1.issuer2.key_manager" permissions listOf("NEOM.tenant1.issuer2.keys:+all"),
        "NEOM.tenant2.contractor" permissions listOf("NEOM.tenant2:+all", "NEOM.tenant2.*.keys:-download")
    )

    globalRoles.forEach {
        println(it)
    }

    val resources = listOf(
        "NEOM",
        "cheqd",
        "NEOM.tenant1",
        "NEOM.tenant1.issuer1",
        "NEOM.tenant1.issuer2",
        "NEOM.tenant2.issuer2.keys.key1",
        "NEOM.tenant2.issuer2.keys.key2",
        "NEOM.tenant2.issuer2.user.key1",
        "NEOM.tenant2.issuer2.keys.subkeys.key1",
        "cheqd.tenant2"
    ).map { PermissionedResource(it) }


    infix fun String.testUserOf(roles: List<String>): TestUser {
        val effectiveRoles = roles.map { roleName -> globalRoles.first { it.id == roleName } }
        return TestUser(this, effectiveRoles)
    }

    val alice = "alice" testUserOf listOf(
        "NEOM.owner"
    )
    val bob = "bob" testUserOf listOf(
        "cheqd.tenant1.administrator",
        "NEOM.tenant2.contractor"
    )
    val charles = "charles" testUserOf listOf("NEOM.tenant1.issuer2.key_manager")

    val users = listOf(alice, bob, charles)

    users.forEach { user ->
        println("== User ${user.name} ==")
        user.roles.forEach { role -> println("- $role") }

        resources.forEach { resource ->
            val accessInsights = canAccessResourceInsights(user.roles, "view", resource)
            val accessInsightStr = accessInsights.map { (k, v) ->
                "YES, due to ${v.joinToString()} of ${k.id} (${k.permissions})"
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
    val accessCause = roles.associateWith { role ->
        role.permissions.filter { p ->
            if (p.target.targets(resource)
            //.also { println("Does ${p.target} target ${resource.id}? $it") }
            ) {
                p.permission == operation || p.permission == "all"
            } else false
        }
    }.filterValues { it.isNotEmpty() }

    return accessCause
}

fun canAccessResource(roles: List<PermissionSet>, operation: String, resource: PermissionedResource): Boolean {
    return roles.any {
        it.permissions.any {
            if (it.target.targets(resource)) {
                it.permission == operation || it.permission == "all"
            } else false
        }
    }
}

