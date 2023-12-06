package id.walt.cli

import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.helpers.WaltidServices

suspend fun main() {
    println("CLI JVM")
    WaltidServices.init()

    val dr = DidService.register(DidWebCreateOptions("localhost:3000"))
    println(dr.did)
    println(dr.didDocument)
}
