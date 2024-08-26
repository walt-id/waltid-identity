package id.walt.authkit.accounts

import kotlin.uuid.Uuid

@OptIn(ExperimentalStdlibApi::class)
data class Account(
    val id: Uuid,
    val name: String? = null
)
