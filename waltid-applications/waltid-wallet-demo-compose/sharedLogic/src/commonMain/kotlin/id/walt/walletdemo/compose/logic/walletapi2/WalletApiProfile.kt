package id.walt.walletdemo.compose.logic.walletapi2

import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class WalletApiKind {
    OpenSource,
    Enterprise,
    ;

    val canRegister: Boolean get() = this == OpenSource
    val canManageWallet: Boolean get() = this == OpenSource
    val canGenerateIdentity: Boolean get() = this == OpenSource
    val canDeleteCredential: Boolean get() = this == OpenSource

    val loginPath: String
        get() = when (this) {
            OpenSource -> "/auth/emailpass"
            Enterprise -> "/auth/account/emailpass"
        }

    val logoutPath: String
        get() = when (this) {
            OpenSource -> "/auth/logout"
            Enterprise -> "/auth/account/logout"
        }
}

fun parseWalletApiKind(value: String?): WalletApiKind {
    return when (value?.trim()?.lowercase()) {
        "enterprise", "es" -> WalletApiKind.Enterprise
        else -> WalletApiKind.OpenSource
    }
}

internal const val EnterpriseResourceTreePath = "/v1/resources-api/tree"

internal fun walletApiOperationPath(kind: WalletApiKind, walletId: String, operation: String = ""): String {
    val suffix = operation.trim('/')
    val tail = if (suffix.isEmpty()) "" else "/$suffix"
    val id = walletId.encodeURLPathPart()
    return when (kind) {
        WalletApiKind.OpenSource -> "/wallet/$id$tail"
        WalletApiKind.Enterprise -> "/v2/$id/wallet-service-api$tail"
    }
}

internal fun adaptWalletRequestBody(kind: WalletApiKind, body: JsonObject): JsonObject {
    if (kind != WalletApiKind.Enterprise) return body
    return adaptEnterpriseValue(body) as JsonObject
}

private fun adaptEnterpriseValue(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> JsonObject(
        buildMap {
            value.forEach { (key, child) ->
                val adapted = adaptEnterpriseValue(child)
                if (key == "keyId" && child !is JsonNull) put("keyReference", adapted) else put(key, adapted)
            }
        },
    )
    is JsonArray -> JsonArray(value.map(::adaptEnterpriseValue))
    else -> value
}

internal fun wallet2TargetsFromResourceTree(tree: JsonObject): List<String> {
    val found = mutableListOf<String>()
    fun walk(node: JsonObject) {
        for ((key, value) in node) {
            val child = value as? JsonObject ?: continue
            if (child.stringField("type") == "wallet2") found += key
            (child["entries"] as? JsonObject)?.let(::walk)
        }
    }
    walk(tree)
    return found
}

internal fun selectWalletId(available: List<String>, remembered: String?): String? =
    remembered?.takeIf { it in available } ?: available.firstOrNull()

/** The wallet that started an authorization-code issuance, when it is still available. */
internal fun sessionMatchesDeployedProfile(savedProfile: String?, deployed: WalletApiKind): Boolean {
    if (savedProfile.isNullOrBlank()) return deployed == WalletApiKind.OpenSource
    return parseWalletApiKind(savedProfile) == deployed
}

internal fun walletForAuthorizationCallback(
    currentWalletId: String,
    availableWalletIds: List<String>,
    pendingWalletId: String?,
): String? {
    if (pendingWalletId.isNullOrBlank() || pendingWalletId == currentWalletId) return currentWalletId
    return pendingWalletId.takeIf { it in availableWalletIds }
}

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull
