package id.walt.openid4vp.conformance.testplans.httpdata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A test module as advertised by `GET /api/runner/available`, restricted to its variant metadata.
 *
 * This is the suite's own description of which variant values each module applies to, which is what
 * lets the runners decide applicability without transcribing the modules' `@VariantNotApplicable` /
 * `@VariantNotApplicableWhen` annotations into this repository (where they would silently drift out
 * of sync on every suite upgrade).
 *
 * Only the fields the runners act on are modelled; the suite adds many more.
 */
@Serializable
data class AvailableTestModule(
    val testName: String? = null,
    /** Keyed by variant parameter name, e.g. `request_method`. */
    val variants: Map<String, VariantAxis> = emptyMap(),
) {

    /**
     * The suite's applicability metadata for one variant parameter of one module.
     */
    @Serializable
    data class VariantAxis(
        /**
         * Values of this parameter the module applies to, i.e. the parameter's enum values minus the
         * ones excluded by a static `@VariantNotApplicable`.
         *
         * The values carry per-value configuration metadata this harness does not use, so only the
         * keys are meaningful here.
         */
        val variantValues: Map<String, JsonElement> = emptyMap(),
        /**
         * Values excluded only for certain values of *another* parameter, from
         * `@VariantNotApplicableWhen`: `conditionParameter -> conditionValue -> excluded values`.
         */
        val notApplicableWhen: Map<String, Map<String, Set<String>>> = emptyMap(),
    ) {

        /**
         * Values of this parameter that remain applicable once [variantSelection] has been taken
         * into account.
         *
         * An empty result means no value of this parameter is applicable, i.e. the module does not
         * apply to [variantSelection] at all. See [id.walt.openid4vp.conformance.testplans.plans.vp
         * .wallet.WalletModuleApplicability] for why that case needs stating explicitly.
         */
        fun applicableValues(variantSelection: Map<String, String>): Set<String> =
            variantValues.keys - notApplicableWhen.flatMap { (conditionParameter, excludedByValue) ->
                excludedByValue[variantSelection[conditionParameter]].orEmpty()
            }.toSet()
    }
}
