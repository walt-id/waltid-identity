package id.walt.webwallet.service.credentials

import id.walt.webwallet.config.RejectionReasonConfig
import id.walt.webwallet.config.WalletConfig

class RejectionReasonService(
    private val config: WalletConfig,
) {

    fun list(): List<String> = (config as? RejectionReasonConfig)?.reasons ?: emptyList()
}