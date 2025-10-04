package id.walt.mdoc

import id.walt.mdoc.cose.AsyncCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
interface JSAsyncCOSECryptoProvider: AsyncCOSECryptoProvider {
  fun sign1Async(payload: dynamic, keyID: String?): Promise<COSESign1>
}
