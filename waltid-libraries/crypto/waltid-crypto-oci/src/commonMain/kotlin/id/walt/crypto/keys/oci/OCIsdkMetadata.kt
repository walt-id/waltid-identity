package id.walt.crypto.keys.oci


expect class OCIsdkMetadata(
    vaultId: String,
    compartmentId: String,
    authType: String = "INSTANCE_PRINCIPAL",
    configFilePath: String? = null,
    configProfile: String? = null,
) {
    val vaultId: String
    val compartmentId: String

    /** Authentication type: INSTANCE_PRINCIPAL (default), CONFIG_FILE, or RESOURCE_PRINCIPAL */
    val authType: String

    /** Path to OCI config file; used when authType is CONFIG_FILE (null = SDK default ~/.oci/config) */
    val configFilePath: String?

    /** OCI config profile name; used when authType is CONFIG_FILE (null = DEFAULT) */
    val configProfile: String?
}