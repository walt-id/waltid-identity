package id.walt.wallet2.mobile

import android.content.Intent
import androidx.credentials.CreateDigitalCredentialResponse
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDigitalCredentialCreateProviderTest {
    @Test
    fun resolvesOpenId4VciV1CreateRequest() {
        val request = AndroidDigitalCredentialCreateProvider.resolveCreateRequest(
            requestJson = """
                {"requests":[{
                  "protocol":"openid4vci-v1",
                  "data":{
                    "credential_issuer":"https://issuer.example",
                    "credential_configuration_ids":["pid"]
                  }
                }]}
            """.trimIndent(),
            verifiedOrigin = "https://issuer.example",
        )

        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1, request.protocol)
        assertEquals("https://issuer.example", request.verifiedOrigin)
        val offer = Json.parseToJsonElement(request.offerJson).jsonObject
        assertEquals("https://issuer.example", offer["credential_issuer"]?.jsonPrimitive?.content)
        assertEquals(
            "pid",
            offer["credential_configuration_ids"]?.jsonArray?.single()?.jsonPrimitive?.content,
        )
    }

    @Test
    fun resolvesHistoricalOpenId4VciProtocolAlias() {
        val request = AndroidDigitalCredentialCreateProvider.resolveCreateRequest(
            requestJson = """{"requests":[{"protocol":"openid4vci1.0","data":{"credential_issuer":"https://i.example","credential_configuration_ids":["c"]}}]}""",
            verifiedOrigin = "android:apk-key-hash:abc",
        )

        assertEquals("openid4vci1.0", request.protocol)
        assertEquals("https://i.example", Json.parseToJsonElement(request.offerJson).jsonObject["credential_issuer"]?.jsonPrimitive?.content)
    }

    @Test
    fun resolvesCapturedDmvCreateRequestWithoutLiveDependency() {
        val requestJson = requireNotNull(javaClass.getResource("/fixtures/dmv-openid4vci-create-request.json"))
            .readText()
        val request = AndroidDigitalCredentialCreateProvider.resolveCreateRequest(
            requestJson = requestJson,
            verifiedOrigin = "https://digital-credentials.dev",
        )

        assertEquals("openid4vci1.0", request.protocol)
        val offer = Json.parseToJsonElement(request.offerJson).jsonObject
        assertEquals("https://digital-credentials.dev", offer["credential_issuer"]?.jsonPrimitive?.content)
        assertEquals(
            "com.emvco.payment_card",
            offer["credential_configuration_ids"]?.jsonArray?.single()?.jsonPrimitive?.content,
        )
        assertEquals(
            "REDACTED_TEST_PRE_AUTHORIZED_CODE",
            offer["grants"]?.jsonObject
                ?.get("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                ?.jsonObject
                ?.get("pre-authorized_code")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun rejectsUnsupportedOpenId4VciProtocolAlias() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialCreateProvider.resolveCreateRequest(
                requestJson = """{"requests":[{"protocol":"openid4vci","data":{"credential_issuer":"https://i.example","credential_configuration_ids":["c"]}}]}""",
                verifiedOrigin = "android:apk-key-hash:abc",
            )
        }
    }

    @Test
    fun rejectsTopLevelProtocolEnvelopeWithoutRequestsArray() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialCreateProvider.resolveCreateRequest(
                requestJson = """
                    {
                      "protocol":"openid4vci-v1",
                      "data":{"credential_issuer":"https://i.example","credential_configuration_ids":["c"]}
                    }
                """.trimIndent(),
                verifiedOrigin = "https://issuer.example",
            )
        }
    }

    @Test
    fun skipsUnsupportedProtocolsUntilOpenId4VciV1IsFound() {
        val request = AndroidDigitalCredentialCreateProvider.resolveCreateRequest(
            requestJson = """
                {"requests":[
                  {"protocol":"unknown-issuance","data":{"x":1}},
                  {"protocol":"openid4vci-v1","data":{"credential_issuer":"https://i.example","credential_configuration_ids":["c"]}}
                ]}
            """.trimIndent(),
            verifiedOrigin = "https://issuer.example",
        )
        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1, request.protocol)
    }

    @Test
    fun rejectsCreateRequestsWithoutOpenId4VciV1() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialCreateProvider.resolveCreateRequest(
                requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
                verifiedOrigin = "https://issuer.example",
            )
        }
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Test
    fun writesCreateAcknowledgementCancellationAndFailure() {
        val responseIntent = Intent()
        AndroidDigitalCredentialCreateProvider.setResponse(responseIntent)
        val response = assertNotNull(
            PendingIntentHandler.retrieveCreateCredentialResponse(
                androidx.credentials.DigitalCredential.TYPE_DIGITAL_CREDENTIAL,
                responseIntent,
            )
        )
        assertIs<CreateDigitalCredentialResponse>(response)
        val body = Json.parseToJsonElement(response.responseJson).jsonObject
        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1, body["protocol"]?.jsonPrimitive?.content)
        assertEquals("{}", body["data"]?.toString())

        val cancellationIntent = Intent()
        AndroidDigitalCredentialCreateProvider.setCancellation(cancellationIntent)
        assertIs<CreateCredentialCancellationException>(
            PendingIntentHandler.retrieveCreateCredentialException(cancellationIntent),
        )

        val failureIntent = Intent()
        AndroidDigitalCredentialCreateProvider.setFailure(failureIntent, "safe failure")
        val failure = assertIs<CreateCredentialUnknownException>(
            PendingIntentHandler.retrieveCreateCredentialException(failureIntent),
        )
        assertTrue(failure.errorMessage?.contains("safe failure") == true)
    }

    @Test
    fun bindsTheNativeCallerSigningCertificateIntoTheOrigin() {
        assertTrue(
            AndroidDigitalCredentialCreateProvider.nativeAppOrigin(byteArrayOf(1, 2, 3))
                .startsWith("android:apk-key-hash:"),
        )
    }
}
