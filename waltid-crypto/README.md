# walt.id Crypto

## Platforms available:

- Java: JVM
- JS: Node.js or WebCrypto
- Native: libsodium & OpenSSL (todo)
- WebAssembly (WASM): (todo)

## Signature schemes available:

| Type  |   ECDSA   | JOSE ID | Description                                                     |
|:-----:|:---------:|:-------:|:----------------------------------------------------------------|
| EdDSA |  Ed25519  |  EdDSA  | EdDSA + Curve25519                                              |
| ECDSA | secp256r1 |  ES256  | ECDSA + SECG curve secp256r1 ("NIST P-256")                     |
| ECDSA | secp256k1 | ES256K  | ECDSA + SECG curve secp256k1 (Koblitz curve as used in Bitcoin) |
|  RSA  |    RSA    |  RS256  | RSA                                                             |

## Compatibility matrix:

### JWS (recommended)

| Algorithm | JVM provider |   JS provider / platform    |
|:---------:|:------------:|:---------------------------:|
|   EdDSA   | Nimbus JOSE  |       jose / Node.js        |
|   ES256   | Nimbus JOSE  | jose / Node.js & Web Crypto |
|  ES256K   | Nimbus JOSE  |       jose / Node.js        |
|   RS256   | Nimbus JOSE  | jose / Node.js & Web Crypto |

### LD Signatures (happy to add upon request - office@walt.id)

|            Suite            |    JVM provider    |    JS provider    |
|:---------------------------:|:------------------:|:-----------------:|
|    Ed25519Signature2018     | ld-signatures-java |                   |
|    Ed25519Signature2020     | ld-signatures-java | jsonld-signatures |
| EcdsaSecp256k1Signature2019 | ld-signatures-java |                   |
|      RsaSignature2018       | ld-signatures-java |                   |
|    JsonWebSignature2020     | ld-signatures-java |                   |


## Feature set status

<table>
    <tbody>
        <!-- header -->
        <tr>
            <td align="center" rowspan="3">Feature</td>
            <td align="center" rowspan="3" colspan="2">Category</td>
            <td align="center" colspan="8">Key</td>
        </tr>
        <!-- sub-header key type -->
        <tr>
            <td align="center" colspan="4">Local</td>
            <td align="center" colspan="4">TSE</td>
        </tr>
        <!-- sub-sub-header key algorithm -->
        <tr>
            <!-- local -->
            <td align="center">ed25519</td>
            <td align="center">secp256k1</td>
            <td align="center">secp256r1</td>
            <td align="center">rsa</td>
            <!-- tse -->
            <td align="center">ed25519</td>
            <td align="center">secp256k1</td>
            <td align="center">secp256r1</td>
            <td align="center">rsa</td>
        </tr>
        <!-- content -->
        <!-- sign -->
        <!-- jws -->
        <tr>
            <td align="center" rowspan="2">sign</td>
            <td align="center" colspan="2">jws</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- raw -->
        <tr>
            <td align="center" colspan="2">raw</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- end sign -->
        <tr><td align="center" colspan="10"></td></tr>
        <!-- verify -->
        <!-- jws -->
        <tr>
            <td align="center" rowspan="2">verify</td>
            <td align="center" colspan="2">jws</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- raw -->
        <tr>
            <td align="center" colspan="2">raw</td>
            <!-- local -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <!-- tse -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- end verify -->
        <tr><td align="center" colspan="10"></td></tr>
        <!-- export -->
        <!-- jwk -->
        <!-- private -->
        <tr>
            <td align="center" rowspan="6">export</td>
            <td align="center" rowspan="2">jwk</td>
            <td align="center">private</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- public -->
        <tr>
            <td align="center">public</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- pem -->
        <!-- private -->
        <tr>
            <td align="center" rowspan="2">pem</td>
            <td align="center">private</td>
            <!-- local -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- public -->
        <tr>
            <td align="center">public</td>
            <!-- local -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <!-- tse -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- JsonObject -->
        <!-- private -->
        <tr>
            <td align="center" rowspan="2">JsonObject</td>
            <td align="center">private</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- public -->
        <tr>
            <td align="center">public</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- end export -->
        <tr><td align="center" colspan="11"></td></tr>
        <!-- import -->
        <!-- jwk -->
        <!-- private -->
        <tr>
            <td align="center" rowspan="6">import</td>
            <td align="center" rowspan="2">jwk</td>
            <td align="center">private</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- public -->
        <tr>
            <td align="center">public</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- pem -->
        <!-- private -->
        <tr>
            <td align="center" rowspan="2">pem</td>
            <td align="center">private</td>
            <!-- local -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- public -->
        <tr>
            <td align="center">public</td>
            <!-- local -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <!-- tse -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- raw -->
        <!-- private -->
        <tr>
            <td align="center" rowspan="2">raw</td>
            <td align="center">private</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- public -->
        <tr>
            <td align="center">public</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- end import -->
    </tbody>
</table>


## Vault setup

A Hashicorp Vault's transit secret engine instance is required in order to be able to use
`TSEKey` features.

### Linux

- [binary download](https://developer.hashicorp.com/vault/install)
- package manager

```shell
wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo
tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install vault
```

```shell
vault server -dev -dev-root-token-id="dev-only-token"
```

### Windows

- [binary download](https://developer.hashicorp.com/vault/install)

### MacOS

- [binary download](https://developer.hashicorp.com/vault/install)
- package manager

```shell
brew tap hashicorp/tap
brew install hashicorp/tap/vault
```

### Docker

```shell
docker run -p 8200:8200 --cap-add=IPC_LOCK -e VAULT_DEV_ROOT_TOKEN_ID=myroot -e VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200 hashicorp/vault
```

More details about installing Hashicorp Vault can be found in the Hashicorp Vault
[documentation](https://developer.hashicorp.com/vault/docs/install)
and [tutorials](https://developer.hashicorp.com/vault/install).