package id.walt.openid4vci.validation

import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.requests.authorization.OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultAccessTokenRequestValidatorTest {

    private val validator = DefaultAccessTokenRequestValidator()

    @Test
    fun `parses token request authorization details`() {
        val result = validator.validate(
            parameters = mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf("code"),
                "authorization_details" to listOf(
                    """[{"type":"openid_credential","credential_configuration_id":"degree"}]"""
                ),
            ),
            session = DefaultSession(),
        )

        val request = assertIs<AccessTokenRequestResult.Success>(result).request
        val detail = request.authorizationDetails.single()
        assertEquals(OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE, detail.type)
        assertEquals("degree", detail.credentialConfigurationId)
    }

    @Test
    fun `rejects malformed token request authorization details`() {
        val result = validator.validate(
            parameters = mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "code" to listOf("code"),
                "authorization_details" to listOf("not-json"),
            ),
            session = DefaultSession(),
        )

        assertIs<AccessTokenRequestResult.Failure>(result)
    }

    @Test
    fun `rejects empty token request authorization details`() {
        val result = validator.validate(
            parameters = mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf("code"),
                "authorization_details" to listOf("[]"),
            ),
            session = DefaultSession(),
        )

        assertIs<AccessTokenRequestResult.Failure>(result)
    }
}
