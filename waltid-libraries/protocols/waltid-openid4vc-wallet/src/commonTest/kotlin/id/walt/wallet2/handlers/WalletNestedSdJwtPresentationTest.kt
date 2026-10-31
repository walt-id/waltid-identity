package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.DcqlParser
import id.walt.sdjwt.SDJwt
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vp.wallet.request.AuthenticatedClientFacts
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Presents a nested SD-JWT VC through the whole wallet (parsing, DCQL matching, consent, presenter) and checks
 * what the verifier rebuilds from the vp_token (RFC 9901 §7.1) against the expected result of each request.
 *
 * A claims path presents the claims it selects plus the disclosures containing them, never their selectively
 * disclosable descendants (OpenID4VP §6.4, §7.1).
 */
class WalletNestedSdJwtPresentationTest {

    // ---------------------------------------------------------------------------------------------------------
    // Credential: everything is selectively disclosable except given_name, family_name and nationalities[1].kind
    //
    //   nationalities: ["IN", {kind: "residency", country: "DE", since: 2015, documents: ["passport", "visa"]}, "FR"]
    //   address:       {street: "Main St 1", locality: "Vienna", geo: {lat: 48.2, lng: 16.37}}
    //   matrix:        [["a", "b"], ["c"]]
    // ---------------------------------------------------------------------------------------------------------

    private val sdJwt = sdJwt {
        val nationalities = buildJsonArray {
            add(element("IN"))
            add(element(buildJsonObject {
                put("kind", "residency")
                putJsonArray("_sd") {
                    add(property("country", JsonPrimitive("DE")))
                    add(property("since", JsonPrimitive(2015)))
                    add(property("documents", buildJsonArray { add(element("passport")); add(element("visa")) }))
                }
            }))
            add(element("FR"))
        }
        val address = buildJsonObject {
            putJsonArray("_sd") {
                add(property("street", JsonPrimitive("Main St 1")))
                add(property("locality", JsonPrimitive("Vienna")))
                add(property("geo", buildJsonObject {
                    putJsonArray("_sd") {
                        add(property("lat", JsonPrimitive(48.2)))
                        add(property("lng", JsonPrimitive(16.37)))
                    }
                }))
            }
        }
        val matrix = buildJsonArray {
            add(element(buildJsonArray { add(element("a")); add(element("b")) }))
            add(element(buildJsonArray { add(element("c")) }))
        }
        payload {
            putJsonArray("_sd") {
                add(property("nationalities", nationalities))
                add(property("address", address))
                add(property("matrix", matrix))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Credential with clear-text parents: only the leaves are selectively disclosable
    //
    //   nationalities: ["IN", "FR"]
    //   address:       {street: "Main St 1", geo: {lat: 48.2}}
    //   degrees:       [{type: "BSc"}, {type: "MSc"}]
    //   matrix:        [["a"], ["c"]]
    // ---------------------------------------------------------------------------------------------------------

    private val clearParentsSdJwt = sdJwt {
        payload {
            putJsonArray("nationalities") { add(element("IN")); add(element("FR")) }
            putJsonObject("address") {
                putJsonArray("_sd") { add(property("street", JsonPrimitive("Main St 1"))) }
                putJsonObject("geo") { putJsonArray("_sd") { add(property("lat", JsonPrimitive(48.2))) } }
            }
            putJsonArray("degrees") {
                listOf("BSc", "MSc").forEach { type ->
                    addJsonObject { putJsonArray("_sd") { add(property("type", JsonPrimitive(type))) } }
                }
            }
            putJsonArray("matrix") {
                addJsonArray { add(element("a")) }
                addJsonArray { add(element("c")) }
            }
        }
    }

    /** Collects the disclosures of an SD-JWT while its issuer-signed payload is built. */
    private class SdJwtBuilder {
        val disclosures = mutableListOf<SdJwtSelectiveDisclosure>()

        fun property(name: String, value: JsonElement): String =
            SdJwtSelectiveDisclosure("salt-$name-${disclosures.size}", name, value).also { disclosures += it }.asHashedFromEncoded()

        fun element(value: JsonElement): JsonObject =
            SdJwtSelectiveDisclosure("salt-element-${disclosures.size}", null, value).also { disclosures += it }
                .let { buildJsonObject { put("...", it.asHashedFromEncoded()) } }

        fun element(value: String) = element(JsonPrimitive(value))

        /** The payload claims shared by both credentials, plus [claims]. */
        fun payload(claims: JsonObjectBuilder.() -> Unit) = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", VCT)
            put("_sd_alg", "sha-256")
            put("given_name", "John")
            put("family_name", "Doe")
            claims()
        }
    }

    private fun sdJwt(issuerSignedPayload: SdJwtBuilder.() -> JsonObject): String {
        val builder = SdJwtBuilder()
        val payload = builder.issuerSignedPayload()
        fun encode(json: JsonObject) = json.toString().encodeToByteArray().encodeToBase64Url()
        val header = buildJsonObject { put("alg", "ES256"); put("typ", "dc+sd-jwt") }
        // The wallet does not verify the issuer signature when presenting
        return "${encode(header)}.${encode(payload)}.c2lnbmF0dXJl~${builder.disclosures.joinToString("~") { it.encoded }}~"
    }

    // ---------------------------------------------------------------------------------------------------------
    // Presenting
    // ---------------------------------------------------------------------------------------------------------

    /** What the verifier rebuilds from the presentation, and the consent rows the wallet showed for it. */
    private class Presented(val verifierView: JsonObject, val preview: List<PresentationDisclosure>) {
        fun previewValue(path: String): JsonElement? = preview.single { it.path == path }.value
        fun previewName(path: String): String? = preview.single { it.path == path }.name
    }

    private suspend fun wallet(sdJwt: String): Wallet {
        val (_, credential) = CredentialParser.detectAndParse(sdJwt)
        val store = InMemoryCredentialStore().also { it.addCredential(StoredCredential(id = CREDENTIAL_ID, credential = credential)) }
        // The legacy P-256 generator uses the iOS Keychain; these tests need only software signing.
        val holderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val keyStore = InMemoryKeyStore().also { it.addCrypto2Key(holderKey) }
        return Wallet(id = "wallet", keyStores = listOf(keyStore), credentialStores = listOf(store))
    }

    /** A request for [claims] (DCQL claims queries as JSON), with optional `claim_sets`. */
    private fun request(vararg claims: String, claimSets: String? = null): AuthorizationRequest {
        val query = """{"credentials": [{"id": "$QUERY_ID", "format": "dc+sd-jwt", "meta": {"vct_values": ["$VCT"]},
            "claims": [${claims.joinToString(",")}]${claimSets?.let { ""","claim_sets": $it""" }.orEmpty()}}]}"""
        return AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            nonce = "nonce",
            dcqlQuery = DcqlParser.parse(query).getOrThrow(),
        )
    }

    private fun resolverFor(request: AuthorizationRequest): suspend (Url) -> ResolvedAuthorizationRequest =
        { ResolvedAuthorizationRequest.Plain(request, AuthenticatedClientFacts.redirectUriBound(request)) }

    /**
     * Previews and presents [request] as a consent UI would: with [selectedOptionalPaths] the user's choice of
     * optional claims (`null` keeps the matcher's choice), from [credential].
     */
    private suspend fun present(
        request: AuthorizationRequest,
        selectedOptionalPaths: List<String>? = null,
        credential: String = sdJwt,
    ): Presented {
        val wallet = wallet(credential)
        val preview = WalletPresentationHandler.previewPresentationStateless(
            wallet = wallet,
            request = PreviewPresentationRequest(REQUEST_URL),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            resolveAuthorizationRequest = resolverFor(request),
        )
        val ready = assertIs<StatelessPreviewPresentationResult.Ready>(preview)

        val vpToken = WalletPresentationHandler.buildVpToken(
            wallet = wallet,
            request = BuildVpTokenRequest(
                requestUrl = REQUEST_URL,
                selectedCredentialOptions = listOf(PresentationCredentialSelection(QUERY_ID, CREDENTIAL_ID)),
                selectedDisclosureOptions = selectedOptionalPaths?.map { PresentationDisclosureSelection(QUERY_ID, CREDENTIAL_ID, it) },
                keyId = ready.keyId,
            ),
            resolveAuthorizationRequest = resolverFor(request),
        ).vpToken
        val presentation = Json.parseToJsonElement(vpToken).jsonObject.getValue(QUERY_ID).jsonArray.single().jsonPrimitive.content

        // Rebuilt by the SD-JWT library, which also rejects disclosures not reachable from the payload
        return Presented(SDJwt.parse(presentation).fullPayload, ready.credentialOptions.single().disclosures)
    }

    private suspend fun isRejected(request: AuthorizationRequest): Boolean =
        WalletPresentationHandler.previewPresentationStateless(
            wallet = wallet(sdJwt),
            request = PreviewPresentationRequest(REQUEST_URL),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            resolveAuthorizationRequest = resolverFor(request),
        ) !is StatelessPreviewPresentationResult.Ready

    private fun claim(vararg path: Any?) = buildJsonObject {
        putJsonArray("path") { path.forEach { add(if (it is Int) JsonPrimitive(it) else JsonPrimitive(it as String?)) } }
    }.toString()

    private fun json(value: String) = Json.parseToJsonElement(value)

    // ---------------------------------------------------------------------------------------------------------
    // Cases
    // ---------------------------------------------------------------------------------------------------------

    @Test
    fun containerPathPresentsTheContainerWithoutItsDisclosedChildren() = runTest {
        // request: ["nationalities"]
        // sent:    nationalities
        // sees:    nationalities = []
        val nationalitiesOnly = present(request(claim("nationalities")))
        assertEquals(json("[]"), nationalitiesOnly.verifierView["nationalities"])
        assertEquals(json("[]"), nationalitiesOnly.previewValue(""""nationalities""""))

        // request: ["address"]
        // sent:    address
        // sees:    address = {}
        val addressOnly = present(request(claim("address")))
        assertEquals(json("{}"), addressOnly.verifierView["address"])
        assertEquals(json("{}"), addressOnly.previewValue(""""address""""))
    }

    @Test
    fun nullPresentsEveryElementOfTheArray() = runTest {
        // request: ["nationalities", null]
        // sent:    nationalities, element 0, element 1, element 2
        // sees:    nationalities = ["IN", {kind: "residency"}, "FR"]   (element 1 without its disclosed fields)
        val everyNationality = present(request(claim("nationalities", null)))
        assertEquals(json("""["IN", {"kind": "residency"}, "FR"]"""), everyNationality.verifierView["nationalities"])
        assertEquals(json("""["IN", {"kind": "residency"}, "FR"]"""), everyNationality.previewValue(""""nationalities".null"""))
        assertEquals("nationalities", everyNationality.previewName(""""nationalities".null"""))

        // request: ["matrix", null]
        // sent:    matrix, row 0, row 1
        // sees:    matrix = [[], []]
        assertEquals(json("[[], []]"), present(request(claim("matrix", null))).verifierView["matrix"])

        // request: ["matrix", null, null]
        // sent:    matrix, both rows, every cell
        // sees:    matrix = [["a", "b"], ["c"]]
        assertEquals(json("""[["a", "b"], ["c"]]"""), present(request(claim("matrix", null, null))).verifierView["matrix"])
    }

    @Test
    fun indexPresentsThatElementWithTheDisclosuresContainingIt() = runTest {
        // request: ["nationalities", 0]
        // sent:    nationalities, element 0
        // sees:    nationalities = ["IN"]
        val firstNationality = present(request(claim("nationalities", 0)))
        assertEquals(json("""["IN"]"""), firstNationality.verifierView["nationalities"])
        assertEquals(json(""""IN""""), firstNationality.previewValue(""""nationalities".0"""))
        assertEquals("nationalities", firstNationality.previewName(""""nationalities".0"""))

        // request: ["matrix", 0, 1]
        // sent:    matrix, row 0, cell "b"
        // sees:    matrix = [["b"]]
        assertEquals(json("""[["b"]]"""), present(request(claim("matrix", 0, 1))).verifierView["matrix"])
    }

    @Test
    fun nestedPathPresentsTheChainOfDisclosuresLeadingToIt() = runTest {
        // request: ["nationalities", 1, "country"]
        // sent:    nationalities, element 1, country
        // sees:    nationalities = [{kind: "residency", country: "DE"}]
        val country = present(request(claim("nationalities", 1, "country")))
        assertEquals(json("""[{"kind": "residency", "country": "DE"}]"""), country.verifierView["nationalities"])
        assertEquals(json(""""DE""""), country.previewValue(""""nationalities".1."country""""))
        assertEquals("country", country.previewName(""""nationalities".1."country""""))

        // request: ["nationalities", 1, "documents", null]
        // sent:    nationalities, element 1, documents, both documents
        // sees:    nationalities = [{kind: "residency", documents: ["passport", "visa"]}]
        assertEquals(
            json("""[{"kind": "residency", "documents": ["passport", "visa"]}]"""),
            present(request(claim("nationalities", 1, "documents", null))).verifierView["nationalities"],
        )

        // request: ["address", "geo", "lat"]
        // sent:    address, geo, lat
        // sees:    address = {geo: {lat: 48.2}}
        val latitude = present(request(claim("address", "geo", "lat")))
        assertEquals(json("""{"geo": {"lat": 48.2}}"""), latitude.verifierView["address"])
        assertEquals(json("48.2"), latitude.previewValue(""""address"."geo"."lat""""))
    }

    @Test
    fun claimsSharingParentsAreAllPresented() = runTest {
        // request: ["address", "street"], ["address", "locality"], ["address", "geo", "lat"], ["address", "geo", "lng"]
        // sent:    address, street, locality, geo, lat, lng   (shared parents once)
        // sees:    address = {street, locality, geo: {lat, lng}}
        val fullAddress = present(
            request(claim("address", "street"), claim("address", "locality"), claim("address", "geo", "lat"), claim("address", "geo", "lng"))
        )
        assertEquals(
            json("""{"street": "Main St 1", "locality": "Vienna", "geo": {"lat": 48.2, "lng": 16.37}}"""),
            fullAddress.verifierView["address"],
        )

        // request: ["nationalities"], ["nationalities", 2]
        // sent:    nationalities, element 2   (the container path adds nothing more)
        // sees:    nationalities = ["FR"]
        assertEquals(
            json("""["FR"]"""),
            present(request(claim("nationalities"), claim("nationalities", 2))).verifierView["nationalities"],
        )
    }

    @Test
    fun clearTextClaimInsideADisclosedElementPresentsOnlyThatElement() = runTest {
        // request: ["nationalities", 1, "kind"]
        // sent:    nationalities, element 1   (kind is clear text inside element 1)
        // sees:    nationalities = [{kind: "residency"}]
        val kind = present(request(claim("nationalities", 1, "kind")))
        assertEquals(json("""[{"kind": "residency"}]"""), kind.verifierView["nationalities"])
        assertEquals(json(""""residency""""), kind.previewValue(""""nationalities".1."kind""""))
    }

    @Test
    fun clearTextClaimNeedsNoDisclosure() = runTest {
        // request: ["given_name"]
        // sent:    no disclosures
        // sees:    given_name = "John", and no nationalities, address or matrix
        val givenName = present(request(claim("given_name")))
        assertEquals(json(""""John""""), givenName.verifierView["given_name"])
        assertEquals("given_name", givenName.previewName(""""given_name""""))
        assertFalse("nationalities" in givenName.verifierView)
        assertFalse("address" in givenName.verifierView)
        assertFalse("matrix" in givenName.verifierView)
    }

    @Test
    fun clearTextContainerIsPreviewedWithoutItsUnpresentedDisclosures() = runTest {
        // credential: clear-text parents, selectively disclosable leaves

        // request: ["nationalities"]
        // sent:    no disclosures
        // sees:    nationalities = []
        val nationalities = present(request(claim("nationalities")), credential = clearParentsSdJwt)
        assertEquals(json("[]"), nationalities.verifierView["nationalities"])
        assertEquals(json("[]"), nationalities.previewValue(""""nationalities""""))
        assertEquals("nationalities", nationalities.previewName(""""nationalities""""))

        // request: ["address"]
        // sent:    no disclosures
        // sees:    address = {geo: {}}
        val address = present(request(claim("address")), credential = clearParentsSdJwt)
        assertEquals(json("""{"geo": {}}"""), address.verifierView["address"])
        assertEquals(json("""{"geo": {}}"""), address.previewValue(""""address""""))
        assertEquals("address", address.previewName(""""address""""))

        // request: ["degrees", 0]
        // sent:    no disclosures
        // sees:    degrees = [{}, {}]
        val firstDegree = present(request(claim("degrees", 0)), credential = clearParentsSdJwt)
        assertEquals(json("[{}, {}]"), firstDegree.verifierView["degrees"])
        assertEquals(json("{}"), firstDegree.previewValue(""""degrees".0"""))
        assertEquals("degrees", firstDegree.previewName(""""degrees".0"""))

        // request: ["matrix", 1]
        // sent:    no disclosures
        // sees:    matrix = [[], []]
        val secondRow = present(request(claim("matrix", 1)), credential = clearParentsSdJwt)
        assertEquals(json("[[], []]"), secondRow.verifierView["matrix"])
        assertEquals(json("[]"), secondRow.previewValue(""""matrix".1"""))
        assertEquals("matrix", secondRow.previewName(""""matrix".1"""))

        // request: ["matrix", 1, 0]
        // sent:    cell "c"
        // sees:    matrix = [[], ["c"]]
        val cell = present(request(claim("matrix", 1, 0)), credential = clearParentsSdJwt)
        assertEquals(json("""[[], ["c"]]"""), cell.verifierView["matrix"])
        assertEquals(json(""""c""""), cell.previewValue(""""matrix".1.0"""))
    }

    @Test
    fun valuesPresentOnlyTheMatchingElements() = runTest {
        // request: ["nationalities", null] with values ["FR"]
        // sent:    nationalities, element 2
        // sees:    nationalities = ["FR"]
        val france = present(request("""{"path": ["nationalities", null], "values": ["FR"]}"""))
        assertEquals(json("""["FR"]"""), france.verifierView["nationalities"])
        assertEquals(json("""["FR"]"""), france.previewValue(""""nationalities".null"""))

        // request: ["nationalities", null] with values ["AT"]
        // result:  rejected, no element matches
        assertTrue(isRejected(request("""{"path": ["nationalities", null], "values": ["AT"]}""")))
    }

    @Test
    fun pathThatDoesNotSelectAClaimIsRejected() = runTest {
        // request: ["nationalities", null, "country"]
        // result:  rejected, "IN" is not an object so "country" cannot be applied to every element
        assertTrue(isRejected(request(claim("nationalities", null, "country"))))

        // request: ["nationalities", 7]
        // result:  rejected, there is no element 7
        assertTrue(isRejected(request(claim("nationalities", 7))))

        // request: ["address", 0] and ["nationalities", "country"]
        // result:  rejected, an index on an object and a key on an array
        assertTrue(isRejected(request(claim("address", 0))))
        assertTrue(isRejected(request(claim("nationalities", "country"))))
    }

    @Test
    fun optionalClaimIsPresentedOnlyWhenTheUserSelectsIt() = runTest {
        // request: ["given_name"] required, ["nationalities", 2] optional (claim_sets [["name"], ["name", "fr"]])
        val request = request(
            """{"id": "name", "path": ["given_name"]}""",
            """{"id": "fr", "path": ["nationalities", 2]}""",
            claimSets = """[["name"], ["name", "fr"]]""",
        )
        val optionalPath = """"nationalities".2"""

        // user leaves it out
        // sent:    no disclosures
        // sees:    no nationalities
        val withheld = present(request, selectedOptionalPaths = emptyList())
        assertTrue(withheld.preview.single { it.path == optionalPath }.selectable)
        assertFalse("nationalities" in withheld.verifierView)

        // user selects it
        // sent:    nationalities, element 2
        // sees:    nationalities = ["FR"]
        val shared = present(request, selectedOptionalPaths = listOf(optionalPath))
        assertEquals(json("""["FR"]"""), shared.verifierView["nationalities"])
    }

    private companion object {
        const val VCT = "https://issuer.example/pid"
        const val QUERY_ID = "pid"
        const val CREDENTIAL_ID = "credential"
        val REQUEST_URL = Url("https://verifier.example/request")
    }
}
