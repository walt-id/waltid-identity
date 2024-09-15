package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.models.service.ServiceEndpoint
import id.walt.did.dids.document.models.verification.method.VerificationMaterialType
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class DidDocConfig(
    val keyMap: Map<String, Key>,
    val verificationConfigurationMap: Map<VerificationRelationshipType, VerificationConfiguration>,
    val serviceConfigurationSet: Set<ServiceConfiguration>,
    val rootCustomProperties: Map<String, JsonElement>? = null,
)

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class VerificationConfiguration(
    val keyId: String,
    val materialType: VerificationMaterialType = VerificationMaterialType.PublicKeyJwk,
    val customProperties: Map<String, JsonElement>? = null,
)

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class ServiceConfiguration(
    val type: Set<String>,
    val serviceEndpoint: Set<ServiceEndpoint>,
    val customProperties: Map<String, JsonElement>? = null,
)
