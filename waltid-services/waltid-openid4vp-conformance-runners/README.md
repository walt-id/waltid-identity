<div align="center">
<h1>walt.id OpenID4VP Conformance Runners</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Utilities and instructions to run OpenID4VP 1.0 conformance tests against walt.id services</p>

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

## Quick Start (Docker)

The fastest way to run conformance tests locally:

### Prerequisites
- Docker and Docker Compose
- Java 21+
- `ngrok`
- `/etc/hosts` entry: `127.0.0.1 localhost.emobix.co.uk`

Add the hosts entry:
```shell
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 1. Clone and Start the Conformance Suite

The canonical compose file lives in this repository:
`waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml`

Docker Compose must still resolve relative paths such as `./nginx` and `./mongo/data` from the
OpenID conformance suite checkout, so use `--project-directory ~/dev/openid/conformance-suite`.

```shell
# Clone the conformance suite (if not already done)
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Start with Docker, using the compose file from the walt repo
docker compose \
  -f ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml \
  --project-directory ~/dev/openid/conformance-suite \
  up -d
```

Wait ~30 seconds for the server to start, then verify:
```shell
curl -k https://localhost.emobix.co.uk:8443/
```

### 2. Start ngrok for the verifier callback port

The local verifier listens on port `7003`. The conformance suite container must call back into that verifier.
On some machines, `host.docker.internal:7003` is not reachable from the container, so use `ngrok` to expose
the verifier as a public HTTPS endpoint.

Start the tunnel:
```shell
ngrok http 7003
```

Determine the current public tunnel URL:
```shell
curl -s http://127.0.0.1:4040/api/tunnels | jq -r '.tunnels[0].public_url'
```

Example output:
```text
https://c117-2001-871-26a-8324-bb52-944b-ae81-350e.ngrok-free.app
```

Determine just the current ngrok subdomain / hostname:
```shell
curl -s http://127.0.0.1:4040/api/tunnels | jq -r '.tunnels[0].public_url' | sed 's#https://##'
```

Example output:
```text
c117-2001-871-26a-8324-bb52-944b-ae81-350e.ngrok-free.app
```

### 3. Run the Conformance Tests

```shell
# From the waltid-unified-build directory
cd ~/dev/walt-id/waltid-unified-build

# Option A: export the callback base URL once in the shell
export OPENID4VP_CONFORMANCE_VERIFIER2_URL_PREFIX="$(curl -s http://127.0.0.1:4040/api/tunnels | jq -r '.tunnels[0].public_url')/verification-session"

# Run the verifier conformance tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "id.walt.openid4vp.conformance.ConformanceTests" \
  --no-daemon \
  --rerun-tasks
```

Or run the test in one command without exporting:
```shell
OPENID4VP_CONFORMANCE_VERIFIER2_URL_PREFIX="$(curl -s http://127.0.0.1:4040/api/tunnels | jq -r '.tunnels[0].public_url')/verification-session" \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "id.walt.openid4vp.conformance.ConformanceTests" \
  --no-daemon \
  --rerun-tasks
```

Or run the main application with the same callback override:
```shell
OPENID4VP_CONFORMANCE_VERIFIER2_URL_PREFIX="$(curl -s http://127.0.0.1:4040/api/tunnels | jq -r '.tunnels[0].public_url')/verification-session" \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:run
```

### 4. Stop the Conformance Suite

```shell
docker compose \
  -f ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml \
  --project-directory ~/dev/openid/conformance-suite \
  down
```

### Notes

- The callback override can be supplied in two ways:
  - environment variable: `OPENID4VP_CONFORMANCE_VERIFIER2_URL_PREFIX`
  - test JVM system property: `openid4vp.conformance.verifier2-url-prefix`
- In local testing, the environment variable has been the most reliable option.
- The value must include the `/verification-session` suffix, for example:
  - `https://<your-ngrok-subdomain>.ngrok-free.app/verification-session`
- There is also a `docker-compose-walt.yml` inside `~/dev/openid/conformance-suite`, but the recommended
  source of truth is the one in this repository. Use the commands above so the compose file stays under
  walt repo control while still using the conformance suite checkout for `nginx/` and `mongo/`.

## SSL Certificate (Already Configured)

This project includes a bundled truststore (`conformance-truststore.jks`) with the conformance suite's self-signed certificate. It's automatically used when running via Gradle.

### Updating the Certificate

If you rebuild the conformance suite's nginx container, extract and import the new certificate:

```shell
# Extract certificate from running server
openssl s_client -connect localhost.emobix.co.uk:8443 -servername localhost.emobix.co.uk </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > conformance-test.pem

# Update truststore
keytool -delete -alias conformance-test-localhost -keystore conformance-truststore.jks -storepass changeit 2>/dev/null || true
keytool -importcert -trustcacerts -alias conformance-test-localhost \
  -file conformance-test.pem -keystore conformance-truststore.jks \
  -storepass changeit -noprompt
```

### Running from IntelliJ

Add these VM options to your run configuration:
```
-Djavax.net.ssl.trustStore=/home/pp/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/conformance-truststore.jks
-Djavax.net.ssl.trustStorePassword=changeit
```

---

## Alternative: Setup with Devenv (Nix)

For the full nix/devenv setup (creates CA, manages hosts file automatically):

### Install Nix

```shell
# Option 1: Native package manager (if available)
sudo pacman -S nix  # Arch
# or
sudo apt install nix  # Debian/Ubuntu

# Option 2: Official installer
sh <(curl --proto '=https' --tlsv1.2 -L https://nixos.org/nix/install) --daemon
```

Enable and start the nix daemon:
```shell
sudo systemctl enable --now nix-daemon.service
```

### Install Devenv

```shell
nix-env --install --attr devenv -f https://github.com/NixOS/nixpkgs/tarball/nixpkgs-unstable
```

### Run Conformance Suite with Devenv

```shell
cd ~/dev/openid/conformance-suite
devenv up
```

In another terminal:
```shell
cd ~/dev/openid/conformance-suite
mvn spring-boot:run
```

Visit: https://localhost.emobix.co.uk:8443/

---

## Test Plans

OpenID4VP 1.0 test coverage:

**Verifier Tests**
- sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post
- sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post.jwt
- iso_mdl + x509_san_dns + request_uri_signed + direct_post
- iso_mdl + x509_san_dns + request_uri_signed + direct_post.jwt

**Wallet Tests** (unsigned/signed + direct_post/direct_post.jwt)
- sd_jwt_vc: `did`, `pre_registered`, `redirect_uri`, `web-origin`, `x509_san_dns`
- iso_mdl: `did`, `pre_registered`, `redirect_uri`, `web-origin`, `x509_san_dns`

See `config/` for example configuration files.

---

## Troubleshooting

### SSL Handshake Errors
If you see `SSLHandshakeException` or certificate errors:
1. Ensure `docker-compose-walt.yml` was used (builds nginx with proper cert)
2. Verify truststore is being used (check Gradle output for JVM args)
3. Try rebuilding: `docker compose -f docker-compose-walt.yml build nginx`

### Conformance Suite Not Starting
Check container logs:
```shell
docker logs conformance-suite-server-1
docker logs conformance-suite-nginx-1
```

### Conformance Suite Times Out Fetching `request_uri`
If the conformance suite fails while fetching a verifier `request_uri`, the Docker container probably cannot
reach your host callback port directly.

Use the ngrok workflow above and verify that the callback override is set:
```shell
echo "$OPENID4VP_CONFORMANCE_VERIFIER2_URL_PREFIX"
```

Verify the tunnel is up:
```shell
curl -s http://127.0.0.1:4040/api/tunnels | jq -r '.tunnels[0].public_url'
```

The verifier callback URLs created during the test should now use the ngrok HTTPS base instead of
`http://host.docker.internal:7003/...`.

---

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
