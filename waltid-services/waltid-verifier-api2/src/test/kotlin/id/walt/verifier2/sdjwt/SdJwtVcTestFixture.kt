package id.walt.verifier2.sdjwt

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.Crypto2DidKeyRegistrar
import id.walt.sdjwt.Crypto2JWTCryptoProvider
import id.walt.sdjwt.Crypto2SdJwtKey
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal const val SD_JWT_VC_TEST_VCT = "https://issuer.example/identity_credential"

/** Issues a synthetic credential from one timestamp; only birthdate is selectively disclosable. */
internal suspend fun issueSdJwtVcForHolder(holderDid: String): SdJwtCredential {
    val issuerKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("sd-jwt-test-issuer"),
            spec = KeySpec.Edwards(EdwardsCurve.ED25519),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )
    val issuerDid = Crypto2DidKeyRegistrar().createByKey(issuerKey, DidKeyCreateOptions()).did
    val issuedAt = Clock.System.now()
    val claims = buildJsonObject {
        put("given_name", "John")
        put("family_name", "Doe")
        put("age_over_18", true)
        put("address", buildJsonObject { put("street_address", "123 Main St") })
        put("birthdate", "1940-01-01")
        put("iat", issuedAt.epochSeconds)
    }
    val sdJwtVc = SDJwtVC.sign(
        sdPayload = SDPayload.createSDPayload(claims, SDMap(mapOf("birthdate" to SDField(true)))),
        jwtCryptoProvider = Crypto2JWTCryptoProvider(
            mapOf(issuerDid to Crypto2SdJwtKey(issuerKey, JwsAlgorithm.EDDSA, keyId = issuerDid))
        ),
        issuerDid = issuerDid,
        holderDid = holderDid,
        issuerKeyId = issuerDid,
        vct = SD_JWT_VC_TEST_VCT,
        nbf = issuedAt.epochSeconds,
        exp = (issuedAt + 365.days).epochSeconds,
        subject = holderDid,
    )
    return CredentialParser.parseOnly(sdJwtVc.toString(formatForPresentation = false, withKBJwt = false)) as SdJwtCredential
}

/** Changes signature bytes while preserving the issuer-signed header, claims and disclosures. */
internal suspend fun SdJwtCredential.withInvalidIssuerSignature(): SdJwtCredential {
    val parts = requireNotNull(signedWithDisclosures).split('.', limit = 3).toMutableList()
    parts[2] = (if (parts[2].first() == 'A') "B" else "A") + parts[2].drop(1)
    return CredentialParser.parseOnly(parts.joinToString(".")) as SdJwtCredential
}
