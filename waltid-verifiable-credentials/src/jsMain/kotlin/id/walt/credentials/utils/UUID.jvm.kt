package id.walt.credentials.utils

import uuid
@ExperimentalJsExport
@JsExport
actual fun randomUUID(): String = uuid.v4()
