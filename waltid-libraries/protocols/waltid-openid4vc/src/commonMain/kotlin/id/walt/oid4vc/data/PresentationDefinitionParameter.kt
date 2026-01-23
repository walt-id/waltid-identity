package id.walt.oid4vc.data

import id.walt.oid4vc.data.dif.PresentationDefinition

@ConsistentCopyVisibility
data class PresentationDefinitionParameter private constructor(
    val presentationDefinition: PresentationDefinition?,
    val presentationDefinitionUri: String?,
    val presentationDefinitionScope: String?
) {
    companion object {
        fun fromPresentationDefinition(presentationDefinition: PresentationDefinition) =
            PresentationDefinitionParameter(presentationDefinition, null, null)

        fun fromPresentationDefinitionUri(presentationDefinitionUri: String) =
            PresentationDefinitionParameter(null, presentationDefinitionUri, null)

        fun fromPresentationDefinitionScope(presentationDefinitionScope: String) =
            PresentationDefinitionParameter(null, null, presentationDefinitionScope)
    }
}
