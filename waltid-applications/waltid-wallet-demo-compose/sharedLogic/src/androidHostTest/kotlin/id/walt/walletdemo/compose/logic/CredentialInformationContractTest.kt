package id.walt.walletdemo.compose.logic

import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.*

class CredentialInformationContractTest {
    @Test fun issuerLabelsAndOrderAreConsistentAcrossStoredAndRequestedInformation() {
        val fixture = Json.parseToJsonElement(File(requireNotNull(System.getProperty("walletDemoImageFixturesDir")), "credential-information.json").readText()).jsonObject
        fun strings(key: String) = fixture.getValue(key).jsonArray.map { it.jsonPrimitive.content }
        val metadata = fixture.getValue("metadata").toString()
        val format = fixture.getValue("format").jsonPrimitive.content
        val locales = strings("preferredLocales")
        val summary = CredentialSummary(id = "metadata-contract", format = format, label = "Identity", issuer = null,
            credentialDataJson = fixture.getValue("credentialData").toString(), metadataJson = metadata)
        val rows = CredentialDisplayNormalizer.toDetails(summary, locales).groups.flatMap { it.items }
        assertEquals(strings("expectedLabels"), rows.take(4).map { it.label })
        assertEquals(strings("expectedValues"), rows.take(4).map { assertIs<DisplayValue.Text>(it.value).value })
        assertTrue(rows.take(4).all { it.labelSource == ClaimLabelSource.IssuerMetadata })
        assertEquals(rows.size, rows.map { it.path.id }.distinct().size)
        assertEquals(DisplayValue.BooleanValue(false), rows.last().value)
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
