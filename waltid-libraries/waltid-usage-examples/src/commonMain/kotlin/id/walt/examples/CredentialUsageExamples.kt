package id.walt.examples

import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.sdjwt.Crypto2AsyncJWTCryptoProvider
import id.walt.sdjwt.Crypto2SdJwtKey
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDMapBuilder
import id.walt.sdjwt.SDPayload
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Issuing and verifying credentials the way an issuer and a verifier would: the issuer signs with a key it holds
 * and publishes its identity as a DID, and the verifier starts from the credential alone - it resolves the
 * issuer key out of the credential itself and validates the signature against it.
 */
object CredentialUsageExamples {

    /** W3C VC in JWT form: issue against a `did:key` issuer, then verify by resolving that DID. */
    suspend fun issueAndVerifyW3cVcJwt(
        issuerKey: Key,
        algorithm: JwsAlgorithm,
        subjectDid: String = SUBJECT_DID,
    ): W3cExampleResult {
        val issuerDidResult = DidUsageExamples.createResolveAndVerify(DidUsageExamples.DidMethod.KEY, issuerKey)
        val issuerDid = issuerDidResult.did
        // The `kid` has to name the DID document's verification method, which is what a verifier looks up. The
        // local key ID means nothing to anyone else.
        val verificationMethodId = issuerDidResult.verificationMethodId

        val credential = W3CVC(
            buildJsonObject {
                put("@context", buildJsonArray { add(JsonPrimitive("https://www.w3.org/2018/credentials/v1")) })
                put("type", buildJsonArray {
                    add(JsonPrimitive("VerifiableCredential"))
                    add(JsonPrimitive("UniversityDegreeCredential"))
                })
                put("issuer", JsonPrimitive(issuerDid))
                put("credentialSubject", buildJsonObject {
                    put("id", JsonPrimitive(subjectDid))
                    put("degree", JsonPrimitive("Bachelor of Science"))
                })
            }
        )

        val signed = credential.signJws(
            issuerKey = issuerKey,
            algorithm = algorithm,
            issuerId = issuerDid,
            issuerKid = verificationMethodId,
            subjectDid = subjectDid,
        )

        // The verifier only holds the credential: the issuer key is resolved out of it.
        val verifiedPayload = JwsSignatureScheme()
            .verifyCrypto2(signed, allowedAlgorithms = setOf(algorithm))
            .getOrThrow()

        return W3cExampleResult(
            issuerDid = issuerDid,
            credentialJwt = signed,
            verifiedIssuer = verifiedPayload.jsonObject["iss"]?.jsonPrimitive?.content,
        )
    }

    /**
     * SD-JWT VC: issue with two selectively disclosable claims, present only one of them, and confirm the other
     * stays hidden while the issuer signature still verifies.
     */
    suspend fun issueAndVerifySdJwtVc(issuerKey: Key, algorithm: JwsAlgorithm): SdJwtExampleResult {
        val issuerDid = DidUsageExamples
            .createResolveAndVerify(DidUsageExamples.DidMethod.JWK, issuerKey)
            .did
        val provider = Crypto2AsyncJWTCryptoProvider(
            mapOf(ISSUER_KEY_ALIAS to Crypto2SdJwtKey(issuerKey, algorithm, issuerDid))
        )

        val claims = buildJsonObject {
            put("iss", JsonPrimitive(issuerDid))
            put("vct", JsonPrimitive(VCT))
            put("given_name", JsonPrimitive("John"))
            put("birthdate", JsonPrimitive("1940-01-01"))
        }
        val disclosable: SDMap = SDMapBuilder()
            .addField("given_name", sd = true)
            .addField("birthdate", sd = true)
            .build()

        val issued = SDJwt.signAsync(
            sdPayload = SDPayload.createSDPayload(claims, disclosable),
            jwtCryptoProvider = provider,
            keyID = ISSUER_KEY_ALIAS,
        )

        // The holder discloses `given_name` and withholds `birthdate`.
        val presented = issued.present(SDMapBuilder().addField("given_name", sd = true).build())
        val received = SDJwt.parse(presented.toString())

        return SdJwtExampleResult(
            issuerDid = issuerDid,
            issuedSdJwt = issued.toString(),
            presentedSdJwt = presented.toString(),
            signatureVerified = received.verifyAsync(provider).verified,
            disclosedClaims = received.fullPayload.keys,
            issuedDisclosableClaims = issued.fullPayload.keys,
        )
    }

    data class W3cExampleResult(
        val issuerDid: String,
        val credentialJwt: String,
        val verifiedIssuer: String?,
    )

    data class SdJwtExampleResult(
        val issuerDid: String,
        val issuedSdJwt: String,
        val presentedSdJwt: String,
        val signatureVerified: Boolean,
        /** Claims a verifier can read from the presentation. */
        val disclosedClaims: Set<String>,
        /** Claims the issuer made available for disclosure. */
        val issuedDisclosableClaims: Set<String>,
    )

    private const val ISSUER_KEY_ALIAS = "issuer"
    private const val VCT = "https://example.com/identity_credential"
    const val SUBJECT_DID = "did:key:zDnaerDaTF5BXEavCrfRZEk316dpbLsfPDZ3WJ5hRTPFU2169"
}
