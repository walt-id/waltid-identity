package id.walt.sdjwt.metadata.type

/** Fixed, independently hashed three-level wire documents, shared by consumer tests. */
object TypeMetadataFixture {
    const val ROOT = """{"vct":"https://types.example/sca","category":"urn:eudi:sca:1","display":[{"lang":"en","name":"SCA base"},{"lang":"de","name":"SCA Basis"}],"claims":[{"path":["accounts",null,"iban"],"mandatory":true,"sd":"allowed","display":[{"lang":"en","label":"IBAN"}]}],"schema":{"type":"object","required":["account_id"]}}"""
    const val PARENT = """{"vct":"https://types.example/sca-user","extends":"https://types.example/sca","extends#integrity":"sha256-bR9cOJ/a0cOfXWLuX6Ez0JhZwoCVT5Aq5JSlLofnc0I=","claims":[{"path":["accounts",null,"iban"],"sd":"always"}]}"""
    const val LEAF = """{"vct":"https://issuer.example/leaf","extends":"https://types.example/sca-user","extends#integrity":"sha256-Np1qpa0EBrmgn7eEATEEnjycUSgNcVnWyH1KlXRvTiE=","display":[{"locale":"en","name":"Bank attestation"}]}"""
    const val LEAF_PIN = """sha256-ijTpgn6qI3QYzN+xLoSKFMqtWm6q11phuKlyStwz/ss="""
    const val ROOT_SHA384 = """sha384-Ms9+eemMEZp30LvAK9ZZZ24S6Czkp/ewE5UaBq1+5B+Wo+CYr/fsSZrTDeBWVjVX"""
    const val ROOT_SHA512 = """sha512-NhYm4ZAvdXacSjfLe4TY1ciUPXoL52gjra1RSetErQuXeFjsXmDImo5nluTS9MKQscQ56gNRsnkGZKpQcCKXXQ=="""

    val documents = mapOf(
        "https://types.example/sca" to ROOT.encodeToByteArray(),
        "https://types.example/sca-user" to PARENT.encodeToByteArray(),
        "https://issuer.example/leaf" to LEAF.encodeToByteArray(),
    )
}
