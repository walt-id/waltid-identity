package id.walt.oid4vc.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun randomUUID(): String {
    return Uuid.random().toString()
}
