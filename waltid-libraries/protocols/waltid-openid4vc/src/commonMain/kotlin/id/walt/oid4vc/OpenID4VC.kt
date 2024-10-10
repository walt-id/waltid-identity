package id.walt.oid4vc

import id.walt.crypto.keys.Key
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.ResponseType.Companion.getResponseTypeString
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.interfaces.ITokenProvider
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.AuthorizationCodeWithAuthorizationRequestResponse
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.oid4vc.util.randomUUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object OpenID4VC {
  fun generateToken(tokenProvider: ITokenProvider, sub: String, issuer: String, audience: TokenTarget, tokenId: String? = null): String {
    return tokenProvider.signToken(audience, buildJsonObject {
      put(JWTClaims.Payload.subject, sub)
      put(JWTClaims.Payload.issuer, issuer)
      put(JWTClaims.Payload.audience, audience.name)
      tokenId?.let { put(JWTClaims.Payload.jwtID, it) }
    })
  }

  fun verifyAndParseToken(tokenProvider: ITokenProvider, token: String, issuer: String, target: TokenTarget): JsonObject? {
    if (tokenProvider.verifyTokenSignature(target, token)) {
      val payload = tokenProvider.parseTokenPayload(token)
      if (payload.keys.containsAll(
          setOf(
            JWTClaims.Payload.subject,
            JWTClaims.Payload.audience,
            JWTClaims.Payload.issuer
          )
        ) &&
        payload[JWTClaims.Payload.audience]!!.jsonPrimitive.content == target.name &&
        payload[JWTClaims.Payload.issuer]!!.jsonPrimitive.content == issuer
      ) {
        return payload
      }
    }
    return null
  }

  fun verifyAndParseIdToken(tokenProvider: ITokenProvider, token: String): JsonObject? {
    // 1. Validate Header
    val header = tokenProvider.parseTokenHeader(token)
    if (!header.keys.containsAll(
        setOf(
          JWTClaims.Header.type,
          JWTClaims.Header.keyID,
          JWTClaims.Header.algorithm,
        )
      )
    ) {
      throw IllegalStateException("Invalid header in token")
    }

    // 2. Validate Payload
    val payload = tokenProvider.parseTokenPayload(token)
    if (!payload.keys.containsAll(
        setOf(
          JWTClaims.Payload.issuer,
          JWTClaims.Payload.subject,
          JWTClaims.Payload.audience,
          JWTClaims.Payload.expirationTime,
          JWTClaims.Payload.issuedAtTime,
          JWTClaims.Payload.nonce,
        )
      )
    ) {
      throw IllegalArgumentException("Invalid payload in token")
    }

    // 3. Verify iss = sub = did
    val sub = payload[JWTClaims.Payload.subject]!!.jsonPrimitive.content
    val iss = payload[JWTClaims.Payload.issuer]!!.jsonPrimitive.content
    val kid = header[JWTClaims.Header.keyID]!!.jsonPrimitive.content
    val did = kid.substringBefore("#")

    if (iss != sub || iss != did || sub != did) {
      println("$sub $iss $did")
      throw IllegalArgumentException("Invalid payload in token. sub != iss != did")
    }

    // 4. Verify Signature

    // 4.a Resolve DID
    // DidService.minimalInit()
    // val didDocument = DidService.resolve(did.removeSurrounding("\"")
    //)

    // 4.b Get verification methods from DID Document
    // val verificationMethods = didDocument.getOrNull()?.get("verificationMethod")

    // 4.c Get the corresponding verification method
    // val verificationMethod = verificationMethods?.jsonArray?.firstOrNull {
    //    it.jsonObject["id"].toString() == kidFull.toString()
    //} ?: throw IllegalArgumentException("Invalid verification method")

    // 4.d Verify Token
    // val key = DidService.resolveToKey(did).getOrThrow()
    // key.verifyJws(token).isSuccess

    if (!tokenProvider.verifyTokenSignature(TokenTarget.TOKEN, token))
      throw IllegalArgumentException("Invalid token - cannot verify signature")

    return payload
  }


  fun generateAuthorizationCodeFor(tokenProvider: ITokenProvider, sessionId: String, issuer: String): String {
    return generateToken(tokenProvider, sessionId, issuer, TokenTarget.TOKEN)
  }

  fun validateAndParseTokenRequest(tokenProvider: ITokenProvider, tokenRequest: TokenRequest, issuer: String): JsonObject? {
    val code = when (tokenRequest.grantType) {
      GrantType.authorization_code -> tokenRequest.code ?: throw TokenError(
        tokenRequest = tokenRequest,
        errorCode = TokenErrorCode.invalid_grant,
        message = "No code parameter found on token request"
      )

      GrantType.pre_authorized_code -> tokenRequest.preAuthorizedCode ?: throw TokenError(
        tokenRequest = tokenRequest,
        errorCode = TokenErrorCode.invalid_grant,
        message = "No pre-authorized_code parameter found on token request"
      )

      else -> throw TokenError(tokenRequest, TokenErrorCode.unsupported_grant_type, "Grant type not supported")
    }
    return verifyAndParseToken(tokenProvider, code, issuer, TokenTarget.TOKEN) ?: throw TokenError(
      tokenRequest = tokenRequest,
      errorCode = TokenErrorCode.invalid_grant,
      message = "Authorization code could not be verified"
    )
  }

  // Create an ID or VP Token request using JAR OAuth2.0 specification https://www.rfc-editor.org/rfc/rfc9101.html
  @OptIn(ExperimentalUuidApi::class)
  fun processCodeFlowAuthorizationWithAuthorizationRequest(
    tokenProvider: ITokenProvider,
    authorizationRequest: AuthorizationRequest,
    responseType: ResponseType,
    providerMetadata: OpenIDProviderMetadata,
    isJar: Boolean? = true,
    presentationDefinition: PresentationDefinition? = null,
  ): AuthorizationCodeWithAuthorizationRequestResponse {
    if (!authorizationRequest.responseType.contains(ResponseType.Code))
      throw AuthorizationError(
        authorizationRequest,
        AuthorizationErrorCode.invalid_request,
        message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
      )

    // Bind authentication request with state
    val authorizationRequestServerState = Uuid.random().toString()
    val authorizationRequestServerNonce = Uuid.random().toString()
    val authorizationResponseServerMode = ResponseMode.direct_post

    val clientId = providerMetadata.issuer!!
    val redirectUri = providerMetadata.issuer + "/direct_post"
    val scope = setOf("openid")

    // Create a session with the state of the ID Token request since it is needed in the direct_post endpoint
    //initializeAuthorization(authorizationRequest, 5.minutes, authorizationRequestServerState)

    return AuthorizationCodeWithAuthorizationRequestResponse.success(
      state = authorizationRequestServerState,
      clientId = clientId,
      redirectUri = redirectUri,
      responseType = getResponseTypeString(responseType),
      responseMode = authorizationResponseServerMode,
      scope = scope,
      nonce = authorizationRequestServerNonce,
      requestUri = null,
      request = when (isJar) {
        // Create a jwt as request object as defined in JAR OAuth2.0 specification
        true -> tokenProvider.signToken(
          TokenTarget.TOKEN,
          buildJsonObject {
            put(JWTClaims.Payload.issuer, providerMetadata.issuer)
            put(JWTClaims.Payload.audience, authorizationRequest.clientId)
            put(JWTClaims.Payload.nonce, authorizationRequestServerNonce)
            put("state", authorizationRequestServerState)
            put("client_id", clientId)
            put("redirect_uri", redirectUri)
            put("response_type", getResponseTypeString(responseType))
            put("response_mode", authorizationResponseServerMode.name)
            put("scope", "openid")
            when (responseType) {
              ResponseType.VpToken -> put("presentation_definition", presentationDefinition!!.toJSON())
              else -> null
            }
          }
//          }, buildJsonObject {
//            put(JWTClaims.Header.algorithm, "ES256")
//            put(JWTClaims.Header.keyID, keyId)
//            put(JWTClaims.Header.type, "jwt")
//          },
//          privKey?.getKeyId(),
//          privKey
        )

        else -> null
      },
      presentationDefinition = when (responseType) {
        ResponseType.VpToken -> presentationDefinition!!.toJSONString()
        else -> null
      }
    )
  }

  fun processCodeFlowAuthorization(authorizationRequest: AuthorizationRequest): AuthorizationCodeResponse {
    if (!authorizationRequest.responseType.contains(ResponseType.Code))
      throw AuthorizationError(
        authorizationRequest,
        AuthorizationErrorCode.invalid_request,
        message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
      )
    val authorizationSession = getOrInitAuthorizationSession(authorizationRequest)
    val code = generateAuthorizationCodeFor(authorizationSession)
    return AuthorizationCodeResponse.success(code, mapOf("state" to listOf(authorizationRequest.state ?: randomUUID())))
  }
}
