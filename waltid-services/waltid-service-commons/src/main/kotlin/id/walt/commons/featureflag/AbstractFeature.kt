package id.walt.commons.featureflag

import id.walt.commons.config.WaltConfig
import kotlin.reflect.KClass

sealed class AbstractFeature(
    open val name: String,
    open val description: String,
    open val configs: Map<String, KClass<out WaltConfig>>,
    open val dependsOn: List<AbstractFeature>,
    open val onEnable: (() -> Unit)? = null
) {

    fun shouldDefaultEnable() = (this is BaseFeature || (this is OptionalFeature && this.default.value))
}
