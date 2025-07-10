package id.walt.cli.presexch

import id.walt.cli.models.Credential
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PresentationSubmissionBuilder(
    private val presentationDefinition: PresentationDefinition,
    private val qualifiedVcList: List<Credential>,
) {
    fun buildToString(): String =
        PresentationSubmission(
            id = presentationDefinition.id,
            definitionId = presentationDefinition.id,
            descriptorMap = qualifiedVcList.map { it.serializedCredential }.mapIndexed { index, vcJwsStr ->
                buildDescriptorMapping(presentationDefinition, index, vcJwsStr)
            }
        ).toJSONString()

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
