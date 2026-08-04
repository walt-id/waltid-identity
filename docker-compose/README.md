# walt.id Identity Docker Environment

This directory contains the Docker Compose configuration that sets up and runs the services and applications of the
walt.id Identity Stack.
You can either run the latest release using pre-built Docker images or build your images locally.

## Prerequisites

Ensure you have the following tools installed:

- [Docker](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)

---

## Quick Start with Latest Release Images

If you prefer to run the services using latest release pre-built Docker images, follow these steps:

### Pull the Latest Release Images

Start by pulling the latest release Docker images for the services:

```bash
$ docker compose pull
```

This ensures that you're using the most recent release images from the Docker registry.

### Start the Services

Once the images are pulled, start the services by running:

```bash
$ docker compose up
```

*Note:* If you are facing issues with the containers, remove the existing ones with `docker compose down` (add `-v` to
also drop the volumes) and run the command above again.

### Stop the Services

```bash
$ docker compose down
```

### Tear down the Services

```bash
$ docker compose down -v
```

*Note:*
The version of the images pulled is controlled by the `VERSION_TAG` in the `.env` file. By default, it is set to latest,
which pulls the most recent release of the Docker images.
If you prefer to use a specific version, such as a past release, modify the `VERSION_TAG` in the `.env` file before
pulling the images.

## Building and Running Services Locally

### Prerequisites

Ensure you have the following tools installed:

- [Docker](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Java 21 SDK](https://adoptium.net/temurin/releases/?version=21)

### Update the VERSION_TAG

Before building locally, ensure the correct version is specified in the `.env` file.
Update the `VERSION_TAG` variable to version `1.0.0-SNAPSHOT`

### Build API Services Docker Images Locally (development)

API Services Docker Images are built with the Ktor Gradle plugin. This
requires Java SDK 21 installed. You build the images by
executing the commands:

```shell
$ cd waltid-identity/
$
$ nano docker-compose/.env # set variable VERSION_TAG=1.0.0-SNAPSHOT so the build version is used
$
$ ./gradlew jibDockerBuild # build docker images
$
$ docker image ls # verify images are built and published in the local docker registry
REPOSITORY             TAG              IMAGE ID       CREATED        SIZE
waltid/issuer-api      1.0.0-SNAPSHOT   0d8752382eae   55 years ago   359MB
waltid/issuer-api2     1.0.0-SNAPSHOT   1a4b62c9f0de   55 years ago   361MB
waltid/verifier-api    1.0.0-SNAPSHOT   5ce8428d031a   55 years ago   353MB
waltid/verifier-api2   1.0.0-SNAPSHOT   9c1de4470bb8   55 years ago   355MB
waltid/wallet-api      1.0.0-SNAPSHOT   712427b1f532   55 years ago   575MB
waltid/wallet-api2     1.0.0-SNAPSHOT   3fa0c7185ed2   55 years ago   578MB

$
```

### Build the Docker Webapp Images Locally

```bash
$ cd docker-compose
$ docker compose build
```

### Start the Services

```bash
$ docker compose up
```

### Starting services selectively

It is possible to start services selectively, including their dependencies.

#### Start the demo wallet and all dependent services

```console
$ docker compose up waltid-demo-wallet
```

will start automatically:

- caddy
- postgres
- wallet-api
- and waltid-demo-wallet itself

#### Start services using compose profiles

`COMPOSE_PROFILES` environment variable located in the .env file allows the selection of
profiles to start the services for. The services are available with the following profiles:

- **identity** - the current stack implementing the final v1 protocols (issuer-api2, verifier-api2, wallet-api2,
  web-portal2)
- **identity-old** - the original stack implementing the draft protocols (wallet-api, issuer-api, verifier-api,
  waltid-demo-wallet, waltid-dev-wallet, web-portal, vc-repo)
- **services** - for API services of both stacks (wallet-api, issuer-api, verifier-api, vc-repo, issuer-api2,
  verifier-api2, wallet-api2)
- **apps** - for web applications of both stacks (waltid-demo-wallet, waltid-dev-wallet, web-portal, web-portal2)
- **valkey** - for the Valkey/Redis service (required when using valkey for session storage in wallet-api)
- **tse** - for the Hashicorp vault service, will be initialized with:
  - a transit secrets engine
  - and authentication methods
    - approle - for my-role, where role-id and secret-id will be output in the console <sup>1</sup>
    - userpass - for myuser with mypassword
    - access-token - with dev-only-token
- **opa** - for the Open Policy Agent service
- **all** - starts all services (equivalent to combining all profiles)

Profiles can be combined, e.g.:

- `COMPOSE_PROFILES=identity,tse` - will start the waltid-identity services and the vault
- `COMPOSE_PROFILES=identity,valkey` - will start the waltid-identity services with valkey for session storage
- `COMPOSE_PROFILES=all` - will start all services including vault, valkey, and opa

<sup>1</sup> - example output:

```console
vault-init            | Role ID: 66f3f095-74c9-b270-9d1f-1f842aa6bf3f
vault-init            | Secret ID: 3abf1e00-2dc1-9e77-0705-9a81a95c7c59
```

### Stop the Services

```bash
$ docker compose down
```

### Tear down the Services

```bash
$ docker compose down -v
```

## Port mapping

### Services

Old Services

- Wallet API: [http://localhost:7001](http://localhost:7001)
- Issuer API: [http://localhost:7002](http://localhost:7002)
- Verifier API: [http://localhost:7003](http://localhost:7003)

New Services

- Issuer API2: [http://localhost:7005](http://localhost:7005)
- Verifier API2: [http://localhost:7004](http://localhost:7004)
- Wallet API2: [http://localhost:7006](http://localhost:7006)

Dependencies

- Valkey (Redis-compatible): `localhost:6379` (requires `--profile valkey` or `--profile all`)
- Hashicorp vault: [http://localhost:8200](http://localhost:8200)
- Open Policy Agent: [http://localhost:8181](http://localhost:8181)

### Apps

Old Apps

- Demo Web Wallet: [http://localhost:7101](http://localhost:7101)
- Dev Web Wallet: [http://localhost:7104](http://localhost:7104)
- Web Portal: [http://localhost:7102](http://localhost:7102)
- Credential Repo: [http://localhost:7103](http://localhost:7103)

New Apps

- Web Portal2: [http://localhost:7105](http://localhost:7105)

## Configurations

Each API service reads the configuration files mounted from its own directory:

- wallet API: `wallet-api/config`
- issuer API: `issuer-api/config`
- verifier API: `verifier-api/config`
- issuer API2: `issuer-api2/config`
- verifier API2: `verifier-api2/config`
- wallet API2: `wallet-api2/config`
- ingress: `Caddyfile`

## How to

### Select the services to start

- browse `.env` file
- update `COMPOSE_PROFILES` so only the required services are started (see
  [Start services using compose profiles](#start-services-using-compose-profiles))

### Update port number

- browse `.env` file
- update the desired port number

This value will be used by reverse proxy (and services configs, if any).

### Update browser-facing host

- browse `.env` file
- update `PUBLIC_SERVICE_HOST` if browser-facing app URLs should not use `localhost`

This value is used for public frontend configuration such as web-portal2 issuer2, verifier2, and wallet URLs.

### Select an identity stack version

- browse `.env` file
- update `VERSION_TAG` to a specific image version (e.g. a release version)
  - if not set, `latest` tag is used

## Troubleshooting

### Updating ports doesn't work

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
- issuer-api2/config
  - issuer-service.conf
  - web.conf
- verifier-api2/config
  - verifier-service.conf
  - web.conf
- wallet-api2/config
  - wallet-service.conf
  - web.conf

### Removing the DB volume

```
docker volume rm docker-compose_wallet-api-db
```

### DB Backup / Restore

```
pg_dump -U your_user_name -h your_host -d your_db_name > backup.sql
psql -U your_user_name -h your_host -d your_db_name < backup.sql
```

