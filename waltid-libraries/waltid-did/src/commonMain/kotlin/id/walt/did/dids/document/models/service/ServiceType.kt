package id.walt.did.dids.document.models.service

//as defined in https://www.w3.org/TR/2024/NOTE-did-spec-registries-20240830/#service-types
enum class ServiceType {
    LinkedDomains,
    LinkedVerifiablePresentation,
    DIDCommMessaging,
    WotThing,
    CredentialRegistry,
    OID4VCI,
    OID4VP,
}
