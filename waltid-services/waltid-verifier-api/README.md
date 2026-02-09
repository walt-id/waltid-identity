<div align="center">
 <h1>Verifier service</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Credential verification using the
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
    <img src="https://img.shields.io/badge/🟠%20Planned%20Deprecation-orange?style=for-the-badge&logo=clock" alt="Status: Planned Deprecation" />
  <br/>
  <em>This project is still supported by the development team at walt.id, but is planned for deprecation sometime in Q2 2026.<br />We encourage users to migrate to using alternative libraries listed below.</em>
  </p>
</div>


Refer to the
[walt.id documentation](https://docs.walt.id/community-stack/verifier/getting-started)
for a detailed view on using the verifier service, or learn more about OpenID4VP [here](http://localhost:3000/concepts/data-exchange-protocols/openid4vp)

This api only supports OpenID4VP draft 14 and draft 20. For OpenID4VP 1.0, use [waltid-verifier-api2](../waltid-verifier-api2).

## What it provides

- OID4VC service provider for Verifiable Presentations (OpenID4VP draft 14 and draft 20)
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
            <td align="center" >OID4VC</td>
            <td align="center" >DIDComm</td>
        </tr>
        <!-- content -->
        <!-- w3c -->
        <!-- jwt -->
        <tr>
            <td align="center" rowspan="2">W3C</td>
            <td align="center" >jwt</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- sdjwt -->
        <tr>
            <td align="center" >sd-jwt</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- SD-JWT VC (IETF) -->
        <tr>
            <td align="center" colspan="2">SD-JWT VC (IETF)</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- mdoc -->
        <tr>
            <td align="center" colspan="2">mDL/mdoc</td>
            <td align="center" >&cross;</td>
            <td align="center" >&dash;</td>
        </tr>
  </tbody>
</table>

Verifier service relies on the following walt.id libraries:

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

- `/openid4vc/verify` - initialize an OID4VC presentation session
- `/openid4vc/verify/{state}` - submit a Verifiable Presentation in direct_post response mode
- `/openid4vc/session/{id}` - get the current state and result information about
  an ongoing OID4VC presentation session
- `/openid4vc/pd/{id}` - get the presentation definition of an ongoing OID4VC presentation session
- `/openid4vc/policy-list` - get the list of the registered policies

### Running from source

1. run the `id.walt.verifier.Mainkt` file
2. the verifier backend is available at: http://localhost:7003

### Docker images

Run the following commands from the waltid-identity root path:

```bash
# Development (local Docker daemon, single-arch)
./gradlew :waltid-services:waltid-verifier-api:jibDockerBuild
# image: waltid/verifier-api:<version>
```

```bash
# Production (multi-arch push to your registry)
export DOCKER_USERNAME=<your-dockerhub-namespace>
export DOCKER_PASSWORD=<your-dockerhub-token>
./gradlew :waltid-services:waltid-verifier-api:jib
# image: docker.io/<DOCKER_USERNAME>/verifier-api:<version>
```

Note: multi-arch images require a registry push. Local tar output is single-arch only.

Run the container:

```bash
docker run -p 7003:7003 waltid/verifier-api -- --webPort=7003 --baseUrl=http://localhost:7003
```

## Steps for LSP Potential Interop Event

https://verifier.portal.walt-test.cloud/swagger/index.html

- Get mDL from our temporary issuer:
    - https://verifier.portal.walt-test.cloud/swagger/index.html#/LSP%20POTENTIAL%20Interop%20Event/post_lsp_potential_issueMdl
    - Put a device key (holder key) in JWK format in the POST body. E.g.:

      ```jsx
      {"kty":"EC","crv":"P-256","x":"BHKLNOrGFlZFA12yBesURGUxJntXRwdkdzl7xYoVoj0","y":"himu9UvfAEcTwYkB6itPP6n4d_brZvLJX3ld62OJIC4"}
    - Here's an example mDL:
  ```
  a267646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c6973737565725369676e6564a26a6e616d65537061636573a1716f72672e69736f2e31383031332e352e3183d8185852a4686469676573744944006672616e646f6d50c37a74c6b22eb370d1d5cadaacfdd80e71656c656d656e744964656e7469666965726b66616d696c795f6e616d656c656c656d656e7456616c756563446f65d8185852a4686469676573744944016672616e646f6d50cb73af5be79d9621e8dfcfcb5158599a71656c656d656e744964656e7469666965726a676976656e5f6e616d656c656c656d656e7456616c7565644a6f686ed818585ba4686469676573744944026672616e646f6d506b0f59bbb169a2934138c08b6efa114071656c656d656e744964656e7469666965726a62697274685f646174656c656c656d656e7456616c7565d903ec6a313939302d30312d31356a697373756572417574688443a10126a1182159014b308201473081eea003020102020839edc87a9a78f92a300a06082a8648ce3d04030230173115301306035504030c0c4d444f4320524f4f54204341301e170d3234303530323133313333305a170d3235303530323133313333305a301b3119301706035504030c104d444f432054657374204973737565723059301306072a8648ce3d020106082a8648ce3d030107034200041b4448341885fa84140f77790c69de810b977a7236f490da306a0cbe2a0a441379ddde146b36a44b6ba7bbc067b04b71bad4b692a4616013d893d440ae253781a320301e300c0603551d130101ff04023000300e0603551d0f0101ff040403020780300a06082a8648ce3d04030203480030450221008e70041000ddec2a230b2586ecc59f8acd156f5d933d9363bc5e2263bb0ab69802201885a8b537327a69b022620f07c5c45d6293b86eed927a3f04e82cc51cadf8635901c3d8185901bea66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a1716f72672e69736f2e31383031332e352e31a3005820a7ee7e23f54e51a298aef1ce2f4ba9fcbe24be13939e92204e79a17fc76cb9f60158202842dc5c4ac3136e88d048a791e7f369ecc82e27de55e54f088e39477bd9cad30258204bdd34978f67299437e5185f7d307657a1135088e206eb1f0e2fcf5ac3e3329a6d6465766963654b6579496e666fa1696465766963654b6579a40102200121582004728b34eac6165645035db205eb14446531267b5747076477397bc58a15a23d2258208629aef54bdf004713c18901ea2b4f3fa9f877f6eb66f2c95f795deb6389202e67646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667369676e6564c0781e323032342d30352d30375430383a35373a32372e3235323030333533305a6976616c696446726f6dc0781e323032342d30352d30375430383a35373a32372e3235323031303236315a6a76616c6964556e74696cc0781e323032352d30352d30375430383a35373a32372e3235323031313436355a5840358866d3bd668ad36fa37233bf746dd41e72efc3e33fe465ccff8f3ac61ca6d33e3a89bc96b7d63ed2c4daff394e92b6d6db3e8acc7dad8e911aee0c43fc0557
  ```

- Save mDL to your wallet
- Use the verifier API to create a openid4vc verification request:
    - https://verifier.portal.walt-test.cloud/swagger/index.html#/Credential%20Verification/post_openid4vc_verify
    - Use these arguments:
        - authorizeBaseUrl: mdoc-openid4vp://
        - responseMode: direct_post_jwt
    - Use a POST body like this:

      ```jsx
      {
          "request_credentials": [
              "org.iso.18013.5.1.mDL"
          ]
      }
      ```

    - You get a link like this, which can be rendered as a QR code for your wallet:

      ```jsx
      mdoc-openid4vp://?response_type=vp_token&client_id=&response_mode=direct_post.jwt&state=AqkYhRmbHpEX&presentation_definition_uri=http%3A%2F%2Flocalhost%3A7003%2Fopenid4vc%2Fpd%2FAqkYhRmbHpEX&client_id_scheme=redirect_uri&client_metadata=%7B%22jwks%22%3A%7B%22keys%22%3A%5B%7B%22kty%22%3A%22EC%22%2C%22crv%22%3A%22P-256%22%2C%22kid%22%3A%22b6ERrLYBSfsJ_nFLMgw6jtPGgzWSxyZX91RlRTvL-c4%22%2C%22x%22%3A%22lZMnJXRAgZ3YQAtFpqSaAywEb34XsWkP2aN3C9ZJwz8%22%2C%22y%22%3A%22rHNeTy9wUOmb4RH2R8YRKZxMadc55qWe_0TGUwgc0Hk%22%2C%22use%22%3A%22enc%22%2C%22alg%22%3A%22ECDH-ES%22%7D%5D%7D%2C%22authorization_encrypted_response_alg%22%3A%22ECDH-ES%22%2C%22authorization_encrypted_response_enc%22%3A%22A256GCM%22%7D&nonce=af1b4ddd-db15-47b8-bcf7-7773c4d75e82&response_uri=http%3A%2F%2Flocalhost%3A7003%2Fopenid4vc%2Fverify%2FAqkYhRmbHpEX
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
