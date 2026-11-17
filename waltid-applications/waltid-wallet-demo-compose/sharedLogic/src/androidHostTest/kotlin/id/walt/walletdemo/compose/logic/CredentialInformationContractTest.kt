package id.walt.walletdemo.compose.logic

import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.*

class CredentialInformationContractTest {
    @Test fun sharingKeepsUnavoidableIdentifiersAndCustomClaimsAcrossFormats() {
        val fixture = Json.parseToJsonElement(File(requireNotNull(System.getProperty("walletDemoImageFixturesDir")), "credential-information.json").readText()).jsonObject
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

    @Test fun issuerLabelsAndOrderAreConsistentAcrossStoredAndRequestedInformation() {
        val fixture = Json.parseToJsonElement(File(requireNotNull(System.getProperty("walletDemoImageFixturesDir")), "credential-information.json").readText()).jsonObject
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

    @Test fun contradictoryOrMalformedStoredClaimMetadataDoesNotInventLabels() {
        for (claims in listOf("[{\"path\":[\"name\"]},{\"path\":[\"name\"]}]", "[{\"path\":[false]}]", "[null]")) {
            assertTrue(StoredCredentialMetadataParser.claims("{\"credentialClaims\":$claims}").isEmpty())
        }
    }

    @Test fun arrayIndicesCannotAccidentallyMatchAPropertyPath() {
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
}
