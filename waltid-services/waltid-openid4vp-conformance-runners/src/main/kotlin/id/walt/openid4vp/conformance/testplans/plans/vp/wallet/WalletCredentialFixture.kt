package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The credential the wallet under test is provisioned with, and the DCQL query that asks for it.
 *
 * In wallet conformance runs the conformance suite plays the verifier: it sends `dcql_query` and the
 * wallet has to find a matching credential in its store. That only works if the query and the
 * provisioned credential agree, so both are defined here rather than being restated per plan.
 */
object WalletCredentialFixture {

    /** `vct` of the SD-JWT VC provisioned into the wallet. */
    const val SD_JWT_VC_VCT = "https://credentials.example.com/identity_credential"

    /** Claims the credential carries, all selectively disclosable. */
    val SD_JWT_VC_CLAIMS: Map<String, String> = mapOf(
        "given_name" to "Erika",
        "family_name" to "Mustermann",
        "birthdate" to "1971-09-01",
    )

    /** DCQL query matching the provisioned SD-JWT VC. */
    val SD_JWT_VC_DCQL: JsonObject = Json.decodeFromString(
        """
        {
            "credentials": [
                {
                    "id": "pid",
                    "format": "dc+sd-jwt",
                    "meta": {
                        "vct_values": ["$SD_JWT_VC_VCT"]
                    },
                    "claims": [
                        {"path": ["given_name"]},
                        {"path": ["family_name"]},
                        {"path": ["birthdate"]}
                    ]
                }
            ]
        }
        """.trimIndent()
    )

    /** `docType` of the mDL provisioned into the wallet. */
    const val MDOC_DOCTYPE = "org.iso.18013.5.1.mDL"

    /** ISO 18013-5 namespace the mDL elements live in. */
    const val MDOC_NAMESPACE = "org.iso.18013.5.1"

    /**
     * DCQL query matching the provisioned mDL.
     *
     * Deliberately minimal, and mirrors the suite's own reference configuration
     * (`scripts/test-configs-rp-against-op/vp-wallet-test-config-dcql-mdoc.json`): every requested
     * claim is a claim that has to be present *and* correctly CBOR-typed in the credential.
     */
    val MDOC_DCQL: JsonObject = Json.decodeFromString(
        """
        {
            "credentials": [
                {
                    "id": "my_credential",
                    "format": "mso_mdoc",
                    "meta": {
                        "doctype_value": "$MDOC_DOCTYPE"
                    },
                    "claims": [
                        {"path": ["$MDOC_NAMESPACE", "document_number"]},
                        {"path": ["$MDOC_NAMESPACE", "birth_date"]}
                    ]
                }
            ]
        }
        """.trimIndent()
    )

    /** DCQL query for [credentialFormat], as used in the conformance-suite plan variants. */
    fun dcqlFor(credentialFormat: String): JsonObject = when (credentialFormat) {
        "iso_mdl" -> MDOC_DCQL
        "sd_jwt_vc" -> SD_JWT_VC_DCQL
        else -> error("No provisioned credential for credential_format '$credentialFormat'")
    }
}
