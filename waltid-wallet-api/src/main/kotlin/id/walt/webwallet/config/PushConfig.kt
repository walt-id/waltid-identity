package id.walt.webwallet.config

import com.sksamuel.hoplite.Masked
import kotlinx.serialization.Serializable

//@Serializable
data class PushConfig(val pushPublicKey: String, val pushPrivateKey: Masked, val pushSubject: String) : WalletConfig()

