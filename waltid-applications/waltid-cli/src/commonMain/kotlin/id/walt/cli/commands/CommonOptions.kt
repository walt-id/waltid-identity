package id.walt.cli.commands

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option

class CommonOptions : OptionGroup("Common Options") {
    val verbose by option().flag().help("Set verbose mode ON")

}