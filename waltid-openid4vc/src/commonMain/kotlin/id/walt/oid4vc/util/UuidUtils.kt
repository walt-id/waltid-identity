package id.walt.oid4vc.util

import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

fun randomUUID(): String {
    return UUID.generateUUID().toString()
}
