package id.walt.credentials

import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.credentials.signatures.sdjwt.SdJwtDisclosureLocator
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SdJwtDecoyTest {
    private fun disclosure(value: String) = SdJwtSelectiveDisclosure("salt-$value", null, JsonPrimitive(value))
    private fun reference(hash: String) = buildJsonObject { put("...", hash) }
    private fun path(index: Int) = listOf(JsonPrimitive("nationalities"), JsonPrimitive(index))

    private suspend fun parse(items: JsonArray, disclosures: List<SdJwtSelectiveDisclosure>): SelectivelyDisclosableVerifiableCredential {
        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", "https://issuer.example/pid")
            put("_sd_alg", "sha-256")
            put("nationalities", items)
        }
        fun encode(value: JsonObject) = value.toString().encodeToByteArray().encodeToBase64Url()
        val header = buildJsonObject { put("alg", "ES256"); put("typ", "dc+sd-jwt") }
        return CredentialParser.parseOnly("${encode(header)}.${encode(payload)}.c2ln~${disclosures.joinToString("~") { it.encoded }}~")
            as SelectivelyDisclosableVerifiableCredential
    }

    @Test
    fun indicesSkipDecoysAndUnavailableDisclosuresButCountClearText() = runTest {
        val de = disclosure("DE")
        val withheld = disclosure("AT")
        val fr = disclosure("FR")
        val credential = parse(buildJsonArray {
            add(reference("decoy-before"))
            add(reference(de.asHashedFromEncoded()))
            add(reference(withheld.asHashedFromEncoded()))
            add(JsonPrimitive("US"))
            add(reference("decoy-between"))
            add(reference(fr.asHashedFromEncoded()))
            add(reference("decoy-after"))
        }, listOf(de, fr))
        assertEquals(path(0), credential.disclosures!!.single { it.value == de.value }.location)
        assertEquals(path(2), credential.disclosures!!.single { it.value == fr.value }.location)
    }
    @Test
    fun nestedW3cLocationsRemainRelativeToCredentialRoot() {
        val leaf = disclosure("passport")
        val row = SdJwtSelectiveDisclosure("row", null, buildJsonObject {
            putJsonArray("documents") { add(reference("decoy-document")); add(reference(leaf.asHashedFromEncoded())) }
        })
        val parent = SdJwtSelectiveDisclosure("parent", "nationalities", buildJsonArray {
            add(reference("decoy-row")); add(reference(row.asHashedFromEncoded()))
        })
        val payload = buildJsonObject {
            putJsonObject("vc") { putJsonArray("_sd") { add(parent.asHashedFromEncoded()) } }
        }
        val located = SdJwtDisclosureLocator.locate(payload, listOf(leaf, row, parent))
        assertEquals(listOf(JsonPrimitive("nationalities")), located.single { it.name == "nationalities" }.location)
        assertEquals(path(0), located.single { it.encoded == row.encoded }.location)
        assertEquals(path(0) + listOf(JsonPrimitive("documents"), JsonPrimitive(0)), located.single { it.encoded == leaf.encoded }.location)
    }

    @Test
    fun repeatedDigestIsRejectedIncludingUnmatchedDecoys() {
        listOf(emptyList(), listOf(disclosure("DE"))).forEach { available ->
            val hash = available.firstOrNull()?.asHashedFromEncoded() ?: "decoy"
            val payload = buildJsonObject { putJsonArray("nationalities") { add(reference(hash)); add(reference(hash)) } }
            val error = assertFailsWith<IllegalArgumentException> { SdJwtDisclosureLocator.locate(payload, available) }
            assertTrue(error.message.orEmpty().contains("repeated digest"))
        }
    }

    @Test
    fun repeatedDigestAcrossDisclosedAndClearTextContainersIsRejected() {
        val parent = SdJwtSelectiveDisclosure("parent", "hidden", buildJsonObject {
            putJsonArray("_sd") { add("shared-decoy") }
        })
        val payload = buildJsonObject {
            putJsonArray("_sd") { add(parent.asHashedFromEncoded()); add("shared-decoy") }
        }
        assertFailsWith<IllegalArgumentException> { SdJwtDisclosureLocator.locate(payload, listOf(parent)) }
    }

    @Test
    fun unreachableAndDuplicateSuppliedDisclosuresAreRejected() {
        val de = disclosure("DE")
        assertFailsWith<UnreachableDisclosuresException> { SdJwtDisclosureLocator.locate(buildJsonObject {}, listOf(de)) }
        val payload = buildJsonObject { putJsonArray("nationalities") { add(reference(de.asHashedFromEncoded())) } }
        assertFailsWith<UnreachableDisclosuresException> { SdJwtDisclosureLocator.locate(payload, listOf(de, de)) }
    }

    @Test
    fun malformedContainersAreNotSilentlyIgnored() {
        val invalidSd = buildJsonObject { putJsonObject("_sd") {} }
        val invalidArrayDigest = buildJsonObject {
            putJsonArray("nationalities") { addJsonObject { putJsonObject("...") {} } }
        }
        listOf(invalidSd, invalidArrayDigest).forEach { payload ->
            assertFailsWith<IllegalArgumentException> { SdJwtDisclosureLocator.locate(payload, emptyList()) }
        }
    }

    @Test
    fun exactWireEncodingIsUsedWithoutReserializingTheDisclosure() {
        val encoded = "[ \"wire-salt\", \"DE\" ]".encodeToByteArray().encodeToBase64Url()
        val de = SdJwtSelectiveDisclosure(Json.parseToJsonElement("[ \"wire-salt\", \"DE\" ]").jsonArray, encoded)
        val payload = buildJsonObject { putJsonArray("nationalities") { add(reference("decoy")); add(reference(de.asHashedFromEncoded())) } }
        val located = SdJwtDisclosureLocator.locate(payload, listOf(de)).single()
        assertEquals(encoded, located.encoded)
        assertEquals(path(0), located.location)
    }

    @Test
    fun legacyIssuerDigestsAfterDecoysResolveAtTheLogicalIndexWithTheirWireEncoding() {
        val de = disclosure("DE")
        val fr = disclosure("FR")
        // Each element matches only through a legacy digest variant, not the wire-encoding digest
        assertNotEquals(de.asHashedFromEncoded(), de.asHashed2())
        assertNotEquals(fr.asHashedFromEncoded(), fr.asHashed3())
        val payload = buildJsonObject {
            putJsonArray("nationalities") {
                add(reference("decoy-first"))
                add(reference(de.asHashed2()))
                add(reference("decoy-middle"))
                add(reference(fr.asHashed3()))
            }
        }
        val located = SdJwtDisclosureLocator.locate(payload, listOf(de, fr))
        assertEquals(path(0), located.single { it.value == de.value }.location)
        assertEquals(path(1), located.single { it.value == fr.value }.location)
        assertEquals(listOf(de.encoded, fr.encoded), located.map { it.encoded })
    }

    @Test
    fun verifierParsesAPresentationWithDecoysAndWithheldElementsInLogicalOrder() = runTest {
        // nationalities: [decoy, DE, AT (withheld), "US" (clear text), decoy, FR, decoy]; the holder presents DE and FR
        val de = disclosure("DE")
        val fr = disclosure("FR")
        fun encode(value: JsonObject) = value.toString().encodeToByteArray().encodeToBase64Url()
        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", "https://issuer.example/pid")
            put("_sd_alg", "sha-256")
            putJsonArray("nationalities") {
                add(reference("decoy-before"))
                add(reference(de.asHashedFromEncoded()))
                add(reference(disclosure("AT").asHashedFromEncoded()))
                add(JsonPrimitive("US"))
                add(reference("decoy-between"))
                add(reference(fr.asHashedFromEncoded()))
                add(reference("decoy-after"))
            }
        }
        val issuerJwt = "${encode(buildJsonObject { put("alg", "ES256"); put("typ", "dc+sd-jwt") })}.${encode(payload)}.c2ln"
        val kbJwt = "${encode(buildJsonObject { put("alg", "ES256"); put("typ", "kb+jwt") })}." +
            "${encode(buildJsonObject { put("aud", "verifier"); put("nonce", "nonce"); put("sd_hash", "hash") })}.c2ln"

        val presentation = DcSdJwtPresentation.parse("$issuerJwt~${de.encoded}~${fr.encoded}~$kbJwt").getOrThrow()
        assertEquals(json("""["DE", "US", "FR"]"""), presentation.credential.credentialData["nationalities"])
    }

    private fun json(value: String) = Json.parseToJsonElement(value)
}
