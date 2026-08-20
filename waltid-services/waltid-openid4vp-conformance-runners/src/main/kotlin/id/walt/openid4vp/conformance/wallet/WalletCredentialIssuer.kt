package id.walt.openid4vp.conformance.wallet

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.cose.CoseCertificate
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletCredentialFixture
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Mints the SD-JWT VC that the wallet under test presents during wallet conformance runs.
 *
 * The credential is issued here rather than fetched from a running issuer so that the run controls
 * both ends of the trust chain: the credential is signed by [TestKeyMaterial.CREDENTIAL_ISSUER_KEY_WITH_X5C],
 * whose leaf chains to [TestKeyMaterial.CREDENTIAL_ISSUER_CA_PEM] - exactly the anchor handed to the
 * suite as `credential.trust_anchor_pem`.
 */
class WalletCredentialIssuer {

    private val issuerJwk: ECKey = ECKey.parse(TestKeyMaterial.CREDENTIAL_ISSUER_KEY_WITH_X5C)

    /**
     * Holder key the credential is bound to via `cnf.jwk`.
     *
     * Generated per run and imported into the wallet, so the wallet can produce the KB-JWT that
     * OpenID4VP requires for cryptographic holder binding.
     */
    val holderKey: ECKey = ECKeyGenerator(Curve.P_256)
        .keyID("conformance-wallet-holder")
        .generate()

    /** Holder key in the walt.id serialized form that `KeyManager.resolveSerializedKey` accepts. */
    fun holderSerializedKey(): JsonObject = buildJsonObject {
        put("type", "jwk")
        put("jwk", Json.parseToJsonElement(holderKey.toJSONString()))
    }

    /**
     * Issue the SD-JWT VC described by [WalletCredentialFixture], bound to [holderKey].
     *
     * Every claim is made selectively disclosable, so the wallet decides what to reveal for a given
     * DCQL query rather than being forced to over-disclose.
     */
    fun issueSdJwtVc(): String {
        val claims = WalletCredentialFixture.SD_JWT_VC_CLAIMS
        val fullPayload = buildJsonObject {
            claims.forEach { (claim, value) -> put(claim, value) }
        }
        val sdPayload = SDPayload.createSDPayload(
            fullPayload = fullPayload,
            disclosureMap = SDMap.generateSDMap(claims.keys),
        )

        return SDJwtVC.sign(
            sdPayload = sdPayload,
            jwtCryptoProvider = SimpleJWTCryptoProvider(
                jwsAlgorithm = JWSAlgorithm.ES256,
                jwsSigner = ECDSASigner(issuerJwk),
                jwsVerifier = null,
            ),
            issuerDid = ISSUER_IDENTIFIER,
            holderKeyJWK = Json.parseToJsonElement(holderKey.toPublicJWK().toJSONString()) as JsonObject,
            issuerKeyId = issuerJwk.keyID,
            vct = WalletCredentialFixture.SD_JWT_VC_VCT,
            // x5c lets the suite chain the credential up to the configured trust anchor.
            additionalJwtHeader = mapOf("x5c" to issuerJwk.x509CertChain.map { it.toString() }),
        ).toString()
    }

    companion object {
        /**
         * `iss` of the issued credential. An https identifier as required for SD-JWT VC; the suite
         * authenticates the issuer through the `x5c` chain rather than by resolving this URL.
         */
        const val ISSUER_IDENTIFIER = "https://credentials.example.com"
    }

    // ---------------------------------------------------------------------------
    // ISO mdoc
    // ---------------------------------------------------------------------------

    /**
     * The same issuer key as a waltid-crypto key.
     *
     * [MdocIssuer] offers a crypto2 overload, but crypto2 has no JWK import and the issuer key has to
     * stay fixed so the trust anchor handed to the suite keeps matching, so the crypto1 overload is
     * the one that fits here.
     */
    private val issuerCrypto1Key: Key by lazy {
        runBlocking { JWKKey.importJWK(TestKeyMaterial.CREDENTIAL_ISSUER_KEY_WITH_X5C).getOrThrow() }
    }

    /** DER of the issuer leaf certificate, taken from the `x5c` of the issuer key. */
    private val issuerLeafDer: ByteArray by lazy {
        val x5c = Json.parseToJsonElement(TestKeyMaterial.CREDENTIAL_ISSUER_KEY_WITH_X5C)
            .jsonObject["x5c"]!!.jsonArray
        x5c.first().jsonPrimitive.content.decodeFromBase64()
    }

    /** [holderKey] as a COSE key, to embed as the mdoc `deviceKeyInfo.deviceKey`. */
    private fun holderCoseKey(): CoseKey = EncodedKey.Jwk(
        data = BinaryData(holderKey.toPublicJWK().toJSONString().encodeToByteArray()),
        privateMaterial = false,
    ).toCoseKey()

    /**
     * Issue the mDL described by [WalletCredentialFixture], bound to [holderKey] as the device key.
     *
     * Uses the typesafe [Mdl] model rather than the schemaless universal path on purpose: ISO 18013-5
     * requires date elements as CBOR full-dates (tag 1004), and the typesafe model carries those tags.
     * A schemaless map would emit plain strings and fail the verifier's digest and claim checks.
     *
     * Returned as base64url CBOR of a bare `IssuerSigned`, which is the OpenID4VCI credential-response
     * shape that `CredentialParser` accepts.
     */
    suspend fun issueMdl(): String {
        val issued = MdocIssuer.issueTypesafe(
            issuerKey = issuerCrypto1Key,
            issuerCertificate = listOf(CoseCertificate(issuerLeafDer)),
            holderKey = holderCoseKey(),
            typesafeData = Mdl(
                familyName = "Mustermann",
                givenName = "Erika",
                birthDate = LocalDate(1971, 9, 1),
                issueDate = LocalDate(2026, 1, 1),
                expiryDate = LocalDate(2036, 1, 1),
                documentNumber = "CONFORMANCE-1",
                drivingPrivileges = listOf(DrivingPrivilege(vehicleCategoryCode = "B")),
            ),
        )
        return coseCompliantCbor.encodeToByteArray(IssuerSigned.serializer(), issued).encodeToBase64Url()
    }
}
