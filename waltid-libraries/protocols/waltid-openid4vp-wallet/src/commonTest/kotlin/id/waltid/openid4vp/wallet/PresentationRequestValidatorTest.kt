package id.waltid.openid4vp.wallet

import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalSerializationApi::class)
class PresentationRequestValidatorTest {
    private val p256Capabilities = WalletPresentationFormatRegistry.capabilitiesFromKeyTypes(setOf(KeyType.secp256r1))

    @Test
    fun validRequestReturnsDecodedTransactionData() {
        val result = validate(
            request(transactionData = listOf(transactionData("payment"))),
            transactionDataTypes = TransactionDataTypeRegistry("payment"),
        )

        assertEquals(1, assertIs<PresentationRequestValidationResult.Valid>(result).transactionData.size)
    }

    @Test
    fun unsupportedTransactionDataReturnsProtocolError() {
        val result = validate(
            request(transactionData = listOf(transactionData("unsupported"))),
            transactionDataTypes = TransactionDataTypeRegistry("payment"),
        )

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun unsupportedVerifierFormatsReturnProtocolError() {
        val result = validate(
            request(
                clientMetadata = ClientMetadata(
                    vpFormatsSupported = mapOf("ac_vp" to JsonObject(emptyMap())),
                ),
            ),
        )

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun unsupportedDcqlCredentialFormatReturnsProtocolError() {
        val result = validate(
            request(dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery(CredentialFormat.AC_VP)))),
        )

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun incompatibleHolderAlgorithmsReturnProtocolError() {
        val result = validate(
            request(
                clientMetadata = ClientMetadata(
                    vpFormatsSupported = mapOf(
                        "dc+sd-jwt" to buildJsonObject {
                            put("kb-jwt_alg_values", JsonArray(listOf(JsonPrimitive("EdDSA"))))
                        },
                    ),
                ),
            ),
        )

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun verifierAndQueryMustShareTheSameSupportedFormat() {
        val result = validate(
            request(
                dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery(CredentialFormat.MSO_MDOC))),
                clientMetadata = ClientMetadata(
                    vpFormatsSupported = mapOf("dc+sd-jwt" to JsonObject(emptyMap())),
                ),
            ),
        )

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun issuerAlgorithmConstraintsDoNotDescribeWalletKeySupport() {
        val result = validate(
            request(
                clientMetadata = ClientMetadata(
                    vpFormatsSupported = mapOf(
                        "dc+sd-jwt" to buildJsonObject {
                            put("sd-jwt_alg_values", JsonArray(listOf(JsonPrimitive("EdDSA"))))
                        },
                    ),
                ),
            ),
        )

        assertIs<PresentationRequestValidationResult.Valid>(result)
    }

    @Test
    fun transactionDataRequiresAnAvailableReferencedCredential() {
        val validation = assertIs<PresentationRequestValidationResult.Valid>(
            validate(
                request(transactionData = listOf(transactionData("payment"))),
                transactionDataTypes = TransactionDataTypeRegistry("payment"),
            )
        )

        val error = PresentationRequestValidator.validateTransactionDataCredentialAvailability(
            transactionData = validation.transactionData,
            availableCredentialQueryIds = emptySet(),
        )

        assertEquals(WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA, error?.code)
    }

    @Test
    fun transactionDataAcceptsOneAvailableReferencedCredential() {
        val validation = assertIs<PresentationRequestValidationResult.Valid>(
            validate(
                request(transactionData = listOf(transactionData("payment"))),
                transactionDataTypes = TransactionDataTypeRegistry("payment"),
            )
        )

        val error = PresentationRequestValidator.validateTransactionDataCredentialAvailability(
            transactionData = validation.transactionData,
            availableCredentialQueryIds = setOf("pid"),
        )

        assertEquals(null, error)
    }

    @Test
    fun unsupportedResponseTypeReturnsProtocolErrorWhenDestinationIsUsable() {
        val result = validate(
            request(
                responseType = OpenID4VPResponseType.CODE,
                responseMode = OpenID4VPResponseMode.QUERY,
                responseUri = null,
                redirectUri = "https://verifier.example/callback",
            ),
        )

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.UNSUPPORTED_RESPONSE_TYPE,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun missingNonceReturnsInvalidRequestWhenDestinationIsUsable() {
        val result = validate(request().copy(nonce = null))

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun missingDcqlQueryReturnsInvalidRequest() {
        val result = validate(request(dcqlQuery = null))

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun emptyDcqlQueryReturnsInvalidRequest() {
        val result = validate(request(dcqlQuery = DcqlQuery(credentials = emptyList())))

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun unavailableRequiredCredentialCombinationReturnsAccessDenied() {
        val query = DcqlQuery(
            credentials = listOf(credentialQuery()),
            credentialSets = listOf(CredentialSetQuery(options = listOf(listOf("pid")))),
        )

        val error = PresentationRequestValidator.validateCredentialAvailability(
            query = query,
            availableCredentialQueryIds = emptySet(),
        )

        assertEquals(WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED, error?.code)
    }

    @Test
    fun oneAvailableCredentialSetAlternativeSatisfiesTheRequest() {
        val query = DcqlQuery(
            credentials = listOf(credentialQuery(), credentialQuery(id = "mdl")),
            credentialSets = listOf(
                CredentialSetQuery(options = listOf(listOf("mdl"), listOf("pid"))),
            ),
        )

        val error = PresentationRequestValidator.validateCredentialAvailability(
            query = query,
            availableCredentialQueryIds = setOf("pid"),
        )

        assertEquals(null, error)
    }

    @Test
    fun unsupportedScopeReturnsProtocolError() {
        val result = validate(request(dcqlQuery = null, scope = "unknown_scope"))

        assertEquals(
            WalletPresentFunctionality2.OID4VPErrorCode.INVALID_SCOPE,
            assertIs<PresentationRequestValidationResult.Invalid>(result).error.code,
        )
    }

    @Test
    fun missingClientIdRemainsLocalBecauseResponseIsUnsafe() {
        assertFailsWith<IllegalArgumentException> {
            validate(request(clientId = null))
        }
    }

    @Test
    fun missingResponseDestinationRemainsLocal() {
        assertFailsWith<IllegalArgumentException> {
            validate(request(responseUri = null))
        }
    }

    @Test
    fun boundPlainRedirectRequestCanReturnAnErrorSafely() {
        val request = request(
            clientId = "redirect_uri:https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            responseUri = null,
            redirectUri = "https://verifier.example/callback",
        )

        assertIs<PresentationRequestValidationResult.Valid>(
            validate(request, resolvedRequest = ResolvedAuthorizationRequest.Plain(request)),
        )
    }

    @Test
    fun mismatchedPlainRedirectRequestRemainsLocal() {
        val request = request(
            clientId = "redirect_uri:https://attacker.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            responseUri = null,
            redirectUri = "https://verifier.example/callback",
        )

        assertFailsWith<IllegalArgumentException> {
            validate(request, resolvedRequest = ResolvedAuthorizationRequest.Plain(request))
        }
    }

    @Test
    fun unauthenticatedPlainDirectPostRequestRemainsLocal() {
        val request = request()

        assertFailsWith<IllegalArgumentException> {
            validate(request, resolvedRequest = ResolvedAuthorizationRequest.Plain(request))
        }
    }

    private fun validate(
        request: AuthorizationRequest,
        transactionDataTypes: TransactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        resolvedRequest: ResolvedAuthorizationRequest = ResolvedAuthorizationRequest.WithRequestObject(
            authorizationRequest = request,
            requestObject = "authenticated.request.object",
        ),
    ): PresentationRequestValidationResult = PresentationRequestValidator.validate(
        resolvedRequest = resolvedRequest,
        transactionDataTypeRegistry = transactionDataTypes,
        formatCapabilities = p256Capabilities,
    )

    private fun request(
        clientId: String? = "did:jwk:verifier",
        responseType: OpenID4VPResponseType = OpenID4VPResponseType.VP_TOKEN,
        responseMode: OpenID4VPResponseMode = OpenID4VPResponseMode.DIRECT_POST,
        responseUri: String? = "https://verifier.example/response",
        redirectUri: String? = null,
        dcqlQuery: DcqlQuery? = DcqlQuery(credentials = listOf(credentialQuery())),
        scope: String? = null,
        clientMetadata: ClientMetadata? = null,
        transactionData: List<String>? = null,
    ) = AuthorizationRequest(
        clientId = clientId,
        responseType = responseType,
        responseMode = responseMode,
        responseUri = responseUri,
        redirectUri = redirectUri,
        nonce = "nonce",
        dcqlQuery = dcqlQuery,
        scope = scope,
        clientMetadata = clientMetadata,
        transactionData = transactionData,
    )

    private fun credentialQuery(
        format: CredentialFormat = CredentialFormat.DC_SD_JWT,
        id: String = "pid",
    ) = CredentialQuery(
        id = id,
        format = format,
        meta = NoMeta,
    )

    private fun transactionData(type: String): String = buildJsonObject {
        put("type", type)
        put("credential_ids", JsonArray(listOf(JsonPrimitive("pid"))))
    }.toString().encodeToByteArray().encodeToBase64Url()
}
