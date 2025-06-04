package id.walt.policies

import id.walt.policies.policies.status.Values
import id.walt.policies.policies.status.model.StatusPolicyAttribute
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute

object StatusTestUtils {
    fun w3cHolderCredential(
        index: String,
        id: String,
        size: Int = 1,
        type: String = Values.STATUS_LIST_2021_ENTRY,
        purpose: String = "revocation"
    ) =
        """
            {
                "vc":
                {
                    "credentialStatus":
                    {
                        "id": "${StatusCredentialTestServer.url}/${String.format(StatusCredentialTestServer.statusCredentialPath, id)}#$index",
                        "type": "$type",
                        "statusPurpose": "$purpose",
                        "statusListIndex": "$index",
                        "statusSize": $size,
                        "statusListCredential": "${StatusCredentialTestServer.url}/${String.format(StatusCredentialTestServer.statusCredentialPath, id)}"
                    }
                }
            }
        """.trimIndent()

    fun statusList2021Scenarios(attribute: W3CStatusPolicyAttribute? = null) = listOf(
        TestContext(w3cHolderCredential("4044", StatusCredentialTestServer.waltidRevoked4044), attribute, false),
        TestContext(w3cHolderCredential("4044", StatusCredentialTestServer.waltidUnrevoked4044), attribute, true),
        TestContext(w3cHolderCredential("7", StatusCredentialTestServer.sampleRevoked07), attribute, false),
        TestContext(w3cHolderCredential("42", StatusCredentialTestServer.sampleRevoked42), attribute, false),
    )

    data class TestContext(
        val credential: String,
        val attribute: StatusPolicyAttribute? = null,
        val expectValid: Boolean,
    )
}