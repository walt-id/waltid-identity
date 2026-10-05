package id.waltid.openid4vp.wallet

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

/** One request for two SD-JWT VCs that are bound to different holder keys (OpenID4VP 1.0: holder binding per query). */
class MultiKeyPresentationTest {

    private class Holder(val key: JWKKey, val publicJwk: JsonObject) {
        val ecKey: ECKey = ECKey.parse(publicJwk.toString())
    }

    private suspend fun holder(): Holder = JWKKey.generate(KeyType.secp256r1).let { Holder(it, it.getPublicKey().exportJWKObject()) }

    private val issuer = ECKeyGenerator(Curve.P_256).generate()

    private fun credential(vct: String, holder: Holder): SdJwtCredential {
        val disclosure = SdJwtSelectiveDisclosure("salt-$vct", "given_name", JsonPrimitive("Alice"))
        val claims = buildJsonObject {
            put("iss", "https://issuer.example"); put("vct", vct); put("_sd_alg", "sha-256")
            put("iat", 1789990000); put("exp", 4102444800L)
            put("cnf", buildJsonObject { put("jwk", holder.publicJwk) })
            put("_sd", buildJsonArray { add(hash(disclosure.encoded)) })
        }
        val signed = JWSObject(JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType("dc+sd-jwt")).build(), Payload(claims.toString()))
            .apply { sign(ECDSASigner(issuer)) }.serialize()
        return SdJwtCredential(
            dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
            disclosables = mapOf("$._sd" to setOf(disclosure.encoded)), disclosures = listOf(disclosure),
            credentialData = claims, originalCredentialData = claims, signature = null, signed = signed,
        )
    }

    private val queries = listOf(
        CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("urn:pid"))),
        CredentialQuery("identity", CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf("urn:identity"))),
    )
    private val request = AuthorizationRequest(clientId = "verifier", nonce = "nonce", dcqlQuery = DcqlQuery(queries))

    private fun matches(pid: SdJwtCredential, identity: SdJwtCredential) = listOf(pid, identity).zip(queries).associate { (credential, query) ->
        query.id to listOf(
            DcqlMatcher.DcqlMatchResult(
                RawDcqlCredential("stored-${query.id}", "dc+sd-jwt", credential.credentialData, originalCredential = credential),
                credential.disclosures!!.associateBy { it.name!! }, query,
            )
        )
    }

    private suspend fun present(pid: Holder, identity: Holder, resolver: CredentialPresentationKeyResolver?) = WalletPresentFunctionality2.buildVpToken(
        authorizationRequest = request,
        matchedCredentials = matches(credential("urn:pid", pid), credential("urn:identity", identity)),
        holderKey = assertNotNull(WalletCrypto2KeyAdapter.signingKey(pid.key)),
        holderDid = null,
        scaAuthorizer = null,
        credentialHolderKeyResolver = resolver,
    )

    private fun keyBindingJwt(vpToken: String, queryId: String): JWSObject =
        JWSObject.parse(Json.parseToJsonElement(vpToken).jsonObject.getValue(queryId).jsonArray.single().jsonPrimitive.content.substringAfterLast('~'))

    @Test
    fun `each credential is presented with its own holder key`() = runTest {
        val pid = holder()
        val identity = holder()
        val keys = mapOf("stored-pid" to pid, "stored-identity" to identity)
        val vpToken = present(pid, identity) { credentialId, _ ->
            CredentialPresentationKey(assertNotNull(WalletCrypto2KeyAdapter.signingKey(keys.getValue(credentialId).key)), did = null)
        }

        assertEquals(setOf("pid", "identity"), Json.parseToJsonElement(vpToken).jsonObject.keys)
        assertTrue(keyBindingJwt(vpToken, "pid").verify(ECDSAVerifier(pid.ecKey)))
        assertTrue(keyBindingJwt(vpToken, "identity").verify(ECDSAVerifier(identity.ecKey)))
        assertFalse(keyBindingJwt(vpToken, "identity").verify(ECDSAVerifier(pid.ecKey)))
    }

    @Test
    fun `a credential is not presented with another credential's key`() = runTest {
        val error = assertFailsWith<IllegalArgumentException> { present(holder(), holder(), resolver = null) }
        assertEquals(
            "The credential for query 'identity' is bound to another holder key (cnf.jwk) than the key selected to present it",
            error.message,
        )
    }

    @Test
    fun `credentials bound to the same key share it`() = runTest {
        val shared = holder()
        val vpToken = present(shared, shared, resolver = null)
        assertTrue(keyBindingJwt(vpToken, "pid").verify(ECDSAVerifier(shared.ecKey)))
        assertTrue(keyBindingJwt(vpToken, "identity").verify(ECDSAVerifier(shared.ecKey)))
    }

    private fun hash(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
}
