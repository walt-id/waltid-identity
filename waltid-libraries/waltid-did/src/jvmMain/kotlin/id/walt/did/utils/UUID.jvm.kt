package id.walt.did.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
actual fun randomUUID(): String = Uuid.random().toString()
