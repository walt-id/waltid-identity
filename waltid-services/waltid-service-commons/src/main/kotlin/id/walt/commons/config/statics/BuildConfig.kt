package id.walt.commons.config.statics

import java.util.*

object BuildConfig {

    private val versionProps by lazy {
        Properties().apply {
            this.javaClass.getResourceAsStream("/version.properties")?.use {
                load(it)
            }
        }
    }

    val version by lazy {
        versionProps.getProperty("version") ?: "[latest]"
    }
}
