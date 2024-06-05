package id.walt.featureflag

/**
 * Implement this interface with an object in your service.
 */
interface ServiceFeatureCatalog {

    val baseFeatures: List<BaseFeature>
    val optionalFeatures: List<OptionalFeature>

}
