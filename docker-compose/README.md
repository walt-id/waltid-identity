# walt.id Identity Package

This package is a docker compose configuration that starts all the services and apps of the identity repo

## Executing The Package

```bash
docker-compose pull
docker-compose up
```

Note: If you are facing issues with the containers, try running the following command to remove the existing containers and then run the
above command again.

```bash
docker-compose down
```

## Port mapping

### Services

- Wallet API: [http://localhost:7001](http://localhost:7001)
- Issuer API: [http://localhost:7002](http://localhost:7002)
- Verifier API: [http://localhost:7003](http://localhost:7003)

### Apps

- Web Wallet: [http://localhost:7101](http://localhost:7101)
- Web Portal: [http://localhost:7102](http://localhost:7102)
- Credential Repo: [http://localhost:7103](http://localhost:7103)

## Configurations

- wallet API:
    - `wallet-api/config` - wallet configuration
    - `wallet-api/walt.yaml` - nft configuration
- issuer API:
    - `issuer-api/config`
- verifier API:
    - `verifier-api/config`
- ingress:
    - `Caddyfile`

[//]: # (## Environment)

[//]: # ()
[//]: # (- main:)

[//]: # (    - `.env` - stores the common environment variables, such as port numbers,)

[//]: # (      version-tag, database-engine selection, etc.)

[//]: # (- postgres:)

[//]: # (    - `postgres/postgres.env` - stores postgres specific variables, e.g. admin user, etc.)

[//]: # (    - `pgadmin.env` - stores pgAdmin specific variables, e.g. admin user, etc.)

[//]: # (- microsoft sql-server:)

[//]: # (    - `mssql/mssql.env` - stores mssql specific variables, e.g. sql-server edition, etc.)

[//]: # ()
[//]: # (Variables from `.env` are propagated automatically down to reverse proxy configurations)

[//]: # (&#40;Caddyfile&#41; and also api configurations &#40;wallet, issuer, verifier&#41;.)

## How to

[//]: # (### Select a database engine)

[//]: # ()
[//]: # (- browse `.env` file)

[//]: # (- set `DATABASE_ENGINE` to one of:)

[//]: # (    - sqlite)

[//]: # (    - postgres)

[//]: # (    - mssql)

This value will be used also by compose profile so only the required services are started.

### Update port number

- browse `.env` file
- update the desired port number

This value will be used by reverse proxy (and services configs, if any).

### Select an identity stack version

- browse `.env` file
- update `VERSION_TAG` to a specific image version (e.g. a release version)
    - if not set, `latest` tag is used

## Troubleshooting

---

#### Display of VC verification result on success page of portal doesn't work

We are working on fixing this issue.

---

#### Updating ports doesn't work

Make sure the ports are also updated in:

- Caddyfile
- issuer-api/config
    - issuer-service.conf
    - web.conf
- verifier-api/config
    - verifier-service.conf
    - web.conf
- wallet-api/config
    - web.conf
    - db.conf
