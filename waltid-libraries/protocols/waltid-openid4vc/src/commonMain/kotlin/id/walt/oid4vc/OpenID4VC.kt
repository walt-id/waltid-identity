package id.walt.oid4vc

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.mdoc.dataelement.MapElement
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.ResponseType.Companion.getResponseTypeString
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.errors.TokenVerificationError
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.COSESign1Utils
import id.walt.oid4vc.util.randomUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

object OpenID4VC {
  private val log = KotlinLogging.logger { }

  suspend fun generateToken(sub: String, issuer: String, audience: TokenTarget, tokenId: String? = null, tokenKey: Key): String {
    return signToken(audience, buildJsonObject {
      put(JWTClaims.Payload.subject, sub)
      put(JWTClaims.Payload.issuer, issuer)
      put(JWTClaims.Payload.audience, audience.name)
      tokenId?.let { put(JWTClaims.Payload.jwtID, it) }
    }, tokenKey)
  }

  suspend fun verifyAndParseToken(token: String, issuer: String, target: TokenTarget, tokenKey: Key? = null): JsonObject? {
    if (verifyTokenSignature(target, token, tokenKey)) {
      val payload = parseTokenPayload(token)
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

  suspend fun verifyAndParseIdToken(token: String, tokenKey: Key? = null): JsonObject {
    // 1. Validate Header
    val header = parseTokenHeader(token)
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
    val payload = parseTokenPayload(token)
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
      log.debug { "$sub $iss $did" }
      throw IllegalArgumentException("Invalid payload in token. sub != iss != did")
    }

    // 4. Verify Signature
    if (!verifyTokenSignature(TokenTarget.TOKEN, token, tokenKey))
      throw IllegalArgumentException("Invalid token - cannot verify signature")

    return payload
  }


  suspend fun generateAuthorizationCodeFor(sessionId: String, issuer: String, tokenKey: Key): String {
    return generateToken(sessionId, issuer, TokenTarget.TOKEN, null, tokenKey)
  }

  suspend fun validateAndParseTokenRequest(tokenRequest: TokenRequest, issuer: String, tokenKey: Key? = null): JsonObject {
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
    return verifyAndParseToken(code, issuer, TokenTarget.TOKEN, tokenKey) ?: throw TokenError(
      tokenRequest = tokenRequest,
      errorCode = TokenErrorCode.invalid_grant,
      message = "Authorization code could not be verified"
    )
  }

  // Create an ID or VP Token request using JAR OAuth2.0 specification https://www.rfc-editor.org/rfc/rfc9101.html
  suspend fun processCodeFlowAuthorizationWithAuthorizationRequest(
    authorizationRequest: AuthorizationRequest,
    responseType: ResponseType,
    providerMetadata: OpenIDProviderMetadata,
    tokenKey: Key,
    isJar: Boolean? = true,
    presentationDefinition: PresentationDefinition? = null,
  ): AuthorizationCodeWithAuthorizationRequestResponse {

    providerMetadata as OpenIDProviderMetadata.Draft13

    if (!authorizationRequest.responseType.contains(ResponseType.Code))
      throw AuthorizationError(
        authorizationRequest,
        AuthorizationErrorCode.invalid_request,
        message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
      )

    // Bind authentication request with state
    val authorizationRequestServerState = randomUUID()
    val authorizationRequestServerNonce = randomUUID()
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
        true -> signToken(
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
          }, tokenKey)

        else -> null
      },
      presentationDefinition = when (responseType) {
        ResponseType.VpToken -> presentationDefinition!!.toJSONString()
        else -> null
      }
    )
  }

  suspend fun processCodeFlowAuthorization(authorizationRequest: AuthorizationRequest, sessionId: String, providerMetadata: OpenIDProviderMetadata, tokenKey: Key): AuthorizationCodeResponse {
    if (!authorizationRequest.responseType.contains(ResponseType.Code))
      throw AuthorizationError(
        authorizationRequest,
        AuthorizationErrorCode.invalid_request,
        message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
      )

    providerMetadata as OpenIDProviderMetadata.Draft13

    val issuer = providerMetadata.issuer ?: throw AuthorizationError(authorizationRequest, AuthorizationErrorCode.server_error,"No issuer configured in given provider metadata")
    val code = generateAuthorizationCodeFor(sessionId, issuer, tokenKey)
    return AuthorizationCodeResponse.success(code, mapOf("state" to listOf(authorizationRequest.state ?: randomUUID())))
  }

  suspend fun processImplicitFlowAuthorization(authorizationRequest: AuthorizationRequest, sessionId: String, providerMetadata: OpenIDProviderMetadata, tokenKey: Key): TokenResponse {
    providerMetadata as OpenIDProviderMetadata.Draft13

    log.debug { "> processImplicitFlowAuthorization for $authorizationRequest" }
    if (!authorizationRequest.responseType.contains(ResponseType.Token) && !authorizationRequest.responseType.contains(ResponseType.VpToken)
      && !authorizationRequest.responseType.contains(ResponseType.IdToken)
    )
      throw AuthorizationError(
        authorizationRequest,
        AuthorizationErrorCode.invalid_request,
        message = "Invalid response type ${authorizationRequest.responseType}, for implicit authorization flow."
      )
    log.debug { "> processImplicitFlowAuthorization: generateTokenResponse..." }
    val issuer = providerMetadata.issuer ?: throw AuthorizationError(authorizationRequest, AuthorizationErrorCode.server_error,"No issuer configured in given provider metadata")
    return TokenResponse.success(
      generateToken(sessionId, issuer, TokenTarget.ACCESS, null, tokenKey),
      "bearer", state = authorizationRequest.state,
      expiresIn = Clock.System.now().epochSeconds + 864000L // ten days in seconds
    )
  }

  suspend fun processDirectPost(authorizationRequest: AuthorizationRequest, sessionId: String, providerMetadata: OpenIDProviderMetadata, tokenKey: Key): AuthorizationCodeResponse {
    providerMetadata as OpenIDProviderMetadata.Draft13

    // Verify nonce - need to add Id token nonce session
    // if (payload[JWTClaims.Payload.nonce] != session.)

    // Generate code and proceed as regular authorization request
    val mappedState = mapOf("state" to listOf(authorizationRequest.state!!))
    val issuer = providerMetadata.issuer ?: throw AuthorizationError(authorizationRequest, AuthorizationErrorCode.server_error,"No issuer configured in given provider metadata")
    val code = generateAuthorizationCodeFor(sessionId, issuer, tokenKey)

    return AuthorizationCodeResponse.success(code, mappedState)
  }

  const val PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"
  fun getPushedAuthorizationRequestUri(sessionId: String): String = "$PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX${sessionId}"
  fun getPushedAuthorizationSessionId(requestUri: String): String = requestUri.substringAfter(
    PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX)

  // ------------------------------------------
  // Simple cryptographics operation interface implementations
  suspend fun signToken(
    target: TokenTarget,
    payload: JsonObject,
    privKey: Key,
    header: JsonObject? = null) : String
  {
    val keyId = privKey.getKeyId()
    log.debug { "Signing JWS:   $payload" }
    log.debug { "JWS Signature: target: $target, keyId: $keyId, header: $header" }

    val headers = (header?.toMutableMap() ?: mutableMapOf())
      .plus(mapOf("alg" to "ES256".toJsonElement(), "type" to "jwt".toJsonElement(), "kid" to keyId.toJsonElement()))

    return privKey.signJws(payload.toString().toByteArray(), headers).also {
      log.debug { "Signed JWS: >> $it" }
    }
  }

  suspend fun signCWTToken(
    target: TokenTarget,
    payload: MapElement,
    privKey: Key,
    header: MapElement? = null,
  ): String {
    TODO("Not yet implemented, may not be required anymore (removed from https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#cwt-proof-type)")
  }

  suspend fun verifyTokenSignature(target: TokenTarget, token: String, tokenKey: Key? = null): Boolean {
    log.debug { "Verifying JWS: $token" }
    log.debug { "JWS Verification: target: $target" }

    val tokenHeader = Json.parseToJsonElement(token.split(".")[0].base64UrlDecode().decodeToString()).jsonObject
    val key = (if (tokenHeader["jwk"] != null) {
      JWKKey.importJWK(tokenHeader["jwk"].toString()).getOrThrow()
    } else if (tokenHeader["kid"] != null) {
      val kid = tokenHeader["kid"]!!.jsonPrimitive.content.split("#")[0]
      if(DidUtils.isDidUrl(kid)) {
        log.debug { "Resolving DID: $kid" }
        DidService.resolveToKey(kid).getOrThrow()
      } else if(tokenKey != null && kid.equals(tokenKey.getKeyId())) {
        tokenKey
      } else null
    } else tokenKey) ?: throw TokenVerificationError(token, target, "Could not resolve key for given token")
    return key.verifyJws(token).also { log.debug { "VERIFICATION IS: $it" } }.isSuccess
  }

  @OptIn(ExperimentalSerializationApi::class)
  suspend fun verifyCOSESign1Signature(target: TokenTarget, token: String): Boolean {
    // May not be required anymore (removed from https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#cwt-proof-type)
    log.debug { "Verifying JWS: $token" }
    log.debug { "JWS Verification: target: $target" }
    // requires currently JVM specific implementation for COSE_Sign1 signature verification
    return COSESign1Utils.verifyCOSESign1Signature(target, token)
  }

  fun parseTokenPayload(token: String): JsonObject {
    return token.substringAfter(".").substringBefore(".").let {
      Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
    }
  }

  fun parseTokenHeader(token: String): JsonObject {
    return token.substringBefore(".").let {
      Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
    }
  }
}
