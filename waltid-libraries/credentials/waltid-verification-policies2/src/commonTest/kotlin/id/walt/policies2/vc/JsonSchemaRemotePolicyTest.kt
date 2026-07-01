package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.W3CExamples
import id.walt.credentials.formats.DigitalCredential
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JsonSchemaRemotePolicyTest {

    @Test
    fun testOk() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = WALT_ID_SCHEMA_URL,
            credential = CredentialParser.parseOnly(W3CExamples.waltidIssuedJoseSignedW3CCredential),
            shouldSucceed = true,
        )
    }

    @Test
    fun testFail() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = WALT_ID_SCHEMA_URL,
            credential = CredentialParser.parseOnly(W3CExamples.joseSignedW3CCredential),
            shouldSucceed = false,
        )
    }
}

class JsonSchemaRemotePolicyTest2 {

    @Test
    fun testOk() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = OPEN_BADGE_SCHEMA_URL,
            credential = W3CExamples.compliantOpenBadgeCredential,
            shouldSucceed = true,
        )
    }

    @Test
    fun testFail() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = OPEN_BADGE_SCHEMA_URL,
            credential = W3CExamples.noncompliantOpenBadgeCredential,
            shouldSucceed = false,
        )
    }
}

internal expect suspend fun createRemoteJsonSchemaPolicy(schemaUrl: Url): JsonSchemaPolicy

private suspend fun runRemoteSchemaPolicyTest(
    schemaUrl: Url,
    credential: DigitalCredential,
    shouldSucceed: Boolean,
) {
    DidService.minimalInit()

    val remotePolicy = createRemoteJsonSchemaPolicy(schemaUrl)
    val baseTest = object : BasePolicyTest() {
        override val policy = remotePolicy
        override val credentialOk = suspend { credential }
        override val credentialNok = suspend { credential }
    }

    if (shouldSucceed) {
        baseTest.runBaseTestOk()
    } else {
        baseTest.runBaseTestNok()
    }
}

private val WALT_ID_SCHEMA_URL =
    Url("https://raw.githubusercontent.com/walt-id/waltid-identity/7e128d18c21c8fce684574aa424cffd21a3eadb1/waltid-libraries/credentials/waltid-verification-policies2/src/commonTest/resources/json-schema-remote-policy.json")

private val OPEN_BADGE_SCHEMA_URL =
    Url("https://raw.githubusercontent.com/walt-id/waltid-identity/refs/heads/main/waltid-libraries/credentials/waltid-verification-policies2/src/commonTest/resources/ob_v3p0_achievementcredential_schema.json")
