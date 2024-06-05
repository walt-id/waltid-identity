package id.walt.featureflag

import id.walt.config.WaltConfig
import kotlin.reflect.KClass

class OptionalFeature(
    override val name: String,
    override val description: String,
    override val configs: Map<String, KClass<out WaltConfig>> = emptyMap(),
    override val dependsOn: List<AbstractFeature> = emptyList(),
    val default: Boolean
) : AbstractFeature(name, description, configs, dependsOn) {

    constructor(name: String, description: String, config: KClass<out WaltConfig>, default: Boolean) : this(name, description, mapOf(name to config), emptyList(), default)

}

