<div align="center">
 <h1>Issuer service</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Credential issuance using the
<a href="https://openid.net/sg/openid4vc/">OpenID for Verifiable Credentials</a>
protocol.<p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>
</div>

Refer to the
[walt.id documentation](https://docs.oss.walt.id/issuer/api/issue-oidc4vc)
for a detailed view on using the issuer service.

## What it provides

- OID4VC service provider for Verifiable Credential issuance
- credential raw signing without using a credential exchange mechanism
- credential signing using an OID4VC credential exchange flow:
    - W3C format (jwt, sdjwt)
    - IEC / ISO18013-5 mdoc / mDL format

A summary of the available issuance flows and credential formats
can be found in the table below:

<table>
    <tbody>
        <!-- header -->
        <tr>
            <td align="center" colspan="2" rowspan="3">Format</td>
            <td align="center" colspan="4">Flow</td>
        </tr>
        <!-- function sub-header -->
        <tr>
            <td align="center" colspan="2">OID4VC</td>
            <td align="center" rowspan="2">raw</td>
            <td align="center" rowspan="2">DIDcomm</td>
        </tr>
        <!-- OID4VC sub-header -->
        <tr>
            <td align="center">single</td>
            <td align="center">batch</td>
        </tr>
        <!-- content -->
        <!-- w3c -->
        <!-- jwt -->
        <tr>
            <td align="center" rowspan="2">w3c</td>
            <td align="center">jwt</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- sdjwt -->
        <tr>
            <td align="center">sd-jwt</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- mdoc -->
        <tr>
            <td align="center" colspan="2">mdoc</td>
            <td align="center">&dash;</td>
            <td align="center">&dash;</td>
            <td align="center">&cross;</td>
            <td align="center">&dash;</td>
        </tr>
  </tbody>
</table>

Issuer service relies on the following walt.id libraries:

- [waltid-sd-jwt library](https://github.com/walt-id/waltid-identity/tree/main/waltid-sdjwt)
  for sd-jwt related processing
- [waltid-openid4vc library](https://github.com/walt-id/waltid-identity/tree/main/waltid-openid4vc)
  for OID4VC interactions
- [waltid-verifiable-credentials library](https://github.com/walt-id/waltid-identity/tree/main/waltid-verifiable-credentials)
  for performing verifiable credential related tasks
- [waltid-did library](https://github.com/walt-id/waltid-identity/tree/main/waltid-did)
  for DID related operations
- [waltid-crypto library](https://github.com/walt-id/waltid-identity/tree/main/waltid-crypto)
  for key related operations

## How to use it

### Endpoints

- `/.well-known/openid-configuration` - service provider configuration
- `/.well-known/openid-credential-issuer` - issuer service configuration
- `/raw/jwt/sign` - sign a jwt-formatted w3c credential without involving an exchange flow
- `/openid4vc/jwt/issue` - sign a jwt-formatted w3c credential and initiate an OID4VC exchange
- `/openid4vc/jwt/issueBatch` - sign a list jwt-formatted w3c credentials and initiate
  an OID4VC exchange
- `/openid4vc/sdjwt/issue`- sign an sd-jwt-formatted w3c credential and initiate an OID4VC exchange
- `/openid4vc/mdoc/issue` - sign an IEC / ISO18013-5 mdoc / mDL credential and initiate
  an OID4VC exchange

### Running from source

1. run the `id.walt.issuer.Mainkt` file
2. the issuer backend is available at: http://localhost:7002

### Using docker

Run the following commands from the waltid-identity root path:

```shell
docker build -t waltid/issuer-api -f waltid-issuer-api/Dockerfile .
docker run -p 7002:7002 -v $PWD/waltid-issuer-api/config:/waltid-issuer-api/config -t waltid/issuer-api
```
