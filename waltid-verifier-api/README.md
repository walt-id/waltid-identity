<div align="center">
 <h1>Verifier service</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Credential verification using the
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
[walt.id documentation](https://docs.oss.walt.id/verifier/api/verify-oidc4vc)
for a detailed view on using the verifier service.

## What it provides

- OID4VC service provider for Verifiable Presentations
- OID4VC presentation session initialization and retrieval
- *vp_token* response verification
- presentation definition retrieval

A summary of the available verification and credential formats
can be found in the table below:

<table>
    <tbody>
        <!-- header -->
        <tr>
            <td align="center" colspan="2">Format</td>
            <td align="center">OID4VC</td>
            <td align="center">DIDComm</td>
        </tr>
        <!-- content -->
        <!-- w3c -->
        <!-- jwt -->
        <tr>
            <td align="center" rowspan="2">w3c</td>
            <td align="center">jwt</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- sdjwt -->
        <tr>
            <td align="center">sd-jwt</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- mdoc -->
        <tr>
            <td align="center" colspan="2">mdoc</td>
            <td align="center">&cross;</td>
            <td align="center">&dash;</td>
        </tr>
  </tbody>
</table>

Verifier service relies on the following walt.id libraries:

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

- `/openid4vc/verify` - initialize an OID4VC presentation session
- `/openid4vc/verify/{state}` - submit a Verifiable Presentation in direct_post response mode
- `/openid4vc/session/{id}` - get the current state and result information about
  an ongoing OID4VC presentation session
- `/openid4vc/pd/{id}` - get the presentation definition of an ongoing OID4VC presentation session
- `/openid4vc/policy-list` - get the list of the registered policies

### Running from source

1. run the `id.walt.verifier.Mainkt` file
2. the verifier backend is available at: http://localhost:7003

### Using docker

Run the following commands from the waltid-identity root path:

```shell
docker build -t waltid/verifier-api -f waltid-verifier-api/Dockerfile .
docker run -p 7003:7003 waltid/verifier-api --webHost=0.0.0.0 --webPort=7003 --baseUrl=http://localhost:7003
```
