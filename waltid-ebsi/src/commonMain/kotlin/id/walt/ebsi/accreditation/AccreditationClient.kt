package id.walt.ebsi.accreditation

import id.walt.crypto.keys.Key
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.http
import id.walt.oid4vc.util.randomUUID
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AccreditationClient(
  val clientUri: String,
  val did: String,
  val authenticationKey: Key,
  val trustedIssuer: String,
  val clientJwksUri: String = "$clientUri/jwks",
  val clientRedirectUri: String = "$clientUri/code-cb",
  val clientId: String = clientUri
) {

  protected suspend fun getIdTokenFor(idTokenRequest: AuthorizationRequest, authorisationServer: String): String {
    return authenticationKey.signJws(
      buildJsonObject {
        put("iss", did)
        put("sub", did)
        put("aud", authorisationServer)
        put("exp", Instant.DISTANT_FUTURE.epochSeconds)
        put("iat", Clock.System.now().epochSeconds)
        put("state", idTokenRequest.state)
        put("nonce", idTokenRequest.nonce)
      }.toString().toByteArray(),
      mapOf("kid" to "$did#${authenticationKey.getKeyId()}", "typ" to "JWT")
    )
  }

  suspend fun getTrustedCredential(credentialType: String): CredentialResponse {
    // https://hub.ebsi.eu/conformance/build-solutions/accredit-and-authorise-functional-flows

    // #### Discovery ####
    val ciMetadata = OpenID4VCI.resolveCIProviderMetadata(trustedIssuer)
    val authorisationServer = ciMetadata.authorizationServer ?: throw AccreditationException("No authorization server found for given trusted issuer")
    val credentialMetadata = ciMetadata.credentialsSupported?.find { it.types?.last()?.equals(credentialType) ?: false } ?: throw AccreditationException("Credential type not supported by given trusted issuer")
    val authMetadata = OpenID4VCI.resolveAuthProviderMetadata(authorisationServer)

    // #### Authorise and Authenticate ####
    val codeChallenge = randomUUID()
    val authReq = AuthorizationRequest(
      responseType = setOf(ResponseType.Code), clientId,
      scope = setOf("openid"),
      redirectUri = clientRedirectUri,
      authorizationDetails = listOf(
        AuthorizationDetails.fromLegacyCredentialParameters(
          CredentialFormat.jwt_vc,
          credentialMetadata.types!!,
          locations = listOf(ciMetadata.credentialIssuer!!)
        )
      ),
      clientMetadata = OpenIDClientMetadata(customParameters = mapOf(
        "jwks_uri" to JsonPrimitive(clientJwksUri),
        "authorization_endpoint" to JsonPrimitive("openid:")
      )),
      codeChallenge = codeChallenge, codeChallengeMethod = "plain"
    )

    val signedRequestObject = authenticationKey.signJws(
      authReq.toRequestObjectPayload(clientId, authMetadata.issuer!!).toString().toByteArray(),
      mapOf("kid" to authenticationKey.getKeyId())
    )

    val httpResp = http.get(authMetadata.authorizationEndpoint!!) {
      url { parameters.appendAll(parametersOf(authReq.toHttpParametersWithRequestObject(signedRequestObject)))
        println(buildString())
      }
    }
    val idTokenReqUrl = httpResp.headers["location"]!!
    val idTokenReq = AuthorizationRequest.fromHttpParametersAuto(Url(idTokenReqUrl).parameters.toMap())

    if(ResponseMode.direct_post != idTokenReq.responseMode || !idTokenReq.scope.contains("openid") ||
        !idTokenReq.responseType.contains(ResponseType.IdToken) || idTokenReq.redirectUri.isNullOrEmpty()) {
      throw AccreditationException("Invalid id_token request received")
    }

    val idToken = getIdTokenFor(idTokenReq, authorisationServer)
    val authResp = http.submitForm(idTokenReq.redirectUri!!, parametersOf(
      TokenResponse.success(idToken = idToken, state = idTokenReq.state).toHttpParameters()))
    if(authResp.status.value != 302) throw AccreditationException("Invalid auth response status")
    val codeResp = AuthorizationCodeResponse.fromHttpQueryString(Url(authResp.headers["Location"]!!).encodedQuery)

    val tokenReq = TokenRequest(
      GrantType.authorization_code,
      clientId, clientRedirectUri,
      codeResp.code,
      codeVerifier = codeChallenge,
      customParameters = mapOf(
        "client_assertion" to listOf(authenticationKey.signJws(
          buildJsonObject {
            put("iss", clientUri)
            put("sub", clientUri)
            put("aud", authMetadata.issuer)
            put("jti", randomUUID())
            put("exp", Instant.DISTANT_FUTURE.epochSeconds)
            put("iat", Clock.System.now().epochSeconds)
          }.toString().toByteArray(),
          mapOf("kid" to authenticationKey.getKeyId(), "typ" to "JWT")),
        ),
        "client_assertion_type" to listOf("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
      )
    )
    val tokenRespRaw = http.submitForm(authMetadata.tokenEndpoint!!, parametersOf(tokenReq.toHttpParameters()))
    if(tokenRespRaw.status != HttpStatusCode.OK) throw AccreditationException("Invalid token response status")
    val tokenResp = TokenResponse.fromJSONString(tokenRespRaw.bodyAsText())
    val accessToken = tokenResp.accessToken ?: throw AccreditationException("No access_token received")

    val jwtProof = ProofOfPossession.JWTProofBuilder(
      ciMetadata.credentialIssuer!!,
      clientId, tokenResp.cNonce,
      "$did#${authenticationKey.getKeyId()}"
    ).build(authenticationKey)

    // #### Credential Issuance ####
    val credReq = CredentialRequest(
      CredentialFormat.jwt_vc,
      proof = jwtProof,
      types = credentialMetadata.types!!
    )
    val credRespRaw = http.post(ciMetadata.credentialEndpoint!!) {
      bearerAuth(accessToken!!)
      contentType(ContentType.Application.Json)
      setBody(credReq.toJSONString())
    }
    println(credRespRaw.bodyAsText())
    if(credRespRaw.status != HttpStatusCode.OK) throw AccreditationException("Invalid credential response status")
    return CredentialResponse.fromJSONString(credRespRaw.bodyAsText())
  }

  suspend fun getAuthorisationToOnboard() = getTrustedCredential("VerifiableAuthorisationToOnboard")
}
