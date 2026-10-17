package id.walt.wallet2.server

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.handlers.PresentationCredentialOption
import id.walt.wallet2.handlers.PresentationDisclosure
import id.walt.wallet2.handlers.StatelessPreviewPresentationResult
import id.walt.wallet2.server.models.PreviewCredentialDisclosure
import id.walt.wallet2.server.models.toPreviewResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PresentationPreviewContractTest {
    private val preview = StatelessPreviewPresentationResult.Ready(
        authorizationRequest = AuthorizationRequest(clientId = "https://verifier.example"),
        keyId = "holder-key", responseEncryption = null, transactionData = emptyList(),
        credentialRequirements = emptyList(),
        credentialOptions = listOf(PresentationCredentialOption(
            queryId = "pid", credentialId = "credential", multiple = false, format = "dc+sd-jwt",
            issuer = null, subject = null, label = null, credentialData = JsonObject(emptyMap()),
            disclosures = listOf(
                PresentationDisclosure("given_name", "Given name", JsonPrimitive("Ada"), true, true, false),
                PresentationDisclosure("family_name", "Family name", JsonPrimitive("Lovelace"), false, false, false, requested = false),
            ),
        )),
    )

    @Test
    fun `default preview remains decodable with the released strict disclosure model`() {
        for (encodeDefaults in listOf(false, true)) {
            val json = Json { this.encodeDefaults = encodeDefaults }
            val disclosures = preview.toPreviewResponse().credentialOptions.single().disclosures
            for (disclosure in disclosures) {
                val encoded = json.encodeToString(disclosure)
                assertFalse("requested" in Json.parseToJsonElement(encoded).jsonObject)
                assertEquals(disclosure.value, json.decodeFromString<ReleasedDisclosure>(encoded).value)
            }
        }
    }

    @Test
    fun `opt in distinguishes unavoidable additional claims without changing selection flags`() {
        val legacy = preview.toPreviewResponse()
        val detailed = preview.toPreviewResponse(includeDetails = true)
        val disclosures = detailed.credentialOptions.single().disclosures
        assertEquals(listOf(true, false), disclosures.map { it.requested })
        assertEquals(legacy, detailed.copy(credentialOptions = detailed.credentialOptions.map { option ->
            option.copy(disclosures = option.disclosures.map { it.copy(requested = true) })
        }))
        for (encodeDefaults in listOf(false, true)) {
            val json = Json { this.encodeDefaults = encodeDefaults }
            val encoded = json.encodeToString(disclosures.last())
            assertEquals(false, Json.parseToJsonElement(encoded).jsonObject.getValue("requested").jsonPrimitive.boolean)
            assertEquals(disclosures.last(), json.decodeFromString<PreviewCredentialDisclosure>(encoded))
        }
    }

    @Serializable
    private data class ReleasedDisclosure(
        val path: String, val name: String? = null, val value: JsonElement,
        val selectivelyDisclosable: Boolean, val required: Boolean, val selectable: Boolean,
    )
}
