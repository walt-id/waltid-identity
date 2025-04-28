package id.walt.policies.policies.vp

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.definitionparser.PresentationDefinition
import id.walt.definitionparser.PresentationDefinition.InputDescriptor.Directive
import id.walt.definitionparser.PresentationDefinitionParser
import id.walt.definitionparser.PresentationSubmission
import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.policies.PresentationDefinitionException
import id.walt.policies.PresentationDefinitionRelationalConstraintException
import id.walt.sdjwt.SDJwt
import id.walt.w3c.utils.VCFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger { }

@Serializable
class PresentationDefinitionPolicy : CredentialWrapperValidatorPolicy(
) {

    override val name = "presentation-definition"
    override val description =
        "Verifies that with an Verifiable Presentation at minimum the list of credentials `request_credentials` has been presented."
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json, VCFormat.ldp_vp)


    private fun getIssuerDid(credential: JsonObject): String? {
        return credential["iss"]?.jsonPrimitive?.contentOrNull
            ?: credential["vc"]?.jsonObject?.get("issuer")?.let { issuerElement ->
                when (issuerElement) {
                    is JsonPrimitive -> issuerElement.contentOrNull // Issuer is a simple string DID
                    is JsonObject -> issuerElement["id"]?.jsonPrimitive?.contentOrNull // Issuer is an object with id
                    else -> null
                }
            }
    }

    // Helper to get Subject DID from a credential JSON object
    private fun getSubjectDid(credential: JsonObject): String? {
        // Check for JWT 'sub' claim first
        return credential["sub"]?.jsonPrimitive?.contentOrNull
        // Then check for LDP VC structure
            ?: credential["vc"]?.jsonObject?.get("credentialSubject")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        // Add other potential locations if needed
    }

    private fun getVpHolderDid(vpWrapper: JsonObject, format: VCFormat?): String? {
        return when (format) {
            VCFormat.ldp_vp, VCFormat.jwt_vp_json ->
                vpWrapper["vp"]?.jsonObject?.get("holder")?.jsonPrimitive?.contentOrNull

            else -> {
                vpWrapper["holder"]?.jsonPrimitive?.contentOrNull
                    ?: vpWrapper["iss"]?.jsonPrimitive?.contentOrNull
                    ?: vpWrapper["vp"]?.jsonObject?.get("holder")?.jsonPrimitive?.contentOrNull
            }
        }
    }

    private fun getHolder(it: JsonObject) = it["sub"]?.jsonPrimitive?.content
        ?: it["vc"]?.jsonObject["credentialSubject"]?.jsonObject["id"]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("Cannot find holder for credential: $it")


    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val presentationDefinition = context["presentationDefinition"]?.toJsonElement()
            ?.let { Json.decodeFromJsonElement<PresentationDefinition>(it) }
            ?: throw IllegalArgumentException("No presentationDefinition in context!")
        require(presentationDefinition.inputDescriptors.isNotEmpty()) {
            "No input descriptors were found. " +
                    "At least one is required in the context of the presentation definition policy."
        }

        val presentationSubmission = context["presentationSubmission"]?.toJsonElement()
            ?.let { Json.decodeFromJsonElement<PresentationSubmission>(it) }
            ?: throw IllegalArgumentException("No presentationSubmission in context!")
        val format =
            presentationSubmission.descriptorMap.firstOrNull()?.format?.let { Json.decodeFromJsonElement<VCFormat>(it) }

        val vpHolderDid = getVpHolderDid(data, format)
            ?: return Result.failure(IllegalArgumentException("Could not determine Holder DID from the Verifiable Presentation")) // Fail early if holder unknown

        log.debug { "VP Holder DID: $vpHolderDid" }

        val presentedTypes = when (format) {
            VCFormat.sd_jwt_vc -> listOf(data["vct"]!!.jsonPrimitive.content)
            else -> data["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.let { SDJwt.parse(it) }?.fullPayload
                    ?.get("vc")?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
        }

        log.debug { "Presented types: $presentedTypes" }
        log.debug { "Presentation definition: $presentationDefinition" }
        log.debug { "Presented data: $data" }

        val credentialsFlow = when (format) {
            VCFormat.sd_jwt_vc -> flowOf(data)

            else -> (data["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.mapNotNull { vc ->
                vc.jsonPrimitive.contentOrNull?.let { SDJwt.parse(it) }?.fullPayload
                    ?: throw IllegalArgumentException("Credential $vc is not a valid (SD-)JWT string")
            } ?: emptyList()).asFlow()

        }

        val subjectDidsPerFieldId = mutableMapOf<String, MutableSet<String>>()
        var overallSuccess = true

        // Process input descriptors
        for (inputDescriptor in presentationDefinition.inputDescriptors) {
            log.debug { "Processing Input Descriptor: ${inputDescriptor.id}" }

            val matchedCredentials = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
                credentialsFlow,
                inputDescriptor,
            ).toList()

            if (matchedCredentials.isEmpty()) {
                log.warn { "Input descriptor ${inputDescriptor.id} did not match any credentials." }
                overallSuccess = false
                continue
            }

            log.debug { "Input descriptor ${inputDescriptor.id} matched ${matchedCredentials.size} credential(s)." }

            // --- subject_is_issuer check ---
            if (inputDescriptor.constraints.subjectIsIssuer?.equals("required", ignoreCase = true) == true) {
                for (cred in matchedCredentials) {
                    val issuer = getIssuerDid(cred)
                        ?: return Result.failure(IllegalArgumentException("Cannot find issuer for credential matching descriptor ${inputDescriptor.id}: $cred"))
                    val subject = getSubjectDid(cred)
                        ?: return Result.failure(IllegalArgumentException("Cannot find subject for credential matching descriptor ${inputDescriptor.id}: $cred"))

                    if (issuer != subject) {
                        log.error { "subject_is_issuer constraint failed for descriptor ${inputDescriptor.id}. Issuer: $issuer, Subject: $subject" }
                        return Result.failure(
                            PresentationDefinitionRelationalConstraintException(
                                constraint = "subject_is_issuer",
                                "Subject ($subject) does not match issuer ($issuer)."
                            )
                        )
                    }
                }
                log.debug { "subject_is_issuer constraint passed for descriptor ${inputDescriptor.id}" }
            }

            // --- is_holder Check ---
            inputDescriptor.constraints.isHolder?.forEach { isHolderConstraint ->
                if (isHolderConstraint.directive == Directive.required) {
                    for (cred in matchedCredentials) {
                        val subject = getSubjectDid(cred)
                            ?: return Result.failure(IllegalArgumentException("Cannot find subject for credential matching descriptor ${inputDescriptor.id} for is_holder check: $cred"))

                        if (subject != vpHolderDid) {
                            log.error { "is_holder constraint failed for descriptor ${inputDescriptor.id}. Subject: $subject, Holder: $vpHolderDid" }
                            return Result.failure(
                                PresentationDefinitionRelationalConstraintException(
                                    constraint = "is_holder", constraintFailureDescription = "" +
                                            "Credential subject ($subject) does not match holder ($vpHolderDid)"
                                )
                            )
                        }
                    }
                    log.debug { "is_holder constraint passed for descriptor ${inputDescriptor.id}" }
                }
            }

            // --- Data Collection for same_subject Check ---
            inputDescriptor.constraints.fields?.filter { it.id != null }?.forEach { field ->
                val fieldId = field.id!!
                for (cred in matchedCredentials) {
                    getSubjectDid(cred)?.let { subjectDid ->
                        subjectDidsPerFieldId.getOrPut(fieldId) { mutableSetOf() }.add(subjectDid)
                        log.trace { "Stored subject $subjectDid for field ID $fieldId from descriptor ${inputDescriptor.id}" }
                    }
                        ?: log.warn { "Could not extract subject DID for credential matching field $fieldId in descriptor ${inputDescriptor.id}" }
                }
            }
        }

        // --- same_subject Check ---
        if (overallSuccess) {
            log.debug { "Performing same_subject checks..." }
            presentationDefinition.inputDescriptors.forEach { inputDescriptor ->
                inputDescriptor.constraints.sameSubject?.forEach { sameSubjectConstraint ->
                    if (sameSubjectConstraint.directive == Directive.required) {
                        log.debug { "Checking required same_subject for fields: ${sameSubjectConstraint.fieldId}" }
                        val subjectSets = sameSubjectConstraint.fieldId
                            .mapNotNull { fieldId -> subjectDidsPerFieldId[fieldId]?.takeIf { it.isNotEmpty() } } // Get non-empty sets for the required fields

                        if (subjectSets.size != sameSubjectConstraint.fieldId.size) {
                            log.error {
                                "same_subject constraint failed: Not all specified field_ids (${sameSubjectConstraint.fieldId}) yielded subject DIDs. Found subjects for: ${
                                    subjectSets.flatten().distinct()
                                }"
                            }
                            return Result.failure(
                                PresentationDefinitionRelationalConstraintException(
                                    constraint = "same_subject",
                                    constraintFailureDescription = "Required field_id(s) did not resolve to subjects: ${sameSubjectConstraint.fieldId.filter { subjectDidsPerFieldId[it].isNullOrEmpty() }}"
                                )
                            )
                        }

                        // Check if all collected subject sets are identical (and non-empty)
                        if (subjectSets.isNotEmpty()) {
                            val firstSubjectSet = subjectSets.first()
                            if (!subjectSets.all { it == firstSubjectSet }) {
                                log.error { "same_subject constraint failed for fields ${sameSubjectConstraint.fieldId}. Found differing subject sets: $subjectSets" }
                                return Result.failure(
                                    PresentationDefinitionRelationalConstraintException(
                                        constraint = "same_subject",
                                        constraintFailureDescription = "Subjects did not match for fields: ${sameSubjectConstraint.fieldId}. Subjects found: ${
                                            subjectSets.flatten().distinct()
                                        }"
                                    )
                                )
                            }
                            log.debug { "same_subject constraint passed for fields ${sameSubjectConstraint.fieldId} with subject(s): $firstSubjectSet" }
                        } else {
                            log.warn { "same_subject constraint for fields ${sameSubjectConstraint.fieldId} had no subjects to compare." }
                            return Result.failure(
                                PresentationDefinitionRelationalConstraintException(
                                    constraint = "same_subject",
                                    constraintFailureDescription = "No subjects found for required field_ids: ${sameSubjectConstraint.fieldId}"
                                )
                            )
                        }
                    }
                }
            }
        }

        return if (overallSuccess) {
            log.info { "Presentation Definition Policy verification successful." }
            Result.success(presentedTypes)
        } else {
            log.error { "Presentation Definition Policy verification failed. Not all input descriptors satisfied or relational constraints failed." }
            // Log details if needed (already done during checks)
            // log.debug { "Requested types: $requestedTypes" } // If you re-introduce requestedTypes
            log.debug { "Presented types: $presentedTypes" }
            log.debug { "Presentation definition: $presentationDefinition" }
            log.debug { "Presented data: $data" }
            log.debug { "Collected Subjects per Field ID: $subjectDidsPerFieldId" }


            Result.failure(
                PresentationDefinitionException(false)
            )
        }
    }
}
