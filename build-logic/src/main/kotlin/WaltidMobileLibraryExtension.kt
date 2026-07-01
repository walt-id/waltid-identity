import org.gradle.api.provider.Property

abstract class WaltidMobileLibraryExtension {
    abstract val androidNamespace: Property<String>
}
