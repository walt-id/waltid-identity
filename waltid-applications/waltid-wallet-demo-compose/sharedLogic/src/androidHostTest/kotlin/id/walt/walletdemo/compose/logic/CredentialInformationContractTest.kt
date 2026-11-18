package id.walt.walletdemo.compose.logic

import java.io.File
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.*
import kotlin.test.*

class CredentialInformationContractTest {
    @Test fun effectiveMdocReviewUsesTheBinaryMarkerForImagesAndOtherBytes() {
        val bytes = File(requireNotNull(System.getProperty("walletDemoImageFixturesDir")), "synthetic-signature.png").readBytes()
        val portrait = Base64.UrlSafe.encode(bytes).trimEnd('=')
        val attachment = "SGVsbG8sIHdhbGxldA"
        val namespace = "org.iso.18013.5.1"
        val rawPortrait = bytes.joinToString(prefix = "[", postfix = "]")
        val selectionPath = "\"$namespace\".\"portrait\""
        val option = WalletDemoPresentationCredentialOption(
            "mdl", "one", format = "mso_mdoc", label = "Driving licence", issuer = null,
            credentialDataJson = """{"$namespace":{"portrait":$rawPortrait}}""",
            disclosures = listOf(WalletDemoPresentationDisclosure("Portrait", selectionPath, rawPortrait,
                selectivelyDisclosable = true, required = true)),
        )
        fun location(path: String, isBinary: Boolean = false, sharedBy: List<String> = emptyList()) =
            WalletDemoClaimLocation(path, path, sharedBy, alwaysCarried = false, isBinary = isBinary)
        val review = WalletDemoPreparedClaimReview(listOf(WalletDemoCredentialClaims("mdl", "one", "mso_mdoc",
            """{"$namespace":{"portrait":"$portrait","attachment":"$attachment","text":"$attachment","blob":"$portrait","image":[137,80,78,71,4]}}""",
            wholeCredential = false, locations = listOf(
                location("[]"), location("""["$namespace"]"""),
                location("""["$namespace","portrait"]""", isBinary = true, sharedBy = listOf(selectionPath)),
                location("""["$namespace","attachment"]""", isBinary = true),
                location("""["$namespace","text"]"""),
                location("""["$namespace","blob"]""", isBinary = true),
                location("""["$namespace","image"]"""),
            ) + (0..4).map { location("""["$namespace","image",$it]""") })))
        val nodes = review.toEffectiveSharingInformation(listOf(option), emptySet()).single()
            .contributions.single().nodes.single().children.associateBy { it.label }
        val photo = nodes.getValue("Portrait")
        assertEquals("[\"$namespace\"].portrait", photo.path.id)
        assertEquals("Required", photo.reason)
        assertTrue(photo.children.isEmpty())
        val image = assertIs<DisplayValue.Image>(assertIs<DisplayValue.DeferredImage>(photo.value).resolve())
        assertEquals("image/png", image.mimeType)
        assertContentEquals(bytes, image.bytes)
        assertEquals(DisplayValue.Text("Binary data"), nodes.getValue("Attachment").value)
        assertEquals(DisplayValue.DecodedText("Hello, wallet"), nodes.getValue("Text").value)
        assertEquals(DisplayValue.Text("Binary data"), nodes.getValue("Blob").value)
        val numericImage = nodes.getValue("Image")
        assertEquals(SharingClaimNode.Kind.Array, numericImage.kind)
        assertEquals((0..4).toList(), numericImage.children.map { it.sourceArrayIndex })
        assertEquals(listOf("137", "80", "78", "71", "4").map(DisplayValue::NumberValue), numericImage.children.map { it.value })
    }

    @Test
    fun projectedArraySelectorsRetainUniqueLabelsAndRestrictions() {
        val fixture = credentialInformationFixture()
        val metadata = fixture.getValue("projectedArrayClaims").toString()
        val claims = StoredCredentialMetadataParser.claims(metadata, listOf("en"))
        assertEquals(fixture.getValue("projectedArrayLabels").jsonArray.map { it.jsonPrimitive.content }, claims.map { it.displayName })
        assertEquals(listOf(listOf(JsonPrimitive("accounts"), JsonPrimitive(1), JsonPrimitive("iban")), listOf(JsonPrimitive("name"))), claims.map { it.path })
        assertEquals(true, claims.first().mandatory)
        val summary = CredentialSummary(id = "array-contract", format = "dc+sd-jwt", label = "Account", issuer = null,
            credentialDataJson = fixture.getValue("projectedArrayCredentialData").toString(), metadataJson = metadata)
        val rows = CredentialDisplayNormalizer.toDetails(summary, listOf("en")).groups.flatMap { it.items }
        val accounts = assertIs<DisplayValue.ListValue>(rows.single { it.path.id == "accounts" }.value).values
        val entries = accounts.map { assertIs<DisplayValue.ObjectValue>(it).entries.single() }
        assertEquals(listOf("Iban", "Second IBAN"), entries.map { it.label })
        assertEquals(listOf(ClaimLabelSource.Wallet, ClaimLabelSource.IssuerMetadata), entries.map { it.labelSource })
        assertEquals("Full name", rows.single { it.path.id == "name" }.label)
        val option = WalletDemoPresentationCredentialOption(queryId = "account", credentialId = summary.id,
            format = summary.format, label = "Account", issuer = null, credentialDataJson = summary.credentialDataJson.orEmpty(), metadataJson = metadata,
            disclosures = listOf(WalletDemoPresentationDisclosure(label = "Iban", path = "[\"accounts\",1,\"iban\"]",
                valueJson = "\"DE456\"", selectivelyDisclosable = true)))
        assertEquals("Second IBAN", option.toRequestedDisclosureGroup(listOf("en"))!!.items.single().label)
        val path = option.disclosures.single().path
        val review = WalletDemoPreparedClaimReview(listOf(WalletDemoCredentialClaims("account", summary.id, summary.format,
            """{"accounts":[{"iban":"DE456"}]}""", false, listOf(
                WalletDemoClaimLocation("[]", "[]", emptyList(), true),
                WalletDemoClaimLocation("""["accounts"]""", """["accounts"]""", emptyList(), true),
                WalletDemoClaimLocation("""["accounts",1]""", """["accounts",0]""", emptyList(), true),
                WalletDemoClaimLocation(path, """["accounts",0,"iban"]""", listOf(path), false),
            ))))
        val account = review.toEffectiveSharingInformation(listOf(option), emptySet(), listOf("en"))
            .single().contributions.single().nodes.single().children.single()
        assertEquals("accounts[1]", account.path.id)
        assertEquals("Second IBAN", account.children.single().label)
        assertEquals(DisplayValue.Text("DE456"), account.children.single().value)
    }

    @Test
    fun sharingKeepsUnavoidableIdentifiersAndCustomClaimsAcrossFormats() {
        val fixture = credentialInformationFixture()
        fixture.getValue("sharingVisibility").jsonArray.forEach { row ->
            val case = row.jsonObject
            val path = case.getValue("path").jsonPrimitive.content
            val requested = case.getValue("requested").jsonPrimitive.boolean
            val visible = case.getValue("visible").jsonPrimitive.boolean
            val option = WalletDemoPresentationCredentialOption(queryId = "identity", credentialId = "one",
                format = case.getValue("format").jsonPrimitive.content, label = "Identity", issuer = "Issuer",
                credentialDataJson = "{}", disclosures = listOf(WalletDemoPresentationDisclosure(
                    label = "Field", path = path, valueJson = "\"Value\"", selectivelyDisclosable = false,
                    required = false, selectable = false, requested = requested)))
            val information = option.toSharingInformation()
            assertEquals(visible, information.fields.isNotEmpty(), row.toString())
            assertEquals(visible && !requested, information.additionalInformationLabels.isNotEmpty(), row.toString())
            information.fields.forEach { assertTrue(it.included) }
        }
    }

    @Test
    fun issuerLabelsAndOrderAreConsistentAcrossStoredAndRequestedInformation() {
        val fixture = credentialInformationFixture()
        fun strings(key: String) = fixture.getValue(key).jsonArray.map { it.jsonPrimitive.content }
        val metadata = fixture.getValue("metadata").toString()
        val format = fixture.getValue("format").jsonPrimitive.content
        val locales = strings("preferredLocales")
        val summary = CredentialSummary(id = "metadata-contract", format = format, label = "Identity", issuer = null,
            credentialDataJson = fixture.getValue("credentialData").toString(), metadataJson = metadata)
        val rows = CredentialDisplayNormalizer.toDetails(summary, locales).groups.flatMap { it.items }
        assertEquals(strings("expectedStoredRootPaths"), rows.map { it.path.id })
        assertEquals(strings("expectedLabels").take(2), rows.take(2).map { it.label })
        fun allItems(items: List<ClaimItem>): List<ClaimItem> = items.flatMap { item ->
            listOf(item) + ((item.value as? DisplayValue.ObjectValue)?.entries?.let(::allItems) ?: emptyList())
        }
        val all = allItems(rows)
        val paths = listOf("family_name", "given_name", "[\"ns.with.dots\"].given_name", "address.line_1")
        val labelled = paths.map { path -> all.single { it.path.id == path } }
        assertEquals(strings("expectedLabels"), labelled.map { it.label })
        assertEquals(strings("expectedValues"), labelled.map { assertIs<DisplayValue.Text>(it.value).value })
        assertTrue(labelled.all { it.labelSource == ClaimLabelSource.IssuerMetadata })
        assertEquals(all.size, all.map { it.path.id }.distinct().size)
        assertEquals(DisplayValue.BooleanValue(false), all.single { it.path.id == "extra" }.value)
        for (path in listOf("address", "[\"ns.with.dots\"]")) assertIs<DisplayValue.ObjectValue>(rows.single { it.path.id == path }.value)
        val option = WalletDemoPresentationCredentialOption(queryId = "identity", credentialId = summary.id,
            format = format, label = "Identity", issuer = null, credentialDataJson = summary.credentialDataJson.orEmpty(), metadataJson = metadata,
            disclosures = strings("requestedPaths").map { WalletDemoPresentationDisclosure(label = "Unhelpful fallback", path = it, valueJson = "\"Ada\"", selectivelyDisclosable = false) })
        val requested = option.toRequestedDisclosureGroup(locales)!!.items
        assertEquals(strings("requestedLabels"), requested.map { it.label })
        assertTrue(requested.all { it.labelSource == ClaimLabelSource.IssuerMetadata })
        assertEquals(listOf(1, 2), requested.map { it.displayOrder })
    }

    @Test
    fun contradictoryOrMalformedStoredClaimMetadataDoesNotInventLabels() {
        for (claims in listOf("[{\"path\":[\"name\"]},{\"path\":[\"name\"]}]", "[{\"path\":[false]}]", "[null]")) {
            assertTrue(StoredCredentialMetadataParser.claims("{\"credentialClaims\":$claims}").isEmpty())
        }
    }

    @Test
    fun arrayIndicesCannotAccidentallyMatchAPropertyPath() {
        val metadata = listOf(WalletDemoCredentialClaimMetadata(listOf("jobs", "name"), null, "Company name"))
        val item = ClaimItem(path = ClaimItemPath.topLevel("jobs").indexedChild(0).child("name"),
            pathComponents = listOf("jobs", "name"), label = "Name", value = DisplayValue.Text("Engineer"))
        assertEquals("Name", item.withClaimMetadata(metadata, "dc+sd-jwt").label)
        assertEquals("Name", item.withClaimMetadata(metadata, "dc+sd-jwt",
            disclosurePathExpression("[\"jobs\",0,\"name\"]", "dc+sd-jwt")).label)
        val specialKey = "line\nwith \"quotes\""
        val path = ClaimItemPath.topLevel("object").child(specialKey)
        assertEquals(listOf(ClaimPathExpression.Segment.Key("object"), ClaimPathExpression.Segment.Key(specialKey)),
            ClaimPathExpression.parse(path.id).segments)
    }

    private fun credentialInformationFixture(): JsonObject {
        val directory = requireNotNull(System.getProperty("walletDemoImageFixturesDir"))
        return Json.parseToJsonElement(File(directory, "credential-information.json").readText()).jsonObject
    }
}
