package id.walt.credentials.utils

import java.util.UUID

actual fun randomUUID(): String = UUID.randomUUID().toString()
