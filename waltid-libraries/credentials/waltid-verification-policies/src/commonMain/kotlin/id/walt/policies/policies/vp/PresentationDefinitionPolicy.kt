package id.walt.policies.policies.vp

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.definitionparser.PresentationDefinition
import id.walt.definitionparser.PresentationDefinition.InputDescriptor.Directive
import id.walt.definitionparser.PresentationDefinitionParser
import id.walt.definitionparser.PresentationSubmission
import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.policies.PresentationDefinitionException
import id.walt.policies.PresentationDefinitionRelationalConstraintException
import id.walt.policies.PresentationDefinitionRelationalConstraintException.RelationalConstraintType
import id.walt.sdjwt.SDJwt
import id.walt.w3c.utils.VCFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger { }

@Serializable
class PresentationDefinitionPolicy : CredentialWrapperValidatorPolicy() {

    override val name = "presentation-definition"
    override val description =
        "Verifies a Verifiable Presentation against a Presentation Definition, including relational constraints."
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json, VCFormat.ldp_vp)


    private fun checkSubjectIsIssuer(
        matchedCredentials: List<JsonObject>,
        descriptorId: String
    ): Result<Unit> {
        for (cred in matchedCredentials) {
            fun cannotFindField(field: String): Result<Unit> =
                Result.failure(IllegalArgumentException("Cannot find $field for credential matching descriptor $descriptorId: $cred"))

            val issuer = getIssuerDid(cred) ?: return cannotFindField("issuer")
            val subject = getSubjectDid(cred) ?: return cannotFindField("subject")

            if (issuer != subject) {
                log.debug { "subject_is_issuer constraint failed for descriptor $descriptorId. Issuer: $issuer, Subject: $subject" }
                return Result.failure(
                    PresentationDefinitionRelationalConstraintException(
                        constraint = RelationalConstraintType.subject_is_issuer,
                        "Subject ($subject) does not match issuer ($issuer) for descriptor $descriptorId."
                    )
                )
            }
        }
        log.trace { "subject_is_issuer constraint passed for descriptor $descriptorId" }
        return Result.success(Unit)
    }

    private fun checkIsHolder(
        matchedCredentials: List<JsonObject>,
        vpHolderDid: String,
        descriptorId: String
    ): Result<Unit> {
        for (cred in matchedCredentials) {
            val subject = getSubjectDid(cred)
                ?: return Result.failure(IllegalArgumentException("Cannot find subject for credential matching descriptor $descriptorId for is_holder check: $cred"))

            if (subject != vpHolderDid) {
                log.error { "is_holder constraint failed for descriptor $descriptorId. Subject: $subject, Holder: $vpHolderDid" }
                return Result.failure(
                    PresentationDefinitionRelationalConstraintException(
                        constraint = RelationalConstraintType.is_holder,
                        "Credential subject ($subject) does not match VP holder ($vpHolderDid) for descriptor $descriptorId."
                    )
                )
            }
        }
        log.trace { "is_holder constraint passed for descriptor $descriptorId" }
        return Result.success(Unit)
    }

    private fun checkSameSubject(
        presentationDefinition: PresentationDefinition,
        subjectDidsPerFieldId: Map<String, Set<String>>
    ): Result<Unit> {
        log.trace { "Performing same_subject checks..." }
        presentationDefinition.inputDescriptors.forEach { inputDescriptor ->
            inputDescriptor.constraints.sameSubject?.forEach { sameSubjectConstraint ->
                if (sameSubjectConstraint.directive == Directive.required) {
                    log.trace { "Checking required same_subject for fields: ${sameSubjectConstraint.fieldId} (related to descriptor ${inputDescriptor.id})" }

                    val subjectSets = sameSubjectConstraint.fieldId
                        .mapNotNull { fieldId -> subjectDidsPerFieldId[fieldId]?.takeIf { it.isNotEmpty() } }

                    // Check if all field IDs mentioned in the constraint actually yielded subjects
                    if (subjectSets.size != sameSubjectConstraint.fieldId.size) {
                        val missingFieldIds = sameSubjectConstraint.fieldId.filter { subjectDidsPerFieldId[it].isNullOrEmpty() }
                        log.debug { "same_subject constraint failed: Not all specified field_ids (${sameSubjectConstraint.fieldId}) yielded subject DIDs. Missing: $missingFieldIds" }
                        return Result.failure(
                            PresentationDefinitionRelationalConstraintException(
                                constraint = RelationalConstraintType.same_subject,
                                "Required field_id(s) did not resolve to subjects: $missingFieldIds"
                            )
                        )
                    }

                    // Check if the subjects found are consistent across the specified fields
                    if (subjectSets.isNotEmpty()) {
                        val firstSubjectSet = subjectSets.first()
                        if (!subjectSets.all { it == firstSubjectSet }) {
                            val distinctSubjects = subjectSets.flatten().distinct()
                            log.debug { "same_subject constraint failed for fields ${sameSubjectConstraint.fieldId}. Found differing subjects: $distinctSubjects" }
                            return Result.failure(
                                PresentationDefinitionRelationalConstraintException(
                                    constraint = RelationalConstraintType.same_subject,
                                    "Subjects did not match for fields: ${sameSubjectConstraint.fieldId}. Subjects found: $distinctSubjects"
                                )
                            )
                        }
                        log.trace { "same_subject constraint passed for fields ${sameSubjectConstraint.fieldId} with subject(s): $firstSubjectSet" }
                    } else {
                        log.debug { "same_subject constraint for fields ${sameSubjectConstraint.fieldId} had no subjects to compare (this should have been caught earlier)." }
                        return Result.failure(
                            PresentationDefinitionRelationalConstraintException(
                                constraint = RelationalConstraintType.same_subject,
                                "No subjects found for required field_ids: ${sameSubjectConstraint.fieldId}"
                            )
                        )
                    }
                }
            }
        }
        log.trace { "All same_subject checks passed." }
        return Result.success(Unit)
    }

    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val presentationDefinition = context["presentationDefinition"]?.toJsonElement()
            ?.let { Json.decodeFromJsonElement<PresentationDefinition>(it) }
            ?: return Result.failure(IllegalArgumentException("PresentationDefinition missing in context"))

        require(presentationDefinition.inputDescriptors.isNotEmpty()) {
            "PresentationDefinition must contain at least one input descriptor."
        }

        val presentationSubmission = context["presentationSubmission"]?.toJsonElement()
            ?.let { Json.decodeFromJsonElement<PresentationSubmission>(it) }
            ?: return Result.failure(IllegalArgumentException("PresentationSubmission missing in context"))

        val format = presentationSubmission.descriptorMap.firstOrNull()?.format
            ?.let { Json.decodeFromJsonElement<VCFormat>(it) }

        val vpHolderDid = getVpHolderDid(data, format)
            ?: return Result.failure(IllegalArgumentException("Could not determine Holder DID from the Verifiable Presentation"))

        log.debug { "Verifying Presentation Definition. VP Holder DID: $vpHolderDid, Format: $format" }

        val credentialsFlow: Flow<JsonObject> = when {
            format == VCFormat.sd_jwt_vc && !data.contains("vp") -> flowOf(data)
            else -> (data["vp"]?.jsonObject?.get("verifiableCredential")?.jsonArray?.mapNotNull { vc ->
                vc.jsonPrimitive.contentOrNull?.let { SDJwt.parse(it) }?.fullPayload
            } ?: emptyList()).asFlow()
        }

        val hasSameSubjectConstraint = presentationDefinition.inputDescriptors.any {
            it.constraints.sameSubject?.isNotEmpty() == true
        }

        val subjectDidsPerFieldId = mutableMapOf<String, MutableSet<String>>()
        var allDescriptorsMatched = true

        for (inputDescriptor in presentationDefinition.inputDescriptors) {
            log.trace { "Processing Input Descriptor: ${inputDescriptor.id}" }

            val matchedCredentials = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
                credentialsFlow,
                inputDescriptor,
            ).toList()

            if (matchedCredentials.isEmpty()) {
                log.debug { "Input descriptor ${inputDescriptor.id} did not match any credentials." }
                allDescriptorsMatched = false
                continue
            }

            log.trace { "Input descriptor ${inputDescriptor.id} matched ${matchedCredentials.size} credential(s)." }

            // subject_is_issuer
            if (inputDescriptor.constraints.subjectIsIssuer?.equals("required", ignoreCase = true) == true) {
                checkSubjectIsIssuer(matchedCredentials, inputDescriptor.id).getOrElse { return Result.failure(it) }
            }

            // is_holder
            inputDescriptor.constraints.isHolder?.forEach { holderConstraint ->
                if (holderConstraint.directive == Directive.required) {
                    checkIsHolder(matchedCredentials, vpHolderDid, inputDescriptor.id).getOrElse { return Result.failure(it) }
                }
            }

            // --- Collect Data for Cross-Descriptor Checks (same_subject) ---
            if (hasSameSubjectConstraint) {
                inputDescriptor.constraints.fields?.filter { it.id != null }?.forEach { field ->
                    val fieldId = field.id!!
                    matchedCredentials.forEach { cred ->
                        getSubjectDid(cred)?.let { subjectDid ->
                            subjectDidsPerFieldId.getOrPut(fieldId) { mutableSetOf() }.add(subjectDid)
                        }
                            ?: log.debug { "Could not extract subject DID for credential matching field $fieldId in descriptor ${inputDescriptor.id}" }
                    }
                }
            }
        }

        // --- Apply Cross-Descriptor Constraints (same_subject) ---
        if (allDescriptorsMatched && hasSameSubjectConstraint) {
            // Only check same_subject if all descriptors were initially matched
            checkSameSubject(presentationDefinition, subjectDidsPerFieldId).getOrElse { return Result.failure(it) }
        }

        // Result
        return if (allDescriptorsMatched) {
            log.info { "Presentation Definition Policy verification successful." }
            val presentedTypes = when (format) {
                VCFormat.sd_jwt_vc -> listOfNotNull(data["vct"]?.jsonPrimitive?.content)
                else -> data["vp"]?.jsonObject?.get("verifiableCredential")?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull?.let { SDJwt.parse(it) }?.fullPayload
                        ?.get("vc")?.jsonObject?.get("type")?.jsonArray?.lastOrNull()?.jsonPrimitive?.contentOrNull
                } ?: emptyList()
            }
            Result.success(presentedTypes)
        } else {
            log.debug { "Presentation Definition Policy verification failed: Not all input descriptors were satisfied." }
            Result.failure(PresentationDefinitionException(false))
        }
    }

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

    private fun getSubjectDid(credential: JsonObject): String? {
        return credential["sub"]?.jsonPrimitive?.contentOrNull
            ?: credential["vc"]?.jsonObject?.get("credentialSubject")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    private fun getVpHolderDid(vpWrapper: JsonObject, format: VCFormat?): String? {
        return when (format) {
            VCFormat.ldp_vp, VCFormat.jwt_vp_json ->
                vpWrapper["vp"]?.jsonObject?.get("holder")?.jsonPrimitive?.contentOrNull

            else -> null
        } ?: vpWrapper["holder"]?.jsonPrimitive?.contentOrNull
        ?: vpWrapper["iss"]?.jsonPrimitive?.contentOrNull
        ?: vpWrapper["vp"]?.jsonObject?.get("holder")?.jsonPrimitive?.contentOrNull
    }
}
