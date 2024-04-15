# walt.id CLI

Manage keys, DIDs, issue Verifiable Credentials, and verify them using the WaltId command line tool.

# How to use

## In development

* `git clone https://github.com/walt-id/waltid-identity.git`
* `cd waltid-identity/waltid-cli`
* `../gradlew clean build`
* `alias waltid="./waltid-cli-development.sh"`

Now, you can run:

| Command                                                                                                                                                                                             | What it does                                                                                                            |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------| 
| `waltid -h`                                                                                                                                                                                         | Print usage message                                                                                                     |
| `waltid --help`                                                                                                                                                                                     | Print WaltId CLI usage message                                                                                          |
| `waltid key -h`                                                                                                                                                                                     | Print WaltId CLI key command usage message                                                                              |
| `waltid key generate -h`                                                                                                                                                                            | Print WaltId CLI key generate command usage message                                                                     |
| `waltid key convert -h`                                                                                                                                                                             | Print WaltId CLI key generate command usage message                                                                     |
| `waltid key generate`                                                                                                                                                                               | Generates a cryptographic key of type Ed25519                                                                           |
| `waltid key generate -tsecp256k1`                                                                                                                                                                   | Generates a cryptographic key of type secp256k1                                                                         |
| `waltid key generate --keyType=RSA`                                                                                                                                                                 | Generates a cryptographic key of type RSA                                                                               |
| `waltid key generate --keyType=RSA -omyRSAKey.json`                                                                                                                                                 | Generates a cryptographic key of type RSA and save it in a file called myRSAKey.json                                    |
| `waltid key generate --keyType=Ed25519 -o myEd25519Key.json`                                                                                                                                        | Generates a cryptographic key of type Ed25519 and save it in a file called myEd25519.json                               |
| `waltid key generate --keyType=secp256k1 -o mySecp256k1Key.json`                                                                                                                                    | Generates a cryptographic key of type Secp256k1 and save it in a file called mySecp256k1.json                           |
| `waltid key generate --keyType=secp256r1 -o mySecp256r1Key.json`                                                                                                                                    | Generates a cruyptographic key of type Secp256r1 and save it in a file called mySecp256r1.json                          |
| `waltid key convert -i myRSAKey.json`                                                                                                                                                               | Convert the given JWK file with an RSA key to the PEM format. The converted file will be called myRSA.pem               |
| `waltid key convert -i myEd25519Key.json`                                                                                                                                                           | ⚠️ Not yet implemented. We don't export Ed25519 keys in PEM format yet.                                                 |
| `waltid key convert -i mySecp256k1Key.json`                                                                                                                                                         | Convert the given JWK with a Secp256k1 key file to the PEM format. The converted file will be called mySecp256k1Key.pem |
| `waltid key convert -i mySecp256r1Key.json`                                                                                                                                                         | Convert the given JWK with a Secp256r1 key file to the PEM format. The converted file will be called mySecp256r1Key.pem |
| `waltid key convert --input=./myRSAKey.pem`                                                                                                                                                         | ⚠️ Not yet implemented.                                                                                                 |
| `waltid key convert --input=./myEd25519Key.pem`                                                                                                                                                     | ⚠️ Not yet implemented.                                                                                                 |
| `waltid key convert --input=./mySecp256k1Key.pem`                                                                                                                                                   | Converts the given PEM with a Sec256k1 key to the JWK format. The converted file will be called mySecp256k1Key.jwk      |
| `waltid key convert --input=./mySecp256r1Key.pem --output=./convertedSecp256r1.jwk`                                                                                                                 | Converts the given PEM with a Sec256r1 key to the JWK format. The converted file will be called convertedSecp256r1.jwk  |
| `openssl ecparam -genkey -name secp256k1 -out secp256k1_by_openssl_pub_pvt_key.pem`                                                                                                                 | Uses OpenSSL to generate a pair of keys in a PEM file.                                                                  |                                                                                                                         |
| `waltid key convert --verbose -i secp256k1_by_openssl_pub_pvt_key.pem`                                                                                                                              | Converts the Secp256k1 key in the given PEM file to the JWK format.                                                     |
| `waltid did -h`                                                                                                                                                                                     | Print WaltID CLI DID command usage message                                                                              |
| `waltid did create -h`                                                                                                                                                                              | Print WaltID CLI DID create command usage message                                                                       |
| `waltid did create`                                                                                                                                                                                 | Creates a new did:key                                                                                                   |
| `waltid did create --key=./myRSAKey.json`                                                                                                                                                           | Creates a new did:key with the key provided in the spoecified file                                                      | 
| `waltid vc sign --key=./myEd25519Key.json --subject=did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9 --issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV ./myCredential.json` | Signs a verifiable credential JSON. The VC needs [to be created first](#1.-create-a-vc).                                |
| `waltid vc sign --key=./myEd25519Key.json --subject=did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9 ./myCredential.json`                                                                   | Signs a verifiable credential JSON with a generated Issuer DID   .                                                      |

## In production

We are still preparing a nice distribution strategy. It will be available soon.

In the meantime, you can use Gradle to generate the distribution package:

* `cd waltid-identity/waltid-cli`
* `../gradlew distZip` or `../gradlew distTar`

A `waltid-cli-1.0.0-SNAPSHOT` file will be created in the `build/distributions` directory.

```bash
$ pwd
.../waltid-identity/waltid-cli

$ ls -la build/distributions/
total 67024
drwxr-xr-x@  3 waltian  staff        96 Feb 20 18:41 .
drwxr-xr-x@ 12 waltian  staff       384 Feb 20 18:41 ..
-rw-r--r--@  1 waltian  staff  34062716 Feb 20 18:41 waltid-cli-1.0.0-SNAPSHOT.zip
```

Extract it somewhere and you will find two folders inside:

```bash
$ unzip  build/distributions/waltid-cli-1.0.0-SNAPSHOT.zip -d /tmp
(...)

$ ll /tmp/waltid-cli-1.0.0-SNAPSHOT
total 0
drwxr-xr-x@  4 waltian  wheel   128B Feb 20 18:41 bin
drwxr-xr-x@ 72 waltian  wheel   2.3K Feb 20 18:41 lib
```

The `bin` folder has the CLI execution script compatible with common operating systems.

Set execution rights to the script

`$ chmod a+x /tmp/waltid-cli-1.0.0-SNAPSHOT/bin/waltid`

Add the `/tmp/waltid-cli-1.0.0-SNAPSHOT/bin` to the PATH.

`$ export PATH="/tmp/waltid-cli-1.0.0-SNAPSHOT/bin:$PATH"`

Execute the walt.id CLI

`$ waltid`

# Supported commands

| Command | Subommand | Description                                    | Ready to use |
|:-------:|:---------:|------------------------------------------------|:------------:|
|   key   | generate  | Generates a new cryptographic key.             |      ✔️      |
|         |  convert  | Convert key files between PEM and JWK formats. |              |
|         |           | <li> from PEM to JWK</li>                      |      ✔️      |
|         |           | <li>Convertion from JWK to PEM</li>            |      ✖️      |
|   did   |  create   | Create a new DID.                              |      ✔️      | 
|         |  resolve  | Resolve a DID.                                 |      ✖️      |  
|   vc    |   sign    | Sign a verifiable credential                   |      ✔️      |
|         |  verify   | Verify a verifiable credential                 |      ✖️      |
|         |    ...    |                                                |              |

# Reference

## `waltid` command

```bash
Usage: waltid [<options>] <command> [<args>]...

  walt.id CLI

  ╭────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╮
  │    The walt.id CLI is a command line tool that allows you to onboard and                                           │
  │    use a SSI (Self-Sovereign-Identity) ecosystem. You can manage                                                   │
  │    cryptographic keys, generate and register W3C Decentralized                                                     │
  │    Identifiers (DIDs) as well as create, issue & verify W3C Verifiable                                             │
  │    credentials (VCs).                                                                                              │
  │                                                                                                                    │
  │    Example commands are:                                                                                           │
  │                                                                                                                    │
  │    Print usage instructions                                                                                        │
  │    -------------------------                                                                                       │
  │    waltid -h                                                                                                       │
  │    waltid --help                                                                                                   │
  │    waltid key -h                                                                                                   │
  │    waltid key generate -h                                                                                          │
  │    waltid key convert -h                                                                                           │
  │    waltid did -h                                                                                                   │
  │    waltid did create -h                                                                                            │
  │                                                                                                                    │
  │    Key generation                                                                                                  │
  │    ---------------                                                                                                 │
  │    waltid key generate                                                                                             │
  │    waltid key generate -t secp256k1                                                                                │
  │    waltid key generate --keyType=RSA                                                                               │
  │    waltid key generate --keyType=RSA -o myRsaKey.json                                                              │
  │                                                                                                                    │
  │    Key conversion                                                                                                  │
  │    ---------------                                                                                                 │
  │    waltid key convert --input=myRsaKey.pem                                                                         │
  │                                                                                                                    │
  │    DID creation                                                                                                    │
  │    -------------                                                                                                   │
  │    waltid did create                                                                                               │
  │    waltid did create -k myKey.json                                                                                 │
  │                                                                                                                    │
  │    VC signing                                                                                                      │
  │    -------------                                                                                                   │
  │    waltid vc sign --key=./myKey.json --subject=did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9 ./myVC.json│
  │    waltid vc sign --key=./myKey.json \                                                                             │
  │                   --subject=did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9\                              │
  │                   --issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV\                               │
  │                   ./myVC.json                                                                                      │
  ╰────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯

Common Options:
  --verbose  Set verbose mode ON

Options:
  -h, --help  Show this message and exit

Commands:
  key  Key management features
  did  DID management features
  vc   Issuing, presenting and verifying Verifiable Credentials
```

## `waltid key` command

```bash
Usage: waltid key [<options>] <command> [<args>]...

  Key management features

Options:
  -h, --help  Show this message and exit

Commands:
  generate  Generates a new cryptographic key.
  convert   Convert key files between PEM and JWK formats.
```

## `waltid key generate` command

```bash
Usage: waltid key generate [<options>]

  Generates a new cryptographic key.

Options:
  -t, --keyType=(Ed25519|secp256k1|secp256r1|RSA)
                       Key type to use. Possible values are: [Ed25519 |
                       secp256k1 | secp256r1 | RSA]. Default value is Ed25519
  -o, --output=<path>  File path to save the generated key. Default value is
                       <keyId>.json
  -h, --help           Show this message and exit
```

## `waltid key convert` command

```bash
Usage: waltid key convert [<options>]

  Convert key files between PEM and JWK formats.

Options:
  -i, --input=<path>       The input file path. Accepted formats are: JWK and
                           PEM
  -o, --output=<path>      The output file path. Accepted formats are: JWK and
                           PEM. If not provided the input filename will be used
                           with a different extension.
  -p, --passphrase=<text>  Passphrase to open an encrypted PEM
  -h, --help               Show this message and exit
```

## `waltid did` command

```bash
Usage: waltid did [<options>] <command> [<args>]...

  DID management features

Options:
  -h, --help  Show this message and exit

Commands:
  create   Create a brand new Decentralized Identity
  resolve  Resolve the decentralized identity passed as an argument, i.e. it retrieves the sovereign identity document addressed by the given DID.
```

## `waltid did create` command

```bash
Usage: waltid did create [<options>]

  Create a brand new Decentralized Identity

Options:
  -m, --method=(KEY|JWK|WEB|EBSI|CHEQD|IOTA)  The DID method to be used.
  -k, --key=<path>                            The Subject's key to be used. If none is provided, a new one will be generated.
  -h, --help                                  Show this message and exit
```

## `waltid vc` command

```bash
Usage: waltid vc [<options>] <command> [<args>]...

  Issuing, presenting and verifying Verifiable Credentials

Options:
  -h, --help  Show this message and exit

Commands:
  sign  Signs a Verifiable Credential.
```

## `waltid vc sign` command

```bash
Usage: waltid vc sign [<options>] <vc>

  Signs a Verifiable Credential.

Options:
  -k, --key=<path>      A core-crypto key representation to sign the credential (required)
  -i, --issuer=<text>   The verifiable credential's issuer DID
  -s, --subject=<text>  The verifiable credential's subject DID (required)
  -o, --overwrite       Flag to overwrite the signed output file if it exists
  -h, --help            Show this message and exit

Arguments:
  <vc>  the verifiable credential file (required)
```

Before signing a VC, we need
to [create a VC](https://docs.oss.walt.id/issuer/sdks/manage-credentials/sign/w3c-credential), which can be done
manually or programmatically.

Since our focus here is on the CLI and not the Walt.id API, let's see what it's like to manually create a VC.

#### 1. Create a VC

Walt.id
provides [a repository of VCs](https://docs.oss.walt.id/issuer/sdks/manage-credentials/sign/w3c-credential#manual-create-credential)
that can be used as templates for creating your own. Choose the one that best suits your needs.

Let's choose the [OpenBadgeCredential](https://credentials.walt.id/credentials/openbadgecredential), adjust it
accordingly and save it in a file called `openbadgecredential_sample.json`.

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
  ],
  "id": "urn:uuid:123",
  "type": [
    "VerifiableCredential",
    "OpenBadgeCredential"
  ],
  "name": "Coffee Lover Badge",
  "issuer": {
    "type": [
      "Profile"
    ],
    "id": "did:example:123",
    "name": "The Coffee Palace",
    "url": "https://en.wikipedia.org/wiki/Coffee_palace",
    "image": "https://www.shutterstock.com/image-vector/coffee-palace-special-lover-600nw-261490277.jpg"
  },
  "issuanceDate": "2023-07-20T07:05:44Z",
  "expirationDate": "2033-07-20T07:05:44Z",
  "credentialSubject": {
    "id": "did:example:456",
    "type": [
      "AchievementSubject"
    ],
    "achievement": {
      "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
      "type": [
        "Achievement"
      ],
      "name": "Coffee Lover",
      "description": "A true lover of good coffee",
      "criteria": {
        "type": "Criteria",
        "narrative": "Able to carry out detailed sensory analysis of different coffee tastings."
      },
      "image": {
        "id": "https://www.teepublic.com/magnet/4067432-certified-coffee-lover-caffeine-addict",
        "type": "Image"
      }
    }
  }
}
```

#### 2. Sign the VC

```
$ waltid vc sign --key myKey.json 
                 --subject did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9 
                 openbadgecredential_sample.json
```

# Note for Windows users

If the input file path is not quoted, all backslashes need to be escaped on Windows.

| Command                                         | Works? |
|:------------------------------------------------|:------:|
| `waltid did create -m KEY -k "C:\foo\key.json"` |   ✅    |
| `waltid did create -m KEY -k C:\\foo\\key.json` |   ✅    |
| `waltid did create -m KEY -k C:/foo/key.json`   |   ?    |
| `waltid did create -m KEY -k C:\foo\key.json`   |   ❌    |

# Compatibility

This project is still a work in progress. As such, not all features are already implemented.

## Key Management

### key generate

* Supported key types
  * Ed25519 ✅
  * secp256k1 ✅
  * secp256r1 ✅
  * RSA ✅
* Export formats
  * JWK ✅
  * PEM
    * RSA ✅
    * ❌

### key convert

* Input formats
  * JWK
    * RSA ✅
    * ed25519 ❌
    * secp256k1 ✅
    * secp256r1 ✅
  * PEM
    * RSA ❌
    * ed25519 ❌
    * secp256k1 ✅
    * secp256r1 ✅
* PEM Content
  * RSA Private Key ✅
  * RSA Public Key ✅
  * RSA Encrypted Private Key ✅
  * Ed25519 ❌
  * secp256k1 Public + Private Key ✅
  * secp256r1 ✅

### DID create

* Supported DID methods
  * KEY ✅
  * JWK ❌
  * WEB ❌
  * EBSI ❌
  * CHEQD ❌
  * IOTA ❌