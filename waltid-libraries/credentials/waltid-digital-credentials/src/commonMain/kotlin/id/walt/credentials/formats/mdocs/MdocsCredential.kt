package id.walt.credentials.formats.mdocs

import id.walt.credentials.formats.VerifiableCredential
import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.json.JsonObject

data class MdocsCredential(
    override val signature: CredentialSignature?,
    override val signed: String?, override val credentialData: JsonObject
) : VerifiableCredential()
