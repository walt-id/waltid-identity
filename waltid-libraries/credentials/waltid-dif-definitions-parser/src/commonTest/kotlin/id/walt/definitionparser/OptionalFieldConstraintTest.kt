package id.walt.definitionparser

import id.walt.definitionparser.PresentationDefinition.InputDescriptor.Constraints.Field
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that the PresentationDefinitionParser correctly handles the `optional` property
 * on Field objects per DIF Presentation Exchange v2.0.0 (Input Evaluation):
 *
 * - optional: true  + path missing  → constraint passes
 * - optional: false + path missing  → constraint fails
 * - optional: null  + path missing  → constraint fails (default is required)
 * - optional: true  + path present  → filter must still validate
 */
class OptionalFieldConstraintTest {

    private val enquirer = JsonObjectEnquirer()

    // A document with only a top-level "type" field
    private val simpleDocument = Json.decodeFromString<JsonObject>(
        """{"type": "HealthInsurance", "issuer": "did:example:123"}"""
    )

    @Test
    fun optionalFieldWithMissingPathPassesConstraint() {
        val field = Field(
            optional = true,
            path = listOf("$.credentialSubject.address.streetAddress")
        )
        assertTrue(
            enquirer.filterConstraint(simpleDocument, field),
            "Optional field with unresolvable path should pass"
        )
    }

    @Test
    fun requiredFieldWithMissingPathFailsConstraint() {
        val field = Field(
            optional = false,
            path = listOf("$.credentialSubject.address.streetAddress")
        )
        assertFalse(
            enquirer.filterConstraint(simpleDocument, field),
            "Required field with unresolvable path should fail"
        )
    }

    @Test
    fun defaultFieldWithMissingPathFailsConstraint() {
        // optional not specified (null) → treated as required
        val field = Field(
            path = listOf("$.credentialSubject.address.streetAddress")
        )
        assertFalse(
            enquirer.filterConstraint(simpleDocument, field),
            "Field without optional flag and unresolvable path should fail"
        )
    }

    @Test
    fun optionalFieldWithPresentPathAndPassingFilterPassesConstraint() {
        val field = Field(
            optional = true,
            path = listOf("$.type"),
            filter = Json.decodeFromString("""{"type": "string", "pattern": "^HealthInsurance$"}""")
        )
        assertTrue(
            enquirer.filterConstraint(simpleDocument, field),
            "Optional field with resolvable path and matching filter should pass"
        )
    }

    @Test
    fun optionalFieldWithPresentPathAndFailingFilterFailsConstraint() {
        // DIF PE spec: "Even when optional is present, the value at the path MUST validate
        // against the filter, if a filter is present."
        val field = Field(
            optional = true,
            path = listOf("$.type"),
            filter = Json.decodeFromString("""{"type": "string", "pattern": "^DriversLicense$"}""")
        )
        assertFalse(
            enquirer.filterConstraint(simpleDocument, field),
            "Optional field with resolvable path but non-matching filter should fail"
        )
    }

    @Test
    fun mixedRequiredAndOptionalFieldsMatchCredential() = runTest {
        // Simulates the HealthInsurance scenario: required fields resolve,
        // optional address fields do not → credential should still match
        val credential = Json.decodeFromString<JsonObject>(
            """
            {
                "type": "HealthInsurance",
                "issuer": "did:example:123",
                "credentialSubject": {
                    "policyHolder": {
                        "name": "Alice"
                    }
                }
            }
            """.trimIndent()
        )

        val inputDescriptor = PresentationDefinition.InputDescriptor(
            id = "health_insurance",
            name = "Health Insurance Credential",
            constraints = PresentationDefinition.InputDescriptor.Constraints(
                fields = listOf(
                    Field(
                        path = listOf("$.type"),
                        filter = Json.decodeFromString("""{"type": "string", "pattern": "^HealthInsurance$"}""")
                    ),
                    Field(
                        path = listOf("$.credentialSubject.policyHolder.name")
                    ),
                    Field(
                        optional = true,
                        path = listOf("$.credentialSubject.policyHolder.address.streetAddress")
                    ),
                    Field(
                        optional = true,
                        path = listOf("$.credentialSubject.policyHolder.address.postalCode")
                    )
                )
            )
        )

        val matched = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
            flowOf(credential),
            inputDescriptor
        ).toList()

        assertEquals(1, matched.size, "Credential should match when only optional fields are missing")
    }

    @Test
    fun allRequiredFieldsMissingRejectsCredential() = runTest {
        val credential = Json.decodeFromString<JsonObject>(
            """{"type": "HealthInsurance"}"""
        )

        val inputDescriptor = PresentationDefinition.InputDescriptor(
            id = "test",
            constraints = PresentationDefinition.InputDescriptor.Constraints(
                fields = listOf(
                    Field(
                        path = listOf("$.credentialSubject.name")
                        // optional not set → required
                    )
                )
            )
        )

        val matched = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
            flowOf(credential),
            inputDescriptor
        ).toList()

        assertEquals(0, matched.size, "Credential should NOT match when required field is missing")
    }
}
