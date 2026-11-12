package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.ClaimDescription
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import id.walt.openid4vci.metadata.issuer.claimDescriptionConflicts
import id.walt.openid4vci.metadata.issuer.claimDescriptionsToProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

/** Appendix B.3: repeated or contradictory claim descriptions abort processing; Appendix C: format-specific paths. */
class CredentialConfigurationClaimConflictsTest {

    private fun path(json: String): List<JsonElement> = Json.parseToJsonElement(json).jsonArray

    private fun configuration(format: CredentialFormat, vararg paths: String) = CredentialConfiguration(
        format = format,
        credentialMetadata = CredentialMetadata(claims = paths.map { ClaimDescription(path = path(it)) }),
    )

    private fun conflictingPaths(format: CredentialFormat, vararg paths: String): List<List<List<JsonElement>>> =
        configuration(format, *paths).claimDescriptionConflicts().map { it.paths }

    private fun assertConsistent(format: CredentialFormat, vararg paths: String) =
        assertEquals(emptyList(), conflictingPaths(format, *paths))

    // JSON-based credentials

    @Test
    fun `distinct claims, wildcard children, and a container with its child are consistent`() = assertConsistent(
        CredentialFormat.SD_JWT_VC,
        """["given_name"]""",
        """["address"]""",
        """["address", "street"]""",
        """["address", "city"]""",
        """["degrees", null, "type"]""",
        """["degrees", null, "year"]""",
        """["other_nationalities", 0]""",
        """["other_nationalities", 1]""",
    )

    @Test
    fun `the same claim described twice conflicts`() = assertEquals(
        listOf(listOf(path("""["given_name"]"""), path("""["given_name"]"""))),
        conflictingPaths(CredentialFormat.SD_JWT_VC, """["given_name"]""", """["family_name"]""", """["given_name"]"""),
    )

    @Test
    fun `a wildcard and an index into the same array conflict`() = assertEquals(
        listOf(listOf(path("""["nationalities", null]"""), path("""["nationalities", 1]"""))),
        conflictingPaths(CredentialFormat.SD_JWT_VC, """["nationalities", null]""", """["nationalities", 1]"""),
    )

    @Test
    fun `a wildcard and an index conflict even when the paths continue differently`() = assertEquals(
        listOf(listOf(path("""["degrees", null, "type"]"""), path("""["degrees", 0, "year"]"""))),
        conflictingPaths(CredentialFormat.SD_JWT_VC, """["degrees", null, "type"]""", """["degrees", 0, "year"]"""),
    )

    @Test
    fun `a claim addressed as an array and as an object conflicts`() {
        assertEquals(1, conflictingPaths(CredentialFormat.SD_JWT_VC, """["address", "street"]""", """["address", 0]""").size)
        assertEquals(1, conflictingPaths(CredentialFormat.SD_JWT_VC, """["address", "street"]""", """["address", null]""").size)
        assertEquals(1, conflictingPaths(CredentialFormat.SD_JWT_VC, """["degrees", null, "type"]""", """["degrees", null, 0]""").size)
    }

    @Test
    fun `a numeric string key is an object key, not an array index`() =
        assertEquals(1, conflictingPaths(CredentialFormat.SD_JWT_VC, """["by_code", "0"]""", """["by_code", 0]""").size)

    @Test
    fun `one conflict is reported per claim, naming one path of each kind`() = assertEquals(
        listOf(listOf(path("""["nationalities", 0]"""), path("""["nationalities", null]"""))),
        conflictingPaths(
            CredentialFormat.SD_JWT_VC,
            """["nationalities", 0]""", """["nationalities", 1]""", """["nationalities", null]""",
        ),
    )

    @Test
    fun `negative integers are not used in paths of JSON-based credentials`() {
        assertEquals(1, conflictingPaths(CredentialFormat.SD_JWT_VC, """["nationalities", -1]""").size)
        assertEquals(1, conflictingPaths(CredentialFormat.JWT_VC_JSON, """["credentialSubject", "nationalities", -1]""").size)
    }

    @Test
    fun `all formats other than mdoc use JSON semantics`() {
        CredentialFormat.entries.filter { it != CredentialFormat.MSO_MDOC }.forEach { format ->
            assertEquals(1, conflictingPaths(format, """["address", "street"]""", """["address", 0]""").size, format.value)
        }
    }

    // ISO mdoc

    @Test
    fun `mdoc map keys may mix strings, non-negative and negative integers`() = assertConsistent(
        CredentialFormat.MSO_MDOC,
        """["org.example.ns", "map", "a"]""",
        """["org.example.ns", "map", 7]""",
        """["org.example.ns", "map", -1]""",
        """["org.example.ns", "map", 10000000000000000000]""",
        """["org.iso.18013.5.1", "driving_privileges", null, "vehicle_category_code"]""",
    )

    @Test
    fun `an mdoc wildcard conflicts with any other key or index of the same element`() {
        listOf("0", "-1", "\"a\"").forEach { other ->
            assertEquals(
                1,
                conflictingPaths(CredentialFormat.MSO_MDOC, """["ns", "el", null]""", """["ns", "el", $other]""").size,
                other,
            )
        }
    }

    @Test
    fun `the same mdoc claim described twice conflicts`() = assertEquals(
        1,
        conflictingPaths(CredentialFormat.MSO_MDOC, """["org.iso.18013.5.1", "family_name"]""", """["org.iso.18013.5.1", "family_name"]""").size,
    )

    @Test
    fun `mdoc paths start with a namespace and a data element identifier`() {
        assertEquals(1, conflictingPaths(CredentialFormat.MSO_MDOC, """["org.iso.18013.5.1"]""").size)
        assertEquals(1, conflictingPaths(CredentialFormat.MSO_MDOC, """["org.iso.18013.5.1", 0]""").size)
        assertEquals(1, conflictingPaths(CredentialFormat.MSO_MDOC, """[null, "family_name"]""").size)
    }

    // Consumers

    @Test
    fun `configurations without claim descriptions are consistent`() =
        assertEquals(emptyList(), CredentialConfiguration(format = CredentialFormat.SD_JWT_VC).claimDescriptionConflicts())

    @Test
    fun `consistent claim descriptions are all processed`() {
        val configuration = configuration(CredentialFormat.SD_JWT_VC, """["given_name"]""", """["nationalities", null]""")
        var reported = false
        assertEquals(configuration.credentialMetadata?.claims, configuration.claimDescriptionsToProcess { reported = true })
        assertEquals(false, reported)
    }

    @Test
    fun `no claim description is processed when any conflict, and the conflicts are reported`() {
        val configuration = configuration(
            CredentialFormat.SD_JWT_VC, """["given_name"]""", """["nationalities", null]""", """["nationalities", 1]""",
        )
        var reported = emptyList<List<List<JsonElement>>>()
        assertEquals(emptyList(), configuration.claimDescriptionsToProcess { conflicts -> reported = conflicts.map { it.paths } })
        assertEquals(listOf(listOf(path("""["nationalities", null]"""), path("""["nationalities", 1]"""))), reported)
    }

    @Test
    fun `a conflict message names the rule and the paths`() = assertEquals(
        """a null wildcard and an index address the same array: ["nationalities",null], ["nationalities",1]""",
        configuration(CredentialFormat.SD_JWT_VC, """["nationalities", null]""", """["nationalities", 1]""")
            .claimDescriptionConflicts().single().message,
    )
}
