# walt.id Identity Package

This package is a docker compose configuration that starts all the services and apps of the identity repo

## Executing The Package

```bash
docker-compose pull
docker-compose up
```

Note: If you are facing issues with the containers, try running the following command to remove the existing containers and then run the above command again.

```bash
docker-compose down
```

## Services Exposed
port mapping below

- Wallet API: [http://localhost:7001](http://localhost:7001)
- Issuer API: [http://localhost:7002](http://localhost:7002)
- Verifier API: [http://localhost:7003](http://localhost:7003)

## Apps
port mapping below

- Web Wallet: [http://localhost:7101](http://localhost:7101)
- Web Portal: [http://localhost:7102](http://localhost:7102)
- Credential Repo: [http://localhost:7103](http://localhost:7103)


## Configurations

Config locations:

- wallet API: `wallet-backend`
- issuer API: `issuer-api/config`
- verifier API: `verifier-api/config`
- ingress: `Caddyfile`
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
