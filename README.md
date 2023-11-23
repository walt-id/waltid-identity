# walt.id Identity

## Credentials

### (to be) Supported combinations:

| Credential type | Signature type | Notes                                               |
|-----------------|----------------|-----------------------------------------------------|
| W3C VC          | JOSE (JWT/JWS) | Simplest format to start                            |
| SD-JWT          | JOSE (JWT/JWS) | Selective disclosure                                |
| W3C VC          | JSON-LD / Soon | Signature type not recommended for new applications |
| mdoc            | COSE1          | Supports mDL & mID                                  |

## Docker container builds:

```shell
docker build -t waltid/issuer -f docker/issuer.Dockerfile .
docker run -p 7000:7000 waltid/issuer --webHost=0.0.0.0 --webPort=7000 --baseUrl=http://localhost:7000
```

```shell
docker build -t waltid/verifier -f docker/verifier.Dockerfile .
docker run -p 7001:7001 waltid/verifier --webHost=0.0.0.0 --webPort=7001 --baseUrl=http://localhost:7001
```