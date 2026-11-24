package id.walt.mdoc.encoding

import id.walt.cose.validateCborStructure

/** See [validateCborStructure] for duplicate-map and embedded tag-24 validation without re-encoding. */
fun validateMdocCbor(bytes: ByteArray) = validateCborStructure(bytes)
