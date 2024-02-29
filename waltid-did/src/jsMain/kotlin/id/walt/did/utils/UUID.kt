package id.walt.did.utils

import uuid

@ExperimentalJsExport
@JsExport
actual fun randomUUID(): String = uuid.v4()