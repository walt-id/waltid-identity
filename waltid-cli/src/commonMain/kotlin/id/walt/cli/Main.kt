package id.walt.cli

import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.commands.Did

fun main(args: Array<String>) {
    println("-- walt.id SSI Kit v2 CLI --")

    MainCommand()
        .subcommands(
            Did().subcommands(
                Did.Create()
            )
        )
        .main(args)


    /*WaltidServices.init()
    WaltidServices.minimalInit()

    val dr = DidService.register(DidWebCreateOptions("localhost:3000"))
    println(dr.did)
    println(dr.didDocument)*/
}
