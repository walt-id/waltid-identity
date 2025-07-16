package id.walt.verifier.openid

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.credentials.CredentialFormat
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import kotlinx.serialization.json.*

/*object WaltidVerifier {

    fun createVerificationSession() {

        val initalAuthorizationRequest = AuthorizationRequest(
            requestUri = "/auth-req/f47ac10b-58cc-4372-a567-0e02b2c3d479",
            clientId = "see clientid/readme.md",

            nonce = null, // not required in the initial request yet
            responseType = null,
        )

        println(initalAuthorizationRequest.toHttpUrl().toString())

        val hostedAuthorizationRequest = AuthorizationRequest(
            responseType = OpenID4VPResponseType.VP_TOKEN,
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            responseUri = "https://api.proprofile.example.com/openid4vp/presentation_response",
            clientId = "see clientid/readme.md",
            nonce = "a7b3k9zPqR2sT5uV",
            state = "session_verifier_badge_add_112233",
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "conference_badge_2025",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = Json.encodeToJsonElement(
                            W3cCredentialMeta(
                                typeValues = listOf(
                                    listOf("VerifiableCredential", "OpenBadgeCredential")
                                )
                            )
                        ).jsonObject,
                        claims = listOf(
                            ClaimsQuery(path = listOf("credentialSubject", "name")),// Name of the badge
                            ClaimsQuery(path = listOf("credentialSubject", "description")),// Name of the badge
                            ClaimsQuery(path = listOf("issuer", "name")),//Name of the badge issuer
                        )
                    )
                )
            ),
            clientMetadata = ClientMetadata(
                clientName = "Badge Verifier",
                logoUri = "https://xyz.example/logo.png",
                vpFormatsSupported = mapOf(
                    "jwt_vc_json" to JsonObject(mapOf("alg_values" to JsonArray(listOf(JsonPrimitive("ES256K"), JsonPrimitive("EdDSA")))))
                )
            )
        )

        println(Json.encodeToString(hostedAuthorizationRequest))

        *//*VerificationSession(
            id = "id",
            authorizationRequest =
        )*//*

    }

}

fun main() {
    WaltidVerifier.createVerificationSession()
}*/
