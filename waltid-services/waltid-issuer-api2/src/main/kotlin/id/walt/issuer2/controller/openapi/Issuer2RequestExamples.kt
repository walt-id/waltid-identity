package id.walt.issuer2.controller.openapi

import id.walt.issuer2.controller.dto.CreateCredentialOfferRequest
import id.walt.issuer2.controller.dto.CreateCredentialOfferResponse
import id.walt.issuer2.controller.dto.CredentialOfferDeliveryMode
import id.walt.issuer2.controller.dto.CredentialOfferRuntimeOverrides
import id.walt.issuer2.domain.AuthenticationMethod
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Issuer2RequestExamples {
    val PRE_AUTHORIZED_CREDENTIAL_OFFER = CreateCredentialOfferRequest(
        profileId = "universityDegree",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        deliveryMode = CredentialOfferDeliveryMode.BY_REFERENCE,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            subjectId = "did:key:z6MkiY...",
            credentialData = buildJsonObject {
                put("givenName", "Jane")
                put("familyName", "Doe")
                put("degree", "Computer Science")
            },
        ),
    )

    val AUTHORIZATION_CODE_CREDENTIAL_OFFER = CreateCredentialOfferRequest(
        profileId = "universityDegree",
        authenticationMethod = AuthenticationMethod.AUTHORIZATION_CODE,
        deliveryMode = CredentialOfferDeliveryMode.BY_REFERENCE,
    )

    val CREDENTIAL_OFFER_RESPONSE = CreateCredentialOfferResponse(
        sessionId = "018f8d6e-8df4-7b73-9f3d-f3df21a4374a",
        profileId = "universityDegree",
        profileVersion = 1,
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        expiresAt = 1_739_000_000_000,
        credentialOfferUri = "openid-credential-offer://?credential_offer_uri=http%3A%2F%2Flocalhost%3A7003%2Fopenid4vci%2Fcredential-offer%3Fid%3D018f8d6e-8df4-7b73-9f3d-f3df21a4374a",
    )
}