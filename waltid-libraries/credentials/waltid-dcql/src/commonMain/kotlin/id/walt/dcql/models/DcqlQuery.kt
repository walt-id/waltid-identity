package id.walt.dcql.models

import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the top-level DCQL query structure.
 * See: Section 6 of OpenID4VP spec
 */
@Serializable
data class DcqlQuery(
    val credentials: List<CredentialQuery>,
    @SerialName("credential_sets")
    val credentialSets: List<CredentialSetQuery>? = null,
) {

    fun precheck() {
        if (credentials.isEmpty()) {
            throw IllegalArgumentException("Requested dcql query: credentials cannot be empty")
        }

        if (credentials.any { it.id.isEmpty() }) {
            throw IllegalArgumentException("Requested dcql query: credential has empty id")
        }

        /* // See OSS #1270
        if (credentials.any { it.meta == NoMeta }) {
            throw IllegalArgumentException("Requested dcql query: credential has no meta field")
        }*/

        if (credentials.any { it.claims != null && it.claims.isEmpty() }) {
            throw IllegalArgumentException("Requested dcql query: claims was set, but has no elements")
        }
    }

    object DcqlQueryExamples {

        /** The following shows a query where an ID and an address are requested; either can come from an mDL or a photoid Credential. */
        val EXAMPLE_CREDENTIAL_SET_MDL_OR_PHOTOID = DcqlQuery(
            credentials = listOf(
                // mDL ID + Address
                CredentialQuery(
                    id = "mdl-id",
                    format = CredentialFormat.MSO_MDOC,
                    meta = MsoMdocMeta("org.iso.18013.5.1.mDL"),
                    claims = listOf(
                        ClaimsQuery(id = "given_name", path = listOf("org.iso.18013.5.1", "given_name")),
                        ClaimsQuery(id = "family_name", path = listOf("org.iso.18013.5.1", "family_name")),
                        ClaimsQuery(id = "portrait", path = listOf("org.iso.18013.5.1", "portrait"))
                    )
                ),
                CredentialQuery(
                    id = "mdl-address",
                    format = CredentialFormat.MSO_MDOC,
                    meta = MsoMdocMeta("org.iso.18013.5.1.mDL"),
                    claims = listOf(
                        ClaimsQuery(id = "resident_address", path = listOf("org.iso.18013.5.1", "resident_address")),
                        ClaimsQuery(id = "resident_country", path = listOf("org.iso.18013.5.1", "resident_country"))
                    )
                ),

                // PhotoID ID + Address
                CredentialQuery(
                    id = "photo_card-id",
                    format = CredentialFormat.MSO_MDOC,
                    meta = MsoMdocMeta("org.iso.23220.photoid.1"),
                    claims = listOf(
                        ClaimsQuery(id = "given_name", path = listOf("org.iso.18013.5.1", "given_name")),
                        ClaimsQuery(id = "family_name", path = listOf("org.iso.18013.5.1", "family_name")),
                        ClaimsQuery(id = "portrait", path = listOf("org.iso.18013.5.1", "portrait"))
                    )
                ),
                CredentialQuery(
                    id = "photo_card-address",
                    format = CredentialFormat.MSO_MDOC,
                    meta = MsoMdocMeta("org.iso.23220.photoid.1"),
                    claims = listOf(
                        ClaimsQuery(id = "resident_address", path = listOf("org.iso.18013.5.1", "resident_address")),
                        ClaimsQuery(id = "resident_country", path = listOf("org.iso.18013.5.1", "resident_country"))
                    )
                )
            ),
            credentialSets = listOf(
                CredentialSetQuery(
                    // Either mdl-id or photo_card id
                    options = listOf(
                        listOf("mdl-id"),
                        listOf("photo_card-id")
                    )
                ),
                CredentialSetQuery(
                    required = false,
                    // Optional: either mld-address or photo_card-address
                    options = listOf(
                        listOf("mdl-address"),
                        listOf("photo_card-address")
                    )
                )
            )
        )

        val EXAMPLE_SDJWT_PID = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "pid",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = SdJwtVcMeta(listOf("https://credentials.example.com/identity_credential")),
                    claims = listOf(
                        ClaimsQuery(path = listOf("given_name")),
                        ClaimsQuery(path = listOf("family_name")),
                        ClaimsQuery(path = listOf("address", "street_address"))
                    )
                )
            )
        )
    }

}
