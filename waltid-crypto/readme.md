# walt.id Crypto

## Platforms available:

- Java / JVM
- JS / Node.js or WebCrypto
- Native / libsodium & OpenSSL (todo)

## Signature schemes available:

|  EdDSA  | JOSE ID | Description        |
|:-------:|:-------:|:-------------------|
| Ed25519 |  EdDSA  | EdDSA + Curve25519 |

|   ECDSA   | JOSE ID | Description                                                     |
|:---------:|:-------:|:----------------------------------------------------------------|
| secp256r1 |  ES256  | ECDSA + SECG curve secp256r1 ("NIST P-256")                     |
| secp256k1 | ES256K  | ECDSA + SECG curve secp256k1 (Koblitz curve as used in Bitcoin) |

| RSA | JOSE ID |
|:---:|:-------:|
| RSA |  RS256  |

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
            <td align="center" rowspan="3">Category</td>
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
        <!-- export -->
        <!-- jwk -->
        <tr>
            <td align="center" rowspan="3">export</td>
            <td align="center">jwk</td>
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
        <!-- pem -->
        <tr>
            <td align="center">pem</td>
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
        <!-- JsonObject -->
        <tr>
            <td align="center">JsonObject</td>
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
        <!-- end export -->
        <tr><td align="center" colspan="10"></td></tr>
        <!-- import -->
        <!-- jwk -->
        <tr>
            <td align="center" rowspan="3">import</td>
            <td align="center">jwk</td>
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
        <!-- pem -->
        <tr>
            <td align="center">pem</td>
            <!-- local -->
            <td align="center">&cross;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- raw -->
        <tr>
            <td align="center">raw</td>
            <!-- local -->
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <!-- tse -->
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
        </tr>
        <!-- end import -->
        <tr><td align="center" colspan="10"></td></tr>
        <!-- sign -->
        <!-- jws -->
        <tr>
            <td align="center" rowspan="2">sign</td>
            <td align="center">jws</td>
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
        <!-- raw -->
        <tr>
            <td align="center">raw</td>
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
        <!-- end sign -->
        <tr><td align="center" colspan="10"></td></tr>
        <!-- verify -->
        <!-- jws -->
        <tr>
            <td align="center" rowspan="2">verify</td>
            <td align="center">jws</td>
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
        <!-- raw -->
        <tr>
            <td align="center">raw</td>
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
        <!-- end verify -->
    </tbody>
</table>
