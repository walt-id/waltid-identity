package id.walt.holderpolicies.checks

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("dcql")
data class DcqlHolderPolicyCheck(
    @SerialName("dcql_query")
    val dcqlQuery: DcqlQuery
) : HolderPolicyCheck {
    override suspend fun matchesCredentials(credentials: Flow<DigitalCredential>): Boolean {
        val dcqlCredentials = credentials.toList().mapIndexed { idx, credential ->
            RawDcqlCredential(
                id = idx.toString(),
                format = credential.format,
                data = credential.credentialData,
                originalCredential = credential,
                disclosures = if (credential is SelectivelyDisclosableVerifiableCredential)
                    credential.disclosures?.map { DcqlDisclosure(it.name, it.value) }
                else null
            )
        }
        val match = DcqlMatcher.match(dcqlQuery, dcqlCredentials)
        return match.isSuccess && match.getOrThrow().isNotEmpty()
    }

}
