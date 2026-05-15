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
- `/etc/hosts` entry: `127.0.0.1 localhost.emobix.co.uk`

Add the hosts entry:
```shell
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 1. Clone and Start the Conformance Suite

```shell
# Clone the conformance suite (if not already done)
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Start with Docker
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-local.yml up -d
```

Wait ~30 seconds for the server to start, then verify:
```shell
curl -k https://localhost.emobix.co.uk:8443/
```

### 2. Run the Conformance Tests

```shell
# From the waltid-unified-build directory
cd ~/dev/walt-id/waltid-unified-build

# Run tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "id.walt.openid4vp.conformance.ConformanceTests"
```

Or run the main application:
```shell
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:run
```

### 3. Stop the Conformance Suite

```shell
cp ./docker-compose-walt.yml cd ~/dev/openid/conformance-suite
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-local.yml down
```

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
