package id.walt.walletdemo.compose.logic

import id.walt.mdoc.proximity.mobile.AndroidMdocHostApduService

/** Dedicated ISO mdoc HCE entry point; wallet-role qualification uses a separate service. */
class DemoMdocHostApduService : AndroidMdocHostApduService()
