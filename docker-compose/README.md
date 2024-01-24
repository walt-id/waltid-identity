# walt.id Identity Package

This package is a docker compose configuration that starts all the services and apps of the identity repo

## Executing The Package

```bash
docker compose up
```

## Services Exposed
port mapping below

- Issuer API: `8000`
- Verifier API: `9000`
- Wallet API: `4545`

## Apps
port mapping below

- Web Wallet: `3000`
- Web Portal: `4000`
- Credential Repo: `5001`


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
