package id.walt.did.dids.document.models.service

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

//as defined in https://www.w3.org/TR/2024/NOTE-did-spec-registries-20240830/#service-types
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
enum class RegisteredServiceType {
    LinkedDomains,
    LinkedVerifiablePresentation,
    DIDCommMessaging,
    WotThing,
    CredentialRegistry,
    OID4VCI,
    OID4VP,
}
