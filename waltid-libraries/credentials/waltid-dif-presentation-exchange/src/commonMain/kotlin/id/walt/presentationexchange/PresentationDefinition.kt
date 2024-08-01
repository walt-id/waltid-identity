package id.walt.presentationexchange

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class PresentationDefinition(
    val id: String,
    val name: String? = null,
    val purpose: String? = null,
    val format: JsonObject? = null,
    val frame: Map<String, JsonElement>? = null,
    @SerialName("submission_requirements")
    val submissionRequirements: List<SubmissionRequirement>? = null,
    @SerialName("input_descriptors")
    val inputDescriptors: List<InputDescriptor>
) {
    @Serializable
    data class InputDescriptor(
        val id: String,
        val name: String? = null,
        val purpose: String? = null,
        val format: JsonElement? = null,
        val group: List<String>? = null,
        val constraints: Constraints
    ) {
        @Suppress("EnumEntryName")
        enum class Directive {
            required,
            preferred,
            disallowed
        }

        @Serializable
        data class Constraints(
            @SerialName("limit_disclosure")
            val limitDisclosure: Directive? = null,
            val statuses: Statuses? = null,
            val fields: List<Field>? = null,
            @SerialName("subject_is_issuer")
            val subjectIsIssuer: String? = null,
            @SerialName("is_holder")
            val isHolder: List<Subject>? = null,
            @SerialName("same_subject")
            val sameSubject: List<Subject>? = null
        ) {


            @Serializable
            data class Statuses(
                val active: StatusDirective? = null,
                val suspended: StatusDirective? = null,
                val revoked: StatusDirective? = null
            )

            @Serializable
            data class Field(
                val id: String? = null,
                val optional: Boolean? = null,
                val path: List<String>,
                val purpose: String? = null,
                val name: String? = null,
                @SerialName("intent_to_retain")
                val intentToRetain: Boolean? = null,
                val filter: JsonElement? = null,
                val predicate: String? = null
            )

            @Serializable
            data class Subject(
                @SerialName("field_id")
                val fieldId: List<String>,
                val directive: Directive
            )

            @Serializable
            data class StatusDirective(
                val type: List<String>,
                val directive: Directive,
            ) {
                init {
                    check(type.isNotEmpty())
                }
            }
        }
    }
}
