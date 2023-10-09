package id.walt.didlib.utils

import java.util.UUID

actual fun randomUUID(): String = UUID.randomUUID().toString()
