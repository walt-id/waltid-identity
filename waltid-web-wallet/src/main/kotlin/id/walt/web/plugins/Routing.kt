package id.walt.web.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.doublereceive.*

fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(DoubleReceive)
}
