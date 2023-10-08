package id.walt.ssikit.utils

import java.util.UUID

actual fun randomUUID(): String = UUID.randomUUID().toString()
