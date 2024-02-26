package id.walt.credentials.utils

import uuid
@OptIn(ExperimentalJsExport::class)
@JsExport
actual fun randomUUID(): String = uuid.v4()
