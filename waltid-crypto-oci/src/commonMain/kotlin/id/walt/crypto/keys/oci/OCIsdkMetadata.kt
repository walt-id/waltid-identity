package id.walt.crypto.keys.oci



expect class OCIsdkMetadata(vaultId: String, compartmentId: String) {
    val vaultId: String
    val compartmentId: String
}