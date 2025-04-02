package id.walt.wallet

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.helpers.WaltidServices
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.http
import id.walt.w3c.PresentationBuilder
import id.walt.w3c.utils.VCFormat
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CoreWalletOpenId4VP {

    /* TODO: Move to OpenID4VP */
    suspend fun directPost(responseUri: String, tokenResponse: TokenResponse): HttpResponse {

        check(tokenResponse.vpToken != null) { "Missing vpToken" }
        check(tokenResponse.presentationSubmission != null) { "Missing PresentationSubmission" }

        val formParameters = Parameters.build {
            append("vp_token", tokenResponse.vpToken!!.toString())
            append("presentation_submission", tokenResponse.presentationSubmission!!.toJSONString())
            //append("response", "")
        }
        val res = http.submitForm(responseUri, formParameters)

        return res
    }

    /* FIXME: Make add PresentationSubmission handling to PresentationDefinition parser lib? */
    // FIXME: this function is not up to spec and should only be used until presentation submission handling is available by a core library
    fun makePresentationSubmissionForPresentationDefinition(
        presentationDefinition: PresentationDefinition,
        credentials: List<String>
    ): PresentationSubmission = PresentationSubmission(
        id = presentationDefinition.id,
        definitionId = presentationDefinition.id,
        descriptorMap = credentials.mapIndexed { idx, credentialString ->

            val credentialStringFormat = when {
                credentialString.startsWith("ey") -> when {
                    credentialString.contains("~") -> VCFormat.sd_jwt_vc
                    else -> VCFormat.jwt_vc_json
                }

                else -> VCFormat.mso_mdoc
            }
            val credentialType = when (credentialStringFormat) {
                VCFormat.jwt_vc_json -> credentialString.decodeJws().payload.let {
                    (it["type"]?.jsonArray ?: it["vc"]?.jsonObject["type"]?.jsonArray).let { it?.last()?.jsonPrimitive?.content }
                }

                VCFormat.sd_jwt_vc -> credentialString.decodeJws().payload["vct"]?.jsonPrimitive?.content
                VCFormat.mso_mdoc -> "mdoc"
                else -> TODO()
            }

            DescriptorMapping(
                id = credentialType,
                format = VCFormat.jwt_vp,
                path = "$",
                pathNested = DescriptorMapping(
                    id = credentialType,
                    format = credentialStringFormat,
                    path = "$.verifiableCredential[$idx]",
                )
                /*
            "id": "OpenBadgeCredential",
            "format": "jwt_vp",
            "path": "$",
            "path_nested": {
              "id": "OpenBadgeCredential",
              "format": "jwt_vc_json",
              "path": "$.verifiableCredential[0]",
              "customParameters": {}
            }
             */
            )
        }
    )

    suspend fun presentCredential(
        presentationRequestUrl: String,
        /**
         * The presentationRequest is provided to this callback.
         *
         * The callback thus has information available and can make a choice what credentials to present,
         * e.g. on the basis of the `presentationRequest.presentationDefinition` or a DCQL query.
         * Also, the callback manages the available credentials (e.g. have a user select with checkboxes in the UI what credentials to present).
         *
         * Finally, the callback will make a VP of the credentials it selected (signing with key + DID on its own).
         *
         * The `PresentationResult` is typically created like this: `PresentationResult(listOf(JsonPrimitive(vp)), presentationSubmission)`.
         */
        signVpCallback: suspend (presentationRequest: AuthorizationRequest) -> PresentationResult,
    ): HttpResponse {
        val presentationRequest = OpenID4VP.parsePresentationRequestFromUrl(presentationRequestUrl)

        // Determine flow details (implicit flow, with vp_token response type, same-device flow with "query" response mode)
        check(presentationRequest.responseType.contains(ResponseType.VpToken)) { TODO("Only supports VpToken responseType in current state") }
        //check(presentationRequest.responseMode == ResponseMode.query) { TODO("Only supports 'query' responseMode in current state") }

        // optional (code flow): code response (verifier <-- wallet), token endpoint (verifier -> wallet)

        val presentationResult = signVpCallback(presentationRequest)

        // Generate token response

        val tokenResponse = OpenID4VP.generatePresentationResponse(presentationResult)
        // Optional: respond to token request (code-flow, verifier <-- wallet), respond to authorization request (implicit flow, verifier <-- wallet)
        // Optional: post token response to response_uri of verifier (cross-device flow, verifier <-- wallet)

        val responseUri = presentationRequest.responseUri ?: tokenResponse.toRedirectUri(
            presentationRequest.redirectUri ?: error("Found no place to send presentation result"),
            presentationRequest.responseMode ?: ResponseMode.query
        )
        return directPost(responseUri, tokenResponse)
    }
}

suspend fun main() {
    WaltidServices.minimalInit()

    val x =
        "openid4vp<://authorize?response_type=vp_token&client_id=https%3A%2F%2Fverifier.demo.walt.id%2Fopenid4vc%2Fverify&response_mode=direct_post&state=YUjSglX1svU0&presentation_definition_uri=https%3A%2F%2Fverifier.demo.walt.id%2Fopenid4vc%2Fpd%2FYUjSglX1svU0&client_id_scheme=redirect_uri&client_metadata=%7B%22authorization_encrypted_response_alg%22%3A%22ECDH-ES%22%2C%22authorization_encrypted_response_enc%22%3A%22A256GCM%22%7D&nonce=663a9df4-f56f-40ca-920c-7d537b81a802&response_uri=https%3A%2F%2Fverifier.demo.walt.id%2Fopenid4vc%2Fverify%2FYUjSglX1svU0"

    val key = KeyManager.resolveSerializedKey(
        """
        {
            "type": "jwk",
            "jwk": {
              "kty": "OKP",
              "d": "f6tgikKGu0KpIYsHFK2KjtIBOC7CxPhAbXhBR00tddY",
              "crv": "Ed25519",
              "kid": "uE5o7su2m6fv38eGLbr5f6HH7tno0JQ7FvoNCe1_mIg",
              "x": "YCI1sj986SPFifj2V4fPHV-kQ4cW0nsnIGcYIehpy60"
            }
        }
        """
    )
    val holderdid = "did:key:z6MkkvXSTYa1ftiSa9ZYviaf3TEPHVyVP1VhB7Msju9Er7LG"
    val credentials = listOf(
        "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3B1cmwuaW1zZ2xvYmFsLm9yZy9zcGVjL29iL3YzcDAvY29udGV4dC5qc29uIl0sImlkIjoidXJuOnV1aWQ6Y2VhOGFmYjktN2YxMS00MTZiLTliYzUtNjI2NzAyNWM2NThkIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk9wZW5CYWRnZUNyZWRlbnRpYWwiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sIm5hbWUiOiJKb2JzIGZvciB0aGUgRnV0dXJlIChKRkYpIiwidXJsIjoiaHR0cHM6Ly93d3cuamZmLm9yZy8iLCJpbWFnZSI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMS0yMDIyL2ltYWdlcy9KRkZfTG9nb0xvY2t1cC5wbmciLCJpZCI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7InR5cGUiOlsiQWNoaWV2ZW1lbnRTdWJqZWN0Il0sImFjaGlldmVtZW50Ijp7ImlkIjoidXJuOnV1aWQ6YWMyNTRiZDUtOGZhZC00YmIxLTlkMjktZWZkOTM4NTM2OTI2IiwidHlwZSI6WyJBY2hpZXZlbWVudCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImRlc2NyaXB0aW9uIjoiVGhpcyB3YWxsZXQgc3VwcG9ydHMgdGhlIHVzZSBvZiBXM0MgVmVyaWZpYWJsZSBDcmVkZW50aWFscyBhbmQgaGFzIGRlbW9uc3RyYXRlZCBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cgZHVyaW5nIEpGRiB4IFZDLUVEVSBQbHVnRmVzdCAzLiIsImNyaXRlcmlhIjp7InR5cGUiOiJDcml0ZXJpYSIsIm5hcnJhdGl2ZSI6IldhbGxldCBzb2x1dGlvbnMgcHJvdmlkZXJzIGVhcm5lZCB0aGlzIGJhZGdlIGJ5IGRlbW9uc3RyYXRpbmcgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93LiBUaGlzIGluY2x1ZGVzIHN1Y2Nlc3NmdWxseSByZWNlaXZpbmcgYSBwcmVzZW50YXRpb24gcmVxdWVzdCwgYWxsb3dpbmcgdGhlIGhvbGRlciB0byBzZWxlY3QgYXQgbGVhc3QgdHdvIHR5cGVzIG9mIHZlcmlmaWFibGUgY3JlZGVudGlhbHMgdG8gY3JlYXRlIGEgdmVyaWZpYWJsZSBwcmVzZW50YXRpb24sIHJldHVybmluZyB0aGUgcHJlc2VudGF0aW9uIHRvIHRoZSByZXF1ZXN0b3IsIGFuZCBwYXNzaW5nIHZlcmlmaWNhdGlvbiBvZiB0aGUgcHJlc2VudGF0aW9uIGFuZCB0aGUgaW5jbHVkZWQgY3JlZGVudGlhbHMuIn0sImltYWdlIjp7ImlkIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0zLTIwMjMvaW1hZ2VzL0pGRi1WQy1FRFUtUExVR0ZFU1QzLWJhZGdlLWltYWdlLnBuZyIsInR5cGUiOiJJbWFnZSJ9fSwiaWQiOiJkaWQ6a2V5Ono2TWtrdlhTVFlhMWZ0aVNhOVpZdmlhZjNURVBIVnlWUDFWaEI3TXNqdTlFcjdMRyJ9LCJpc3N1YW5jZURhdGUiOiIyMDI1LTAzLTEyVDEwOjM5OjIwLjI5OTE4MjYxN1oiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDMtMTJUMTA6Mzk6MjAuMjk5MjQ1MDE3WiJ9LCJqdGkiOiJ1cm46dXVpZDpjZWE4YWZiOS03ZjExLTQxNmItOWJjNS02MjY3MDI1YzY1OGQiLCJleHAiOjE3NzMzMTE5NjAsImlhdCI6MTc0MTc3NTk2MCwibmJmIjoxNzQxNzc1OTYwfQ.5zPKjZszbC3rTbiHtYc78-C2yAOuzy2nP4-KneO_TkyEYqY78smupXVMOzyOAKbc7CNWeWlVPgeBUYN7yTE4DQ"
    )

    val callback: suspend (presentationRequest: AuthorizationRequest) -> PresentationResult = { presentationRequest ->
        val presentationDefinition = OpenID4VP.resolvePresentationDefinition(presentationRequest)

        println("In callback! PresentationRequest: $presentationRequest")
        val vp = PresentationBuilder().apply {
            did = holderdid
            nonce = presentationRequest.nonce
            credentials.forEach {
                addCredential(JsonPrimitive(it))
            }
        }.buildAndSign(key)


        val presentationSubmission = CoreWalletOpenId4VP.makePresentationSubmissionForPresentationDefinition(
            presentationDefinition = presentationDefinition, credentials = credentials
        )

        PresentationResult(listOf(JsonPrimitive(vp)), presentationSubmission)
    }

    CoreWalletOpenId4VP.presentCredential(x, callback)
}
