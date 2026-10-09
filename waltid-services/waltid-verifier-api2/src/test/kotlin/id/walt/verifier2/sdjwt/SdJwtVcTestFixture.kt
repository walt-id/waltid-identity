package id.walt.verifier2.sdjwt

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.WaltIdJWTCryptoProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal const val SD_JWT_VC_TEST_VCT = "https://issuer.example/identity_credential"

/** Issues a synthetic credential from one timestamp; only birthdate is selectively disclosable. */
@Suppress("DEPRECATION")
internal suspend fun issueSdJwtVcForHolder(
    holderDid: String,
    issuedAt: Instant = Clock.System.now(),
): SdJwtCredential {
    val issuerKey = JWKKey.generate(KeyType.Ed25519)
    val issuerDid = DidKeyRegistrar().registerByKey(issuerKey, DidKeyCreateOptions()).did
    val issuerKeyId = issuerKey.getKeyId()
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
        jwtCryptoProvider = WaltIdJWTCryptoProvider(mapOf(issuerKeyId to issuerKey)),
        issuerDid = issuerDid,
        holderDid = holderDid,
        issuerKeyId = issuerKeyId,
        vct = SD_JWT_VC_TEST_VCT,
        nbf = issuedAt.epochSeconds,
        exp = (issuedAt + 365.days).epochSeconds,
        additionalJwtHeader = mapOf("kid" to issuerDid),
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
