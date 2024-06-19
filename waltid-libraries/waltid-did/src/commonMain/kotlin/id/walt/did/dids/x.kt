package id.walt.did.dids

import id.walt.did.dids.resolver.UniresolverResolver

val json = """
{
  "@context": [
    "https://www.w3.org/ns/did/v1",
    "https://w3id.org/security/suites/ed25519-2020/v1"
  ],
  "id": "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN",
  "verificationMethod": [
    {
      "id": "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN#key1",
      "type": "Ed25519VerificationKey2020",
      "controller": "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN",
      "publicKeyMultibase": "z6Mkta7joRuvDh7UnoESdgpr9dDUMh5LvdoECDi3WGrJoscA"
    }
  ],
  "service": [
    {
      "id": "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN#website",
      "type": "LinkedDomains",
      "serviceEndpoint": [
        "https://www.cheqd.io"
      ]
    },
    {
      "id": "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN#non-fungible-image",
      "type": "LinkedDomains",
      "serviceEndpoint": [
        "https://gateway.ipfs.io/ipfs/bafybeihetj2ng3d74k7t754atv2s5dk76pcqtvxls6dntef3xa6rax25xe"
      ]
    },
    {
      "id": "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN#twitter",
      "type": "LinkedDomains",
      "serviceEndpoint": [
        "https://twitter.com/cheqd_io"
      ]
    },
    {
      "id": "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN#linkedin",
      "type": "LinkedDomains",
      "serviceEndpoint": [
        "https://www.linkedin.com/company/cheqd-identity/"
      ]
    }
  ],
  "authentication": [
    "did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN#key1"
  ]
}
""".trimIndent()

suspend fun main() {
    println(UniresolverResolver().resolve("did:cheqd:mainnet:Ps1ysXP2Ae6GBfxNhNQNKN"))


    println("end")
}
