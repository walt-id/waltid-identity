package id.walt.wallet2.mobile.digitalcredentials

/** Title, subtitle and artwork displayed by the operating system for a credential. */
public class RegistryEntryDisplay(
    public val name: String,
    public val subtitle: String = "",
    iconPng: ByteArray? = null,
    public val cardArt: MobileWalletCardArt = MobileWalletCardArt(),
) {
    public val iconPng: ByteArray? get() = icon?.copyOf()

    public override fun equals(other: Any?): Boolean = other is RegistryEntryDisplay &&
        name == other.name && subtitle == other.subtitle && icon.contentEquals(other.icon) && cardArt == other.cardArt

    public override fun hashCode(): Int = ((name.hashCode() * 31 + subtitle.hashCode()) * 31 + icon.contentHashCode()) * 31 + cardArt.hashCode()

    internal fun withIcon(png: ByteArray) = RegistryEntryDisplay(name, subtitle, png, cardArt)

    private val icon = iconPng?.copyOf()
}

/** Ordered remote image candidates and local fallback artwork supplied by credential metadata. */
public class MobileWalletCardArt(
    imageUris: List<String> = emptyList(),
    public val backgroundColor: String? = null,
    fallbackPng: ByteArray? = null,
) {
    public val imageUris: List<String> = buildList { addAll(imageUris) }
    public val fallbackPng: ByteArray? get() = fallback?.copyOf()

    public override fun equals(other: Any?): Boolean = other is MobileWalletCardArt &&
        imageUris == other.imageUris && backgroundColor == other.backgroundColor && fallback.contentEquals(other.fallback)

    public override fun hashCode(): Int = (imageUris.hashCode() * 31 + backgroundColor.hashCode()) * 31 + fallback.contentHashCode()

    private val fallback = fallbackPng?.copyOf()
}
