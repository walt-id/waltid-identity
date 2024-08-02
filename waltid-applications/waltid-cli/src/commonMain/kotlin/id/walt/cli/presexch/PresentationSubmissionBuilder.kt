package id.walt.cli.presexch

import id.walt.cli.models.Credential
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.oid4vc.data.dif.*
import kotlinx.serialization.json.*

class PresentationSubmissionBuilder(
    private val presentationDefinition: PresentationDefinition,
    private val qualifiedVcList: List<Credential>,
) {
    fun buildToString() = Json.encodeToString(
        PresentationSubmissionSerializer,
        PresentationSubmission(
            id = presentationDefinition.id,
            definitionId = presentationDefinition.id,
            descriptorMap = qualifiedVcList.map { it.serializedCredential }.mapIndexed { index, vcJwsStr ->
                buildDescriptorMapping(presentationDefinition, index, vcJwsStr)
            }
        )
    )

    private fun buildDescriptorMapping(
        presentationDefinition: PresentationDefinition,
        index: Int,
        vcJwsStr: String
    ) = let {
        val vcJws = vcJwsStr.decodeJws()
        val type = vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            ?: "VerifiableCredential"

        DescriptorMapping(
            id = getDescriptorId(
                type,
                presentationDefinition
            ),
            format = VCFormat.jwt_vp_json,
            path = "$",
            pathNested = DescriptorMapping(
                id = getDescriptorId(
                    type,
                    presentationDefinition
                ),
                format = VCFormat.jwt_vc_json,
                path = "$.verifiableCredential[$index]",
            )
        )
    }

    private fun getDescriptorId(type: String, presentationDefinition: PresentationDefinition?) =
        presentationDefinition?.inputDescriptors?.find {
            (it.name ?: it.id) == type
        }?.id
}