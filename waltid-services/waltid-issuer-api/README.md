<div align="center">
 <h1>Issuer service</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Credential issuance using the
<a href="https://openid.net/sg/openid4vc/">OpenID for Verifiable Credentials</a>
protocol.</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

Refer to the
[walt.id documentation](https://docs.walt.id/community-stack/issuer/getting-started)
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
            <td align="center" >single</td>
            <td align="center" >batch</td>
        </tr>
        <!-- content -->
        <!-- w3c -->
        <!-- jwt -->
        <tr>
            <td align="center" rowspan="2">W3C</td>
            <td align="center" >jwt</td>
            <td align="center" >&check;</td>
            <td align="center" >&check;</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- sdjwt -->
        <tr>
            <td align="center" >sd-jwt</td>
            <td align="center" >&check;</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- SD-JWT VC (IETF) -->
        <tr>
            <td align="center" colspan="2">SD-JWT VC (IETF)</td>
            <td align="center" >&check;</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- mdoc -->
        <tr>
            <td align="center" colspan="2">mDL/mdoc</td>
            <td align="center" >&dash;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&dash;</td>
        </tr>
  </tbody>
</table>

Issuer service relies on the following walt.id libraries:

- [waltid-sd-jwt library](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/waltid-sdjwt)
  for sd-jwt related processing
- [waltid-openid4vc library](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/waltid-openid4vc)
  for OID4VC interactions
- [waltid-w3c-credentials library](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/waltid-w3c-credentials)
  for performing verifiable credential related tasks
- [waltid-did library](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/waltid-did)
  for DID related operations
- [waltid-crypto library](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/waltid-crypto)
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

### Docker images

Run the following commands from the waltid-identity root path:

```bash
# Development (local Docker daemon, single-arch)
./gradlew :waltid-services:waltid-issuer-api:jibDockerBuild
# image: waltid/issuer-api:<version>
```

```bash
# Production (multi-arch push to your registry)
export DOCKER_USERNAME=<your-dockerhub-namespace>
export DOCKER_PASSWORD=<your-dockerhub-token>
./gradlew :waltid-services:waltid-issuer-api:jib
# image: docker.io/<DOCKER_USERNAME>/issuer-api:<version>
```

Note: multi-arch images require a registry push. Local tar output is single-arch only.

Run the container:

```bash
docker run -p 7002:7002 waltid/issuer-api -- --webPort=7002 --baseUrl=http://localhost:7002
```

Or, run with local config directory:

```bash
docker run -p 7002:7002 -v $PWD/waltid-services/waltid-issuer-api/config:/waltid-issuer-api/config -t waltid/issuer-api
```

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
