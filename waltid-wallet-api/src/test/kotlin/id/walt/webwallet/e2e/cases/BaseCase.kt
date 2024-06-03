package id.walt.webwallet.e2e.cases

import io.github.oshai.kotlinlogging.KotlinLogging

abstract class BaseCase {
    protected val log = KotlinLogging.logger { }
    init {
        log.debug { "Initializing test case ${{}.javaClass.name}" }
    }
}