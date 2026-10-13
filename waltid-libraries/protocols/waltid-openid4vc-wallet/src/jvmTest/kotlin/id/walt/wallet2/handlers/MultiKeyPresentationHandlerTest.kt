package id.walt.wallet2.handlers

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.resolveKeyMaterial
import id.walt.wallet2.data.withVerifiedIssuanceHolderKeyBinding
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.test.*

/** A Wallet2 presentation of two credentials that were issued to different holder keys of the wallet. */
class MultiKeyPresentationHandlerTest {

    private suspend fun signingKey(id: String) = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
    )

    private suspend fun publicJwk(key: Key): JsonObject =
        Json.parseToJsonElement(key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()).jsonObject

    private suspend fun sdJwt(vct: String, holder: Key): SdJwtCredential {
        val disclosure = SdJwtSelectiveDisclosure("salt-$vct", "given_name", JsonPrimitive("Alice"))
        val claims = buildJsonObject {
            put("iss", "https://issuer.example"); put("vct", vct); put("_sd_alg", "sha-256")
            put("cnf", buildJsonObject { put("jwk", publicJwk(holder)) })
            put("_sd", buildJsonArray {
                add(MessageDigest.getInstance("SHA-256").digest(disclosure.encoded.encodeToByteArray()).encodeToBase64Url())
            })
        }
        val header = """{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray().encodeToBase64Url()
        return SdJwtCredential(
            dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
            disclosables = mapOf("$._sd" to setOf(disclosure.encoded)), disclosures = listOf(disclosure),
            credentialData = claims, originalCredentialData = claims, signature = null,
            signed = "$header.${claims.toString().encodeToByteArray().encodeToBase64Url()}.c2ln",
        )
    }

    @Test
    fun `stored credentials are presented with the keys they were issued to`() = runTest {
        val pidKey = signingKey("pid-key")
        val identityKey = signingKey("identity-key")
        val keyStore = InMemoryKeyStore().apply { addCrypto2Key(pidKey); addCrypto2Key(identityKey) }
        val credentialStore = InMemoryCredentialStore()
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore), credentialStores = listOf(credentialStore))
        for ((id, vct, key) in listOf(Triple("pid", "urn:pid", pidKey), Triple("identity", "urn:identity", identityKey))) {
            val issued = StoredCredential(id, sdJwt(vct, key))
            val material = assertNotNull(wallet.resolveKeyMaterial(key.id.value, setOf(KeyUsage.SIGN)))
            credentialStore.addCredential(wallet.withVerifiedIssuanceHolderKeyBinding(issued, material))
        }

        val queries = listOf(
            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("urn:pid"))),
            CredentialQuery("identity", CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("urn:identity"))),
        )
        val request = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback", redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT, nonce = "nonce", dcqlQuery = DcqlQuery(queries),
        )
        val result = WalletPresentationHandler.buildVpToken(
            wallet = wallet,
            request = BuildVpTokenRequest(
                requestUrl = Url("https://verifier.example/authorize"),
                keyId = pidKey.id.value,
                selectedCredentialOptions = listOf(
                    PresentationCredentialSelection(queryId = "pid", credentialId = "pid"),
                    PresentationCredentialSelection(queryId = "identity", credentialId = "identity"),
                ),
            ),
            resolveAuthorizationRequest = { ResolvedAuthorizationRequest.Plain(request) },
        )

        val vpToken = Json.parseToJsonElement(result.vpToken).jsonObject
        fun keyBinding(queryId: String) = vpToken.getValue(queryId).jsonArray.single().jsonPrimitive.content.substringAfterLast('~')
        CompactJws.verify(keyBinding("pid"), pidKey, JwsAlgorithm.ES256)
        CompactJws.verify(keyBinding("identity"), identityKey, JwsAlgorithm.ES256)
        assertFails { CompactJws.verify(keyBinding("identity"), pidKey, JwsAlgorithm.ES256) }
    }
}
