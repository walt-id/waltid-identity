package id.walt.commons.featureflag

import id.walt.commons.config.WaltConfig
import kotlin.reflect.KClass

class BaseFeature(
    override val name: String,
    override val description: String,
    override val configs: Map<String, KClass<out WaltConfig>>,
) : AbstractFeature(name, description, configs, emptyList()) {

    constructor(
        name: String,
        description: String,
        config: KClass<out WaltConfig>,
    ) : this(name, description, mapOf(name to config))
}
