package id.walt.webwallet.service.credentials

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.RejectionReasonConfig

class RejectionReasonService {
    private val config by lazy { ConfigManager.getConfig<RejectionReasonConfig>() }
    fun list(): List<String> = (config as? RejectionReasonConfig)?.reasons ?: emptyList()
}