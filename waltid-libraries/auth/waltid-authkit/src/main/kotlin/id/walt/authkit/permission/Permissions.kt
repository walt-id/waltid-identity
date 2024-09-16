package id.walt.authkit.permission

import id.walt.authkit.utils.times
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.measureTime

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
            -// absolue path: customerAbc.tenant1.issuer1.key1
    - Tenant
        - Tenant


//PermissionedResource

// permission set -> list of permissions
permissionSets = {
    customerAbc.owner = ["customerAbc:+all"]
    customerAbc.total_customerAbc_admin = ["customerAbc.*:+all"]
    customerAbc.iss_adm = ["customerAbc.tenant1.issuer1", "customerAbc.tenant2.issuer"]
    customerAbc.iss_viewer = ["customerAbc.tenant1.issuer1+view", "customerAbc.tenant1.issuer2+view"]
    customerAbc.tenant1.contractors = ["customerAbc.tenant1+manage"]
    customerXyz.superadmin = ["customerXyz.*+all"]
    customerXyz.no_private = ["customerXyz.private1-all", "customerXyz.private2-all"]

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
    val path = id.split(".")

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
data class PermissionSet(
    val id: String,
    val grantPermissions: List<Permission>,

    ) {
    constructor(permissionStrings: List<String>, id: String) : this(
        id,
        permissionStrings.flatMap { Permission.parseFromPermissionString(it) })


    /*
    companion object {
        fun fromPermissionList() {

        }
    }
     */

    override fun toString(): String = "[PermissionSet \"$id\": ${grantPermissions.joinToString()}]"
}


private infix fun String.permissions(permissions: List<String>) = PermissionSet(permissions, this)

data class TestUser(val name: String, val roles: List<PermissionSet>) {
    fun checkAccessTo(resource: String, operation: String): Boolean {
        val accessInsights = canAccessResourceInsights(roles, operation, PermissionedResource(resource))
        val accessInsightStr = accessInsights.map { (k, v) ->
            "YES, due to permission(s) [${v.joinToString()}] of role \"${k.id}\" (${k.grantPermissions})"
        }.let { if (it.isEmpty()) "NO" else it.joinToString() }

        println("\"$operation\" on \"$resource\":\n -> $accessInsightStr")
        return accessInsights.isNotEmpty()
    }

    infix fun Boolean.expect(other: Boolean) = check(this == other)
}

private infix fun String.userWith(roles: List<PermissionSet>) = TestUser(this, roles).also { println("\n== user: $this ==") }


/**
 * A search tree data structure in the form of a compacted Trie (also called Patricia-Trie) for
 * efficient permission lookup.
 * Compacting the permissions is reached by having the full target be the search path, and use
 *  separate tree for the operations.
 */
@Serializable
class PermissionTrie(
    val root: HashMap<String, PermissionTreeNode> = HashMap<String, PermissionTreeNode>(),
) {

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[PermissionTrie]")
        root.forEach { (_, node) ->
            sb.append("\n$node")
        }
        return sb.toString()
    }

    @Serializable
    data class PermissionTreeNode(
        val thisName: String,

        val permissionMethods: ArrayList<String>,
        val children: HashMap<String, PermissionTreeNode>,
    ) {
        constructor(id: String) : this(id, ArrayList(), HashMap())

        fun toString(index: Int): String {
            val sb = StringBuilder()
            sb.append("  " * index + "- $thisName: $permissionMethods")
            if (children.isNotEmpty()) {
                children.forEach {
                    sb.append("\n" + "  " * index + it.value.toString(index + 2))
                }
            }
            return sb.toString()
        }

        override fun toString(): String {
            return toString(0)
        }
    }

    fun getOrMakeRoot(id: String): PermissionTreeNode = root.getOrPut(id) { PermissionTreeNode(id) }

    fun storePermission(target: PermissionedResourceTarget, permission: String) {
        val targetPath = target.path
        val root = getOrMakeRoot(targetPath[0])
        val remainingTraversals = targetPath.drop(1)

        var currentNode = root
        remainingTraversals.forEach {
            currentNode = currentNode.children.getOrPut(it) { PermissionTreeNode(it) }
        }
        currentNode.permissionMethods += permission
    }

    inline fun traverseMatching(
        target: PermissionedResourceTarget,
        permission: String,
        nextTraversal: (String) -> Unit = {},
        onMatch: () -> Unit,
    ): Unit? {
        val targetPath = target.path
        val root = root[targetPath[0]] ?: return null
        val remainingTraversals = targetPath.drop(1)

        var currentNode = root
        remainingTraversals.forEach {
            if (currentNode.permissionMethods.contains(permission)) {
                onMatch.invoke()
            }
            currentNode = currentNode.children[it] ?: currentNode.children["*"] ?: return null
            nextTraversal.invoke(it)
        }
        if (currentNode.permissionMethods.contains(permission)) {
            onMatch.invoke()
        }
        return Unit
    }

    fun hasAny2(target: PermissionedResourceTarget, permission: String): Boolean {
        traverseMatching(target, permission) {
            return true
        }
        return false
    }

    fun findAll2(target: PermissionedResourceTarget, permission: String): List<String>? {
        val matching = ArrayList<String>()

        var currentPath = target.path[0]
        traverseMatching(target, permission, nextTraversal = { currentPath += ".$it" }) {
            matching.add(currentPath)
        }
        return matching
    }

    /**
     * @param target example: organization1.tenant1.resource1
     * @param permission example: use
     */
    fun hasAnyMatching(target: PermissionedResourceTarget, permission: String): Boolean {
        val targetPath = target.path
        val root = root[targetPath[0]] ?: return false
        val remainingTraversals = targetPath.drop(1)

        var currentNode = root
        remainingTraversals.forEach {
            if (currentNode.permissionMethods.contains(permission)) return true
            currentNode = currentNode.children[it] ?: currentNode.children["*"] ?: return false
        }
        return currentNode.permissionMethods.contains(permission)
    }

    fun findAllMatching(target: PermissionedResourceTarget, permission: String): List<String>? {
        val targetPath = target.path
        val root = root[targetPath[0]] ?: return null
        val remainingTraversals = targetPath.drop(1)

        val matching = ArrayList<String>()

        var currentPath = targetPath[0]
        var currentNode = root
        remainingTraversals.forEach {
            if (currentNode.permissionMethods.contains(permission)) matching.add(currentPath)
            currentNode = currentNode.children[it] ?: currentNode.children["*"] ?: return matching
            currentPath += ".$it"
        }
        if (currentNode.permissionMethods.contains(permission)) matching.add(currentPath)
        return matching
    }
}

class PermissionChecker() {

    val allowTrie = PermissionTrie()
    val denyTrie = PermissionTrie()


}

/*




 */

fun main() {


    val trie = PermissionTrie()

    ("alice" userWith listOf(
        "a" permissions listOf(
            "orgA.tenant1.resource1:+issue"
        ),
        "b" permissions listOf(
            "orgB.tenant2.resource2:+issue"
        )
    )).apply {
        this.roles.forEach {
            it.grantPermissions.forEach {
                trie.storePermission(it.target, it.permission)
            }
        }
    }

    trie.findAllMatching(PermissionedResourceTarget(""), "")
    trie.findAll2(PermissionedResourceTarget(""), "")

    val checks1 = (1..10000).map {
        PermissionedResourceTarget("org" + (1..99).random() + "." + "tenant" + (1..99).random() + ".resource" + (1..99).random())
    }
    val checks2 = (1..10000).map {
        PermissionedResourceTarget("org" + (1..99).random() + "." + "tenant" + (1..99).random() + ".resource" + (1..99).random())
    }

    trie.findAllMatching(checks1.first(), "test")
    trie.findAll2(checks1.first(), "test")
    trie.hasAnyMatching(checks1.first(), "test")
    trie.hasAny2(checks1.first(), "test")


    println("X2: ${measureTime { checks2.forEach { trie.hasAny2(it, "abc") } }}")
    println("X1: ${measureTime { checks1.forEach { trie.hasAnyMatching(it, "abc") } }}")

    println("2: ${measureTime { checks2.forEach { trie.hasAny2(it, "abc") } }}")
    println("1: ${measureTime { checks1.forEach { trie.hasAnyMatching(it, "abc") } }}")
    println("2: ${measureTime { checks2.forEach { trie.findAll2(it, "abc") } }}")
    println("1: ${measureTime { checks1.forEach { trie.findAllMatching(it, "abc") } }}")

}
    fun main3() {
        /*
         * Alice is allowed to operate the diploma issuers of the
         * - engineering sciences departments of University1,
         * - colleges of University2
         * - private (non-universitygroup) UniversityX
         */
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

        /*
         * Bob is allowed to:
         * - operate the certificate courses issuer
         */
        ("bob" userWith listOf(
            "universityX.user.bob" permissions listOf(
                "universityX.certificate_courses.investment_banking.certificate_issuer:+issue",
            )
        )).apply {
            checkAccessTo("universitygroup.university1.departement_informatics.diploma_issuer1", "issue") expect false

            checkAccessTo("universityX.certificate_courses.commodities_hedging.certificate_issuer", "issue") expect false
            checkAccessTo("universityX.certificate_courses.investment_banking.certificate_issuer", "issue") expect true
        }

        /*
         * Charlie:
         * -
         */
        ("charlie" userWith listOf(
            "universityX.certificate_issuer" permissions listOf(
                "universityX.certificate_courses.*.certificate_issuer:+issue"
            )
        )).apply {
            checkAccessTo("universityX.certificate_courses.commodities_hedging.certificate_issuer", "issue") expect true
            checkAccessTo("universityX.certificate_courses.investment_banking.certificate_issuer", "issue") expect true
        }


        /*
         Role to give:  [  Guest  ]
         */


        /*
         * Diana:
         * - has full authority over all services belonging to University2
         * - view (read-only) configuration of the Informatics department of University1
         */
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


    fun main2() {
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
                    p.permission == operation || p.permission == "all"
                } else false
            }
        }.filterValues { it.isNotEmpty() }

        return allowCause
    }

    fun canAccessResource(roles: List<PermissionSet>, operation: String, resource: PermissionedResource): Boolean {
        return roles.any {
            it.grantPermissions.any {
                if (it.target.targets(resource)) {
                    it.permission == operation || it.permission == "all"
                } else false
            }
        }
    }

