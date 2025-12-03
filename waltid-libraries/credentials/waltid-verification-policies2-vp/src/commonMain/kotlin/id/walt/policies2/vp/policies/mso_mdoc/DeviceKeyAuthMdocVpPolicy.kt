@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "mso_mdoc/device_key_auth"

@Serializable
@SerialName(policyId)
class DeviceKeyAuthMdocVpPolicy : MdocVPPolicy() {

    override val id = policyId
    override val description = "Verify holder-verified data"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext
    ): Result<Unit> {
        log.trace { "--- MDOC DATA - HOLDER VERIFIED DATA ---" }

        val deviceSignedNamespaces = document.deviceSigned?.namespaces?.value?.entries
        if (deviceSignedNamespaces == null) {
            log.trace { "No holder-verified data in this mdoc (no namespaces in DeviceSigned)." }
            addResult("no_device_signed_namespaces", true)
        } else {
            if (deviceSignedNamespaces.isEmpty()) {
                log.trace { "Namespace list in DeviceSigned exists, but is empty." }
                addResult("empty_device_signed_namespaces", true)
            } else {
                val keyAuthorization = mso.deviceKeyInfo.keyAuthorizations
                    ?: throw IllegalArgumentException("Found holder-verified data, but KeyAuthorization is fully missing")

                keyAuthorization.namespaces?.forEach {
                    log.trace { "KeyAuthorization authorizes namespaces: $it" }
                }
                if (keyAuthorization.namespaces != null) {
                    addResult("authorized_namespaces", keyAuthorization.namespaces!!)
                }

                keyAuthorization.dataElements?.forEach { (namespace, elementIdentifiers) ->
                    log.trace { "KeyAuthorization data elements: $namespace - $elementIdentifiers" }
                }
                if (keyAuthorization.dataElements != null) {
                    addResult("authorized_data_elements", keyAuthorization.dataElements!!)
                }

                deviceSignedNamespaces.entries.forEach { (namespace, deviceSignedItems) ->
                    val isNamespaceFullAuthorized = keyAuthorization.namespaces?.contains(namespace) == true
                    log.trace { "Is full namespace authorized for $namespace: $isNamespaceFullAuthorized" }

                    deviceSignedItems.entries.forEach { deviceSignedItem ->
                        val elementIdentifier = deviceSignedItem.key

                        val isElementAuthorized = keyAuthorization.dataElements?.get(namespace)?.contains(elementIdentifier) == true
                        val elementValue = deviceSignedItem.value

                        log.trace { "  $namespace - $elementIdentifier -> $elementValue (${elementValue::class.simpleName})" }

                        require(isNamespaceFullAuthorized || isElementAuthorized) { "The holder-verified data $namespace - $elementIdentifier is not authorized in KeyAuthorization" }
                        addHashListResult("allowed_data_elements", namespace, elementIdentifier)
                    }
                }
            }
        }

        return success()
    }
}
