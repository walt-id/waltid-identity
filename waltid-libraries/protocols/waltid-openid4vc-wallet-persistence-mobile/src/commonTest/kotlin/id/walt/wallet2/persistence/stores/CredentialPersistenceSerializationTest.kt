package id.walt.wallet2.persistence.stores

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CredentialPersistenceSerializationTest {

    @Test
    fun preservesSdJwtDisclosuresForReloadableDisplayData() = runTest {
        val (_, credential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)

        val serialized = credential.serializedForWalletPersistence()

        assertEquals(SdJwtExamples.sdJwtVcSignedExample2, serialized)

        val (_, reloadedCredential) = CredentialParser.detectAndParse(serialized)
        assertEquals("Inga", reloadedCredential.credentialData["given_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Silverstone", reloadedCredential.credentialData["family_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("1991-11-06", reloadedCredential.credentialData["birthdate"]?.jsonPrimitive?.contentOrNull)
        assertFalse(reloadedCredential.credentialData.containsKey("_sd"))
    }
}
