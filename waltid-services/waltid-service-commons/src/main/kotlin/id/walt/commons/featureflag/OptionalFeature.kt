package id.walt.commons.featureflag

import id.walt.commons.config.WaltConfig
import kotlin.collections.emptyList
import kotlin.reflect.KClass

class OptionalFeature(
    override val name: String,
    override val description: String,
    override val configs: Map<String, KClass<out WaltConfig>> = emptyMap(),
    override val dependsOn: List<AbstractFeature> = emptyList(),
    val default: Lazy<Boolean>,
    override val onEnable: (() -> Unit)? = null
) : AbstractFeature(name, description, configs, dependsOn) {

    constructor(
        name: String,
        description: String,
        config: KClass<out WaltConfig>,
        default: Boolean,
        onEnable: (() -> Unit)? = null
    ) : this(
        name = name,
        description = description,
        configs = mapOf(name to config),
        default = lazy { default },
        onEnable = onEnable
    )

    constructor(
        name: String,
        description: String,
        config: KClass<out WaltConfig>,
        default: Lazy<Boolean>,
    ) : this(
        name = name,
        description = description,
        configs = mapOf(name to config),
        default = default
    )

    constructor(
        name: String,
        description: String,
        configs: Map<String, KClass<out WaltConfig>> = emptyMap(),
        dependsOn: List<AbstractFeature> = emptyList(),
        default: Boolean,
        onEnable: (() -> Unit)? = null
    ) : this(
        name = name,
        description = description,
        configs = configs,
        dependsOn = dependsOn,
        default = lazy { default },
        onEnable = onEnable
    )

}

