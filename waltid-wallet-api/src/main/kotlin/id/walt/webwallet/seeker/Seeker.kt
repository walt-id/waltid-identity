package id.walt.webwallet.seeker

import id.walt.webwallet.db.models.WalletCredential

interface Seeker<T> {
    fun get(credential: WalletCredential): T
}