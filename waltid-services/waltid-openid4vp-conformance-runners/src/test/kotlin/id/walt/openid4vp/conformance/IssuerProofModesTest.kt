package id.walt.openid4vp.conformance

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import id.walt.openid4vp.conformance.testplans.*
import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class IssuerProofModesTest {
    @TempDir lateinit var reports: Path

    private val selected = IssuerVariantSelection(
        clientAuthTypes = setOf("client_attestation"), senderConstrains = setOf("dpop"),
        authorizationRequestTypes = setOf("simple"), requestMethods = setOf("unsigned"),
    ).select(IssuerVariantMatrix.all())

    @Test
    fun expandsBothModesWithoutAddingSuiteVariantParameters() {
        val expanded = expandIssuerProofModes(selected, parseIssuerProofModes("jwt,attestation"))
        assertEquals(40, expanded.size)
        assertEquals(40, expanded.map { it.id }.toSet().size)
        for (mode in listOf("jwt", "attestation")) {
            assertEquals(12, expanded.count { it.credentialProofType == mode && !it.isHaip })
            assertEquals(8, expanded.count { it.credentialProofType == mode && it.isHaip })
        }
        expanded.forEach { variant ->
            assertEquals(variant.credentialProofType, variant.toJsonObject()["credential_proof_type"]?.jsonPrimitive?.content)
            assertFalse("credential_proof_type" in variant.testPlanCreationVariant())
            assertEquals(variant.copy(credentialProofType = null).testPlanCreationVariant(), variant.testPlanCreationVariant())
        }
        assertEquals(selected, expandIssuerProofModes(selected, parseIssuerProofModes(null)))
        val oldId = selected.first().id
        assertEquals(2, IssuerVariantSelection(explicitVariantIds = setOf(oldId)).select(expanded).size)
        val attestationId = "$oldId-proof-attestation"
        assertEquals(listOf(attestationId),
            IssuerVariantSelection(explicitVariantIds = setOf(attestationId)).select(expanded).map { it.id })
        for (input in listOf("", "jwt,", "unknown")) {
            assertFailsWith<IllegalArgumentException> { parseIssuerProofModes(input) }
        }
    }

    @Test
    fun everyFixtureConfigurationHasIsolatedProofTypeAndMatchingProfile() {
        fun fixture(name: String) = ConfigFactory.parseString(
            requireNotNull(javaClass.getResource("/issuer2/$name")).readText()
        ).resolve()
        val profiles = fixture("issuer2-profiles-ci.conf").getConfig("profiles")
        for (name in listOf("credential-issuer-metadata-ci.conf", "credential-issuer-metadata-key-attestation.conf")) {
            val catalog = fixture(name).getConfig("credentialConfigurations")
            val metadata = buildJsonObject {
                put("credential_configurations_supported", Json.parseToJsonElement(catalog.root().render(ConfigRenderOptions.concise())))
            }
            catalog.root().keys.forEach { id ->
                val mode = if (id.endsWith(".attestation")) "attestation" else "jwt"
                requireIsolatedIssuerProofType(metadata, id, mode)
                assertEquals(id, profiles.getConfig(deriveIssuerCredentialProfileId(id)).getString("credentialConfigurationId"))
            }
        }
    }

    @Test
    fun rejectsMixedProofTypesToPreventSuitePreferenceOverridingHint() {
        val both = Json.parseToJsonElement("""{
          "credential_configurations_supported": {"example": {"proof_types_supported": {
            "jwt": {"proof_signing_alg_values_supported": ["ES256"], "key_attestations_required": {}},
            "attestation": {"proof_signing_alg_values_supported": ["ES256"]}
          }}}
        }""").jsonObject
        for (mode in listOf("jwt", "attestation")) {
            assertFailsWith<IllegalArgumentException> { requireIsolatedIssuerProofType(both, "example", mode) }
        }
        assertFails { requireIsolatedIssuerProofType(both, "missing", "attestation") }
    }

    @Test
    fun requiresActualProofAndKeyAttestationGenerationEvidence() {
        val key = TestLogEntry(src = "VCIGenerateKeyAttestationIfNecessary", result = "SUCCESS")
        for ((mode, generator) in listOf("jwt" to "VCIGenerateJwtProof", "attestation" to "VCIGenerateAttestationProof")) {
            val proof = TestLogEntry(src = generator, result = "SUCCESS")
            requireIssuerProofEvidence(mode, listOf(key, proof))
            for (logs in listOf(emptyList(), listOf(key), listOf(proof), listOf(key, proof.copy(result = "FAILURE")))) {
                assertFailsWith<IllegalArgumentException> { requireIssuerProofEvidence(mode, logs) }
            }
            val other = if (mode == "jwt") "attestation" else "jwt"
            assertFailsWith<IllegalArgumentException> { requireIssuerProofEvidence(other, listOf(key, proof)) }
        }
    }

    @Test
    fun writesAggregateAndSeparateReportsWithoutOverwritingVariants() {
        val expanded = expandIssuerProofModes(selected, listOf("jwt", "attestation"))
        val results = expanded.map { variant ->
            IssuerVariantRunResult(variant.id, variant.toJsonObject(), IssuerVariantRunStatus.GENERATED)
        }
        IssuerVariantReportWriter.write(reports.toString(), expanded, results, true)
        fun read(dir: Path, file: String) = Json.parseToJsonElement(Files.readString(dir.resolve(file))).jsonArray
        assertEquals(40, read(reports, "results.json").size)
        for (mode in listOf("jwt", "attestation")) {
            val subset = read(reports.resolve(mode), "results.json")
            assertEquals(20, subset.size)
            assertTrue(subset.all { it.jsonObject.getValue("variantId").jsonPrimitive.content.endsWith("-proof-$mode") })
            assertEquals(20, read(reports.resolve(mode), "matrix.json").size)
        }
    }

    private fun fixtureMetadata(): JsonObject {
        val catalog = ConfigFactory.parseString(requireNotNull(
            javaClass.getResource("/issuer2/credential-issuer-metadata-ci.conf")
        ).readText()).resolve().getConfig("credentialConfigurations")
        return buildJsonObject {
            put("credential_configurations_supported", Json.parseToJsonElement(catalog.root().render(ConfigRenderOptions.concise())))
        }
    }

    private fun configurationId(variant: IssuerVariant): String {
        val base = if (variant.credentialFormat == "sd_jwt_vc") {
            if (variant.isHaip) "identity_credential_haip" else "identity_credential"
        } else {
            if (variant.isHaip) "org.iso.18013.5.1.mDL.haip" else "org.iso.18013.5.1.mDL"
        }
        return if (variant.credentialProofType == "attestation") "$base.attestation" else base
    }

    @Test
    fun preflightFindsAllMissingAttestationConfigurationsBeforeAnyVariantExecutes() {
        val expanded = expandIssuerProofModes(selected, listOf("jwt", "attestation"))
        val catalog = fixtureMetadata().getValue("credential_configurations_supported").jsonObject
        val jwtOnly = buildJsonObject {
            put("credential_configurations_supported", JsonObject(catalog.filterKeys { !it.endsWith(".attestation") }))
        }
        val results = preflightIssuerConfigurations(jwtOnly, expanded, ::configurationId)
        assertEquals(20, results.count { it.status == IssuerVariantRunStatus.GENERATED })
        assertEquals(20, results.count { it.status == IssuerVariantRunStatus.BLOCKED })
        assertTrue(results.all { it.planId == null && it.modules.isEmpty() })
        val error = assertFailsWith<IllegalArgumentException> { requireIssuerConfigurationPreflight(results) }
        assertContains(error.message!!, "no conformance tests were started")
        catalog.keys.filter { it.endsWith(".attestation") }.forEach { assertContains(error.message!!, it) }
        assertEquals(4, results.mapNotNull { it.error }.distinct().size)
        IssuerVariantReportWriter.write(reports.toString(), expanded, results, true)
        val saved = Json.parseToJsonElement(Files.readString(reports.resolve("attestation/results.json"))).jsonArray
        assertEquals(20, saved.size)
        assertTrue(saved.all { it.jsonObject.getValue("status").jsonPrimitive.content == "BLOCKED" })
    }

    @Test
    fun preflightAcceptsCompleteFixturesAndBlocksMixedOrUnresolvedConfigurations() {
        val expanded = expandIssuerProofModes(selected, listOf("jwt", "attestation"))
        val metadata = fixtureMetadata()
        val valid = preflightIssuerConfigurations(metadata, expanded, ::configurationId)
        assertEquals(40, valid.size)
        assertTrue(valid.all { it.status == IssuerVariantRunStatus.GENERATED && it.error == null })
        requireIssuerConfigurationPreflight(valid)
        val id = configurationId(expanded.last())
        val catalog = metadata.getValue("credential_configurations_supported").jsonObject.toMutableMap()
        val configuration = catalog.getValue(id).jsonObject.toMutableMap()
        configuration["proof_types_supported"] = buildJsonObject {
            put("attestation", JsonObject(emptyMap()))
            put("jwt", JsonObject(emptyMap()))
        }
        catalog[id] = JsonObject(configuration)
        val mixed = preflightIssuerConfigurations(buildJsonObject {
            put("credential_configurations_supported", JsonObject(catalog))
        }, expanded, ::configurationId)
        assertFailsWith<IllegalArgumentException> { requireIssuerConfigurationPreflight(mixed) }
        assertTrue(mixed.any { it.error?.contains("must advertise only attestation") == true })
        val unresolved = preflightIssuerConfigurations(metadata, selected) { null }
        assertTrue(unresolved.all { it.status == IssuerVariantRunStatus.BLOCKED })
        val absent = preflightIssuerConfigurations(metadata, selected) { "missing" }
        assertTrue(absent.all { it.error?.contains("missing credential configuration missing") == true })
    }

    @Test
    fun switchingProofModesRemovesStaleReportsAndPreservesOtherFiles() {
        fun write(modes: List<String>) {
            val variants = expandIssuerProofModes(selected, modes)
            IssuerVariantReportWriter.write(reports.toString(), variants, variants.map {
                IssuerVariantRunResult(it.id, it.toJsonObject(), IssuerVariantRunStatus.PASSED)
            }, true)
        }
        write(listOf("jwt", "attestation"))
        val notes = reports.resolve("jwt/notes.txt")
        Files.writeString(notes, "keep")
        for ((modes, obsolete) in listOf(
            listOf("attestation") to listOf("jwt"),
            listOf("jwt") to listOf("attestation"),
            emptyList<String>() to listOf("jwt", "attestation"),
        )) {
            write(modes)
            for (mode in obsolete) {
                for (file in listOf("matrix.json", "results.json", "summary.md")) {
                    assertFalse(Files.exists(reports.resolve("$mode/$file")))
                }
            }
            val saved = Json.parseToJsonElement(Files.readString(reports.resolve("results.json"))).jsonArray
            assertEquals(20, saved.size)
            assertTrue(saved.all {
                it.jsonObject.getValue("variant").jsonObject["credential_proof_type"]?.jsonPrimitive?.content == modes.singleOrNull()
            })
            assertEquals("keep", Files.readString(notes))
        }
    }

    @Test
    fun preparationClearsOnlyGeneratedFilesEvenIfRunFailsBeforeMetadata() {
        // A report directory may not exist on the first run.
        IssuerVariantReportWriter.prepareForRun(reports.resolve("new").toString())
        val expanded = expandIssuerProofModes(selected, listOf("jwt", "attestation"))
        IssuerVariantReportWriter.write(reports.toString(), expanded, expanded.map {
            IssuerVariantRunResult(it.id, it.toJsonObject(), IssuerVariantRunStatus.PASSED)
        }, true)
        val notes = reports.resolve("notes.txt")
        Files.writeString(notes, "keep")
        IssuerVariantReportWriter.prepareForRun(reports.toString())
        for (dir in listOf(reports, reports.resolve("jwt"), reports.resolve("attestation"))) {
            for (file in listOf("matrix.json", "results.json", "summary.md")) {
                assertFalse(Files.exists(dir.resolve(file)))
            }
        }
        assertEquals("keep", Files.readString(notes))
    }
}
