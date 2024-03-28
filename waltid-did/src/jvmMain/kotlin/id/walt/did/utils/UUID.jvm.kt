package id.walt.did.utils

import java.util.UUID

actual fun randomUUID(): String = UUID.randomUUID().toString()