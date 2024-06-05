package id.walt.mdoc.docrequest

import id.walt.mdoc.readerauth.ReaderAuthentication

/**
 *
 */
data class MDocRequestVerificationParams(
  val requiresReaderAuth: Boolean = false,
  val readerKeyId: String? = null,
  val allowedToRetain: Map<String, Set<String>>? = null,
  val readerAuthentication: ReaderAuthentication? = null
)
