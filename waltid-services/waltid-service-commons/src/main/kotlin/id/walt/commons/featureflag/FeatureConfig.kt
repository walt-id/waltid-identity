package id.walt.commons.featureflag

import id.walt.commons.config.WaltConfig

/**
 * Feature configuration:
 * - This refers to optional features, base features are always enabled.
 * - Features have a `default` (boolean) state, either true or false.
 * - Features marked with default=true are enabled by default.
 *      - To disable them, they have to be explicitly disabled by listing them
 *        in `disabledFeatures`.
 *      - This is usually the case with features that don't "hurt"/"bother" users, and
 *        that either don't have any configuration or take on a sane default configuration
 *        when no configuration is provided
 *        (= user does not need to configure the feature for it to work or make sense).
 * - Features marked with default=false are disabled by default.
 *      - To enable them, they have to be explicitly enabled by listing them
 *        in `enabledFeatures`.
 *      - This is usually the case with features that are rather specific to ones use-cases,
 *        and that require configuration by the user (e.g., access credentials/API tokens).
 *        (= user needs to configure the feature for it to work or make sense).
 */
data class FeatureConfig(
    val enabledFeatures: List<String> = emptyList(),
    val disabledFeatures: List<String> = emptyList(),
) : WaltConfig()
