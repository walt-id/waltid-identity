# walt.id Identity Package

This package is a docker compose configuration that starts all the services and apps of the identity repo

## Executing The Package

```bash
docker compose up
```

## Services Exposed
port mapping below

- Wallet API: `7001`
- Issuer API: `7002`
- Verifier API: `7003`

## Apps
port mapping below

- Web Wallet: `7101`
- Web Portal: `7102`
- Credential Repo: `7103`


## Configurations

Config locations:

- wallet API: `wallet-backend`
- issuer API: `issuer-api/config`
- verifier API: `verifier-api/config`
- ingress: `ingress.conf`
- environment: `.env`

## Troubleshooting

---
#### Display of VC verification result on success page of portal doesn't work

We are working on fixing this issue.

---

#### Updating ports doesn't work

Make sure the ports are also updated in:
- ingress.conf
- walletkit/config
  - issuer-config.json
  - verifier-config.json
  - wallet-config.json
- wallet-backend/config
  - wallet.conf
  - web.conf
