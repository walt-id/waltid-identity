package id.walt.wallet2.issuance

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.mdoc.verification.verifyIssuerAuthentication
import id.walt.mdoc.verification.verifyIssuerSignedItemDigests
import id.walt.sdjwt.SDJwt
import id.walt.wallet2.data.StoredCredential
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock

/** Independently provisioned P-256 verification key for one OpenID4VCI issuer and credential format. */
@Serializable
sealed class CredentialReceiptAuthority {
    abstract val credentialIssuer: String
    abstract val publicJwk: String

    /** ES256 SD-JWT VC issuer key. [signedIssuer] is the required signed iss claim. */
    @Serializable
    @SerialName("sd-jwt-vc")
    data class SdJwtVc(
        override val credentialIssuer: String,
        override val publicJwk: String,
        val signedIssuer: String = credentialIssuer,
    ) : CredentialReceiptAuthority()

    /** ES256 mdoc document signer key provisioned independently of the credential's certificate chain. */
    @Serializable
    @SerialName("mdoc")
    data class Mdoc(
        override val credentialIssuer: String,
        override val publicJwk: String,
    ) : CredentialReceiptAuthority()
}

/** The configured receipt profile could not authenticate an issued credential. */
class CredentialReceiptTrustException internal constructor() :
    IllegalArgumentException("Credential issuer trust could not be established")

/** Host-provisioned receipt profile. Each configured key applies to one protocol issuer and format. */
class CredentialReceiptTrust(authorities: List<CredentialReceiptAuthority>) {
    /** Rechecks the original issuer-signed credential before publishing an existing record to the OS. */
    suspend fun permitsRegistration(stored: StoredCredential): Boolean = try {
        verify(stored.receiptOrigin(), stored.credential)
        true
    } catch (_: CredentialReceiptTrustException) {
        false
    }

    internal suspend fun verify(origin: String?, credential: DigitalCredential) {
        try {
            val candidates = pins.filter { it.authority.credentialIssuer == origin }
            require(candidates.isNotEmpty())
            when (credential) {
                is SdJwtCredential -> verifySdJwt(credential, candidates)
                is MdocsCredential -> verifyMdoc(credential, candidates)
                else -> throw CredentialReceiptTrustException()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw CredentialReceiptTrustException()
        }
    }

    private data class Pin(val authority: CredentialReceiptAuthority, val key: EncodedKey.Jwk)

    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val pins = authorities.map { authority ->
        require(authority.credentialIssuer.isNotBlank()) { "Configure the credential issuer identifier" }
        if (authority is CredentialReceiptAuthority.SdJwtVc) require(authority.signedIssuer.isNotBlank())
        val jwk = Json.parseToJsonElement(authority.publicJwk).jsonObject
        require(
            jwk["kty"] == JsonPrimitive("EC") && jwk["crv"] == JsonPrimitive("P-256") && "d" !in jwk &&
                (jwk["alg"] == null || jwk["alg"] == JsonPrimitive("ES256")) &&
                listOf("x", "y").all { (jwk[it] as? JsonPrimitive)?.let { v -> v.isString && v.content.isNotBlank() } == true }
        ) { "Receipt authorities require an ES256 public verification key" }
        Pin(authority, EncodedKey.Jwk(BinaryData(authority.publicJwk.encodeToByteArray()), privateMaterial = false))
    }

    private suspend fun verifySdJwt(credential: SdJwtCredential, candidates: List<Pin>) {
        val wire = requireNotNull(credential.signedWithDisclosures)
        val jwt = wire.substringBefore('~')
        val decoded = CompactJws.decodeUnverified(jwt)
        require(decoded.protectedHeader["alg"] == JsonPrimitive("ES256"))
        require(decoded.protectedHeader["typ"] in setOf(JsonPrimitive("dc+sd-jwt"), JsonPrimitive("vc+sd-jwt")))
        val claims = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject
        val issuer = claims["iss"]?.jsonPrimitive?.takeIf { it.isString }?.content
        require(candidates.any { pin ->
            val authority = pin.authority
            authority is CredentialReceiptAuthority.SdJwtVc && authority.signedIssuer == issuer && signatureMatches(jwt, pin.key)
        })
        require(SDJwt.parse(wire).sdPayload.verifyDisclosures())
        require(claims["vct"]?.jsonPrimitive?.let { it.isString && it.content.isNotBlank() } == true)
        val now = Clock.System.now().epochSeconds
        for (name in listOf("nbf", "exp")) {
            val value = claims[name]?.jsonPrimitive ?: continue
            val time = requireNotNull(value.takeUnless { it.isString }?.doubleOrNull)
            require(time.isFinite() && if (name == "exp") now < time else now >= time)
        }
        require(CredentialParser.detectAndParse(wire).second.credentialData == credential.credentialData)
    }

    private suspend fun signatureMatches(jwt: String, pin: EncodedKey.Jwk): Boolean = try {
        val key = runtime.restore(pin.toStoredSoftwareKey(KeyId("receipt"), setOf(KeyUsage.VERIFY)))
        CompactJws.verify(jwt, key, JwsAlgorithm.ES256)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun verifyMdoc(credential: MdocsCredential, candidates: List<Pin>) {
        val canonical = CredentialParser.detectAndParse(requireNotNull(credential.signed)).second as MdocsCredential
        require(canonical.credentialData == credential.credentialData)
        val now = Clock.System.now()
        val result = verifyIssuerAuthentication(canonical.document, verificationTime = now)
        require(result.coseAlgorithm == -7)
        val publicKey = requireNotNull(result.signerKey.capabilities.publicKeyExporter).exportPublicKey()
        val actual = Jwk.sha256Thumbprint(publicKey.toPublicJwk(result.signerKey.spec))
        require(candidates.any { it.authority is CredentialReceiptAuthority.Mdoc && Jwk.sha256Thumbprint(it.key) == actual })
        val mso = requireNotNull(canonical.documentMso)
        require(now >= mso.validityInfo.validFrom && now <= mso.validityInfo.validUntil && mso.validityInfo.signed <= now)
        verifyIssuerSignedItemDigests(canonical.document, mso)
    }
}

private const val RECEIPT_ORIGIN = "walt.id:credential-receipt-issuer"

internal fun StoredCredential.receiptOrigin(): String? = (metadata?.get(RECEIPT_ORIGIN) as? JsonPrimitive)?.contentOrNull

internal fun receiptMetadata(metadata: JsonObject?, origin: String?): JsonObject? {
    if (origin == null && metadata?.containsKey(RECEIPT_ORIGIN) != true) return metadata
    val fields = metadata.orEmpty() - RECEIPT_ORIGIN
    return JsonObject(if (origin == null) fields else fields + (RECEIPT_ORIGIN to JsonPrimitive(origin)))
}
