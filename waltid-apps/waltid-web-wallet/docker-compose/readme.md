# Web-wallet package

This package is a docker compose configuration which starts all the services required to
run a complete credential flow:
- issue - using issuer-portal
- store - using web-wallet
- verify - using verifier-portal

## Services

The complete list of services and their port mapping is following:
- web-wallet-frontend: `3000`
- web-wallet-backend: `4545` (not published)
- web-portal: `4000`
- vc-repo: `5000`
- issuer: `8000`
- verifier: `9000`

## Configurations

Config locations:

- web-wallet: `wallet-backend/config`
- issuer: `issuer/config`
- verifier: `verifier/config`
- ingress: `ingress.conf`
- environment: `.env`

## Examples

### Issue VerifiableId credential

1. go to issuer-portal (http://localhost:8000)
2. select _Verifiable ID document_
3. click '_Start issuance_'
4. on the popup, click '_walt.id web wallet_'
   1. redirect to web-wallet
   2. sign in to web-wallet
5. on the '_Receive single credential_' page, click '_Accept_'
   1. redirect to credentials list page

### Verify VerifiableId credential

1. go to verifier-portal (http://localhost:9000)
2. click '_Connect Wallet using Verifiable ID_'
3. redirect to web-wallet
   1. sign in to web-wallet
4. on the '_Present_' page, click '_Accept_'
   1. redirect to verification result

## Troubleshooting

---

#### Redirect to web-wallet doesn't work (http://host.docker.internal:3000)

Make sure the hostname mapping is available in the hosts file:
- Linux: `/etc/hosts`
- MacOS: `/private/etc/hosts`
- Windows: `C:\Windows\System32\drivers\etc\hosts`

It should contain a record similar to `127.0.0.1 host.docker.internal`.

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