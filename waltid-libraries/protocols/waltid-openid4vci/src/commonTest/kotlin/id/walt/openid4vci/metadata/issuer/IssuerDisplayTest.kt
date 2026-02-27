package id.walt.openid4vci.metadata.issuer

import kotlin.test.Test
import kotlin.test.assertFailsWith

class IssuerDisplayTest {

    @Test
    fun `issuer display accepts empty object`() {
        IssuerDisplay()
    }

    @Test
    fun `issuer display accepts name only`() {
        IssuerDisplay(name = "Issuer")
    }

    @Test
    fun `issuer display accepts locale only`() {
        IssuerDisplay(locale = "en")
    }

    @Test
    fun `issuer display accepts logo only`() {
        IssuerDisplay(
            logo = IssuerLogo(
                uri = "https://issuer.example/logo.png",
                altText = "issuer logo",
            )
        )
    }

    @Test
    fun `issuer display accepts name and locale`() {
        IssuerDisplay(
            name = "Issuer",
            locale = "en",
        )
    }

    @Test
    fun `issuer display accepts name and logo`() {
        IssuerDisplay(
            name = "Issuer",
            logo = IssuerLogo(
                uri = "https://issuer.example/logo.png",
            ),
        )
    }

    @Test
    fun `issuer display accepts locale and logo`() {
        IssuerDisplay(
            locale = "en",
            logo = IssuerLogo(
                uri = "https://issuer.example/logo.png",
            ),
        )
    }

    @Test
    fun `issuer display accepts name locale and logo`() {
        IssuerDisplay(
            name = "Issuer",
            locale = "en",
            logo = IssuerLogo(
                uri = "https://issuer.example/logo.png",
                altText = "issuer logo",
            ),
        )
    }

    @Test
    fun `issuer display rejects blank name`() {
        assertFailsWith<IllegalArgumentException> {
            IssuerDisplay(name = " ")
        }
    }

    @Test
    fun `issuer display rejects blank locale`() {
        assertFailsWith<IllegalArgumentException> {
            IssuerDisplay(locale = " ")
        }
    }

    @Test
    fun `issuer logo rejects blank uri`() {
        assertFailsWith<IllegalArgumentException> {
            IssuerDisplay(logo = IssuerLogo(uri = " "))
        }
    }
}
