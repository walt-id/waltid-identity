package id.walt.w3c.issuance

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.PublicKeyIds.publicKeyId
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.did.dids.DidUtils
import id.walt.w3c.utils.W3CVcUtils.overwrite
import id.walt.w3c.utils.W3CVcUtils.update
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object Issuer {

    /**
     * Manually set data and issue credential
     */
    @Deprecated("Use the crypto2 overload accepting a Key and JwsAlgorithm")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.baseIssue(
        key: Key,
        issuerId: String,
        subject: String,

        dataOverwrites: Map<String, JsonElement>,
        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeaders: Map<String, JsonElement>,
        additionalJwtOptions: Map<String, JsonElement>,
    ): String {
        val overwritten = overwrite(dataOverwrites)
        var updated = overwritten
        dataUpdates.forEach { (k, v) -> updated = updated.update(k, v) }

        return signJws(
            issuerKey = key,
            issuerId = issuerId,
            subjectDid = subject,
            additionalJwtHeader = additionalJwtHeaders,
            additionalJwtOptions = additionalJwtOptions
        )
    }

    @JsExport.Ignore
    suspend fun W3CVC.baseIssue(
        key: Crypto2Key,
        algorithm: JwsAlgorithm,
        issuerId: String,
        subject: String,
        dataOverwrites: Map<String, JsonElement>,
        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeaders: Map<String, JsonElement>,
        additionalJwtOptions: Map<String, JsonElement>,
    ): String {
        var updated = overwrite(dataOverwrites)
        dataUpdates.forEach { (name, values) -> updated = updated.update(name, values) }
        return updated.signJws(
            issuerKey = key,
            algorithm = algorithm,
            issuerId = issuerId,
            subjectDid = subject,
            additionalJwtHeader = additionalJwtHeaders,
            additionalJwtOptions = additionalJwtOptions,
        )
    }

    @Deprecated("Use getKidHeader with a crypto2 Key")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getKidHeader(issuerKey: Key, issuerDid: String? = null): String {
        return qualifyDidKeyId(issuerKey.publicKeyId(), issuerDid)
    }

    @JsExport.Ignore
    @JvmBlocking
    @JvmAsync
    @JsPromise
    suspend fun getKidHeader(issuerKey: Crypto2Key, issuerDid: String? = null): String {
        val rawId = issuerKey.id.value
        val keyId = when {
            DidUtils.isDidUrl(rawId) -> rawId
            issuerDid != null && DidUtils.isDidUrl(issuerDid) &&
                !issuerDid.startsWith("did:key:") &&
                !issuerDid.startsWith("did:jwk:") ->
                publicJwkThumbprint(issuerKey)
            else -> rawId
        }
        return qualifyDidKeyId(keyId, issuerDid)
    }

    private suspend fun publicJwkThumbprint(issuerKey: Crypto2Key): String {
        val exported = requireNotNull(issuerKey.capabilities.publicKeyExporter) {
            "Issuer signing key cannot export a public JWK for kid"
        }.exportPublicKey()
        return Jwk.sha256Thumbprint(exported.toPublicJwk(issuerKey.spec))
    }

    private fun qualifyDidKeyId(keyId: String, issuerDid: String?): String = when {
        issuerDid.isNullOrEmpty() -> keyId
        issuerDid.startsWith("did:jwk:") -> "$issuerDid#0"
        DidUtils.isDidUrl(keyId) -> keyId
        keyId.startsWith("#") -> issuerDid + keyId
        issuerDid.startsWith("did:key:") -> "$issuerDid#${issuerDid.removePrefix("did:key:")}"
        else -> "$issuerDid#$keyId"
    }
}
