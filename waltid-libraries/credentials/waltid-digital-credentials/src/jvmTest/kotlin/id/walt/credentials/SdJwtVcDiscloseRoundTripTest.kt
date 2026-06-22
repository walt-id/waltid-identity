package id.walt.credentials

import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwtVC
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproduces the enterprise present pipeline at unit level for SD-JWT VC:
 * parse a signed SD-JWT VC, re-disclose its disclosures (as OldWalletPresentFunctionality does),
 * then run the legacy verifier's getFullPayload() — which throws
 * "N disclosure(s) not referenced by any digest" if the re-assembled disclosures don't match the
 * signed payload's `_sd` digests.
 */
class SdJwtVcDiscloseRoundTripTest {

    @Test
    fun disclosedSdJwtVcVerifiesWithLegacyVerifier() = runTest {
        val example = SdJwtExamples.sdJwtVcSignedExample2

        val (_, parsed) = CredentialParser.detectAndParse(example)

        // Storage round-trip: enterprise serializes the parsed credential to the DB and reloads it.
        val json = Json { ignoreUnknownKeys = true }
        val serialized = json.encodeToString(DigitalCredential.serializer(), parsed)
        val vc = json.decodeFromString(DigitalCredential.serializer(), serialized)
        check(vc is SelectivelyDisclosableVerifiableCredential)

        val disclosures = vc.disclosures!!
        // Re-assemble exactly as OldWalletPresentFunctionality does (disclose all).
        val disclosed = vc.disclose(vc, disclosures)

        // Enterprise then does SDJwtVC.parse(disclosed).present(discloseAll = true).toString(...)
        val presented = SDJwtVC.parse(disclosed)
            .present(discloseAll = true)
            .toString(formatForPresentation = true, withKBJwt = false)

        // The legacy verifier resolves the full payload; this throws if any appended disclosure's
        // digest is not referenced in the signed payload's `_sd`.
        val full = SDJwt.parse(presented).fullPayload
        assertEquals("1004", full["sub"]?.toString()?.trim('"'))
        assertEquals("Inga", full["given_name"]?.toString()?.trim('"'))
        assertEquals("Silverstone", full["family_name"]?.toString()?.trim('"'))
        assertEquals("1991-11-06", full["birthdate"]?.toString()?.trim('"'))
    }

    /**
     * Reproduces VerifierPresentCredentialsIntegrationTest.shouldIssueAndPresentSdJwtVc:
     * present only a SUBSET of disclosures (limit_disclosure=required), then run the legacy
     * verifier's getFullPayload(). Must NOT throw "N disclosure(s) not referenced by any digest".
     */
    @Test
    fun disclosingSubsetVerifiesWithLegacyVerifier() = runTest {
        val example = SdJwtExamples.sdJwtVcSignedExample2

        val (_, parsed) = CredentialParser.detectAndParse(example)
        check(parsed is SelectivelyDisclosableVerifiableCredential)
        val allDisclosures = parsed.disclosures!!
        check(allDisclosures.size >= 2) { "Need >=2 disclosures to test a subset, got ${allDisclosures.size}" }

        // Present only ONE disclosure (mimics limit_disclosure with a single requested field).
        val subset = listOf(allDisclosures.first())
        val disclosed = parsed.disclose(parsed, subset)

        // Enterprise: SDJwtVC.parse(disclosed).present(discloseAll = true).toString(...)
        val presented = SDJwtVC.parse(disclosed)
            .present(discloseAll = true)
            .toString(formatForPresentation = true, withKBJwt = false)

        // Legacy verifier resolves the full payload — must not throw on the partial presentation.
        val full = SDJwt.parse(presented).fullPayload

        // The one disclosed claim must be present in the resolved payload.
        val disclosedKey = subset.first().name
        if (disclosedKey != null) {
            assertEquals(true, full.containsKey(disclosedKey), "disclosed claim '$disclosedKey' must resolve")
        }
    }
}
