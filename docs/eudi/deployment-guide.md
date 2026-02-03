# EUDI Wallet Deployment Guide

This guide covers deploying and operating the walt.id issuer with EUDI wallet compatibility.

## Architecture Overview

```
┌─────────────────────┐     ┌─────────────────────┐
│   EUDI Wallet       │────▶│   Issuer API        │
│   (Android/iOS)     │     │   (Port 7002)       │
└─────────────────────┘     └─────────────────────┘
                                      │
                                      ▼
┌─────────────────────┐     ┌─────────────────────┐
│   Web Portal        │────▶│   Wallet API        │
│   (Port 7102)       │     │   (Port 7001)       │
└─────────────────────┘     └─────────────────────┘
```

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Wallet API | 7001 | Backend wallet service |
| Issuer API | 7002 | Credential issuance |
| Verifier API | 7003 | Legacy verification |
| Verifier API2 | 7004 | OID4VP 1.0 verification |
| Demo Wallet | 7101 | Web wallet UI |
| Web Portal | 7102 | Issuer/verifier portal |

## Building Custom Images

EUDI wallet support requires locally built images with Draft 13+ protocol fixes.

### Build All Services

```bash
# From repository root
./gradlew jibDockerBuild

# Tag all images
docker tag waltid/issuer-api:latest waltid/issuer-api:stable
docker tag waltid/verifier-api:latest waltid/verifier-api:stable
docker tag waltid/wallet-api:latest waltid/wallet-api:stable
```

### Build Specific Service

```bash
# Build only issuer
./gradlew :waltid-services:waltid-issuer-api:jibDockerBuild
docker tag waltid/issuer-api:latest waltid/issuer-api:stable
```

## Docker Compose Deployment

### Starting the Stack

```bash
cd docker-compose

# Pull webapp images and start
docker compose --profile identity pull
docker compose --profile identity up -d

# Force recreate with new images
docker compose --profile identity up -d --force-recreate
```

### Available Profiles

| Profile | Services |
|---------|----------|
| `services` | API services only |
| `apps` | Web applications only |
| `identity` | Full identity stack |
| `valkey` | Redis-compatible cache |
| `tse` | Hardware security module |
| `opa` | Policy engine |
| `all` | Everything |

### Using Multiple Profiles

```bash
docker compose --profile identity --profile tse up -d
```

## Configuration Reference

### Issuer API Configuration

Location: `docker-compose/issuer-api/config/`

| File | Purpose |
|------|---------|
| `issuer-service.conf` | Issuer DID and key configuration |
| `credential-issuer-metadata.conf` | Credential types and metadata |
| `db.conf` | Database settings |

### Credential Issuer Metadata

```hocon
# docker-compose/issuer-api/config/credential-issuer-metadata.conf

credentialConfigurationsSupported {

  # EUDI PID - mDoc format
  "eu.europa.ec.eudi.pid.1" {
    format = "mso_mdoc"
    doctype = "eu.europa.ec.eudi.pid.1"
    cryptographic_binding_methods_supported = ["cose_key"]
    proof_types_supported = {
      jwt = {
        proof_signing_alg_values_supported = ["ES256"]
      }
    }
    display = [{
      name = "EU Personal ID"
      locale = "en"
    }]
  }

  # EUDI PID - SD-JWT format
  "eu.europa.ec.eudi.pid_vc_sd_jwt" {
    format = "dc+sd-jwt"
    vct = "urn:eudi:pid:1"
    cryptographic_binding_methods_supported = ["jwk"]
    proof_types_supported = {
      jwt = {
        proof_signing_alg_values_supported = ["ES256"]
      }
    }
  }

  # Mobile Driving License
  "org.iso.18013.5.1.mDL" {
    format = "mso_mdoc"
    doctype = "org.iso.18013.5.1.mDL"
    cryptographic_binding_methods_supported = ["cose_key"]
    proof_types_supported = {
      jwt = {
        proof_signing_alg_values_supported = ["ES256"]
      }
    }
  }
}
```

### Issuer Service Configuration

```hocon
# docker-compose/issuer-api/config/issuer-service.conf

issuer {
  # Base URL for the issuer (must be externally accessible)
  baseUrl = "https://issuer.example.com"

  # DID for signing credentials
  did = "did:key:z6Mk..."

  # Key configuration
  key {
    type = "jwk"
    jwk = {
      kty = "EC"
      crv = "P-256"
      # ... key material
    }
  }
}
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VERSION_TAG` | `stable` | Docker image tag |
| `EXTERNAL_URL` | - | Public issuer URL |
| `LOG_LEVEL` | `INFO` | Logging verbosity |

### Setting Environment Variables

```bash
# In docker-compose/.env
VERSION_TAG=stable
EXTERNAL_URL=https://issuer.example.com

# Or inline
VERSION_TAG=latest docker compose --profile identity up
```

## HTTPS Configuration

EUDI wallets require HTTPS for production. Options:

### 1. Reverse Proxy (Recommended)

Use nginx, Traefik, or Caddy as a reverse proxy with TLS termination.

Example nginx configuration:
```nginx
server {
    listen 443 ssl;
    server_name issuer.example.com;

    ssl_certificate /etc/ssl/certs/issuer.crt;
    ssl_certificate_key /etc/ssl/private/issuer.key;

    location / {
        proxy_pass http://localhost:7002;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 2. Direct TLS

Configure Ktor to serve HTTPS directly (for development):

```hocon
# Add to application.conf
ktor {
  deployment {
    sslPort = 8443
    ssl {
      keyStore = /path/to/keystore.jks
      keyAlias = issuer
      keyStorePassword = password
      privateKeyPassword = password
    }
  }
}
```

## Health Checks

### Service Health

```bash
# Check issuer health
curl http://localhost:7002/health

# Check all services
for port in 7001 7002 7003 7004; do
  echo "Port $port: $(curl -s http://localhost:$port/health | head -1)"
done
```

### Metadata Endpoint

```bash
# Verify issuer metadata is served correctly
curl http://localhost:7002/.well-known/openid-credential-issuer | jq .
```

## Logs

### View Service Logs

```bash
# All services
docker compose --profile identity logs -f

# Specific service
docker compose logs -f issuer-api

# Last 100 lines
docker compose logs --tail 100 issuer-api
```

### Log Levels

Set via environment variable:
```bash
LOG_LEVEL=DEBUG docker compose --profile identity up
```

## Troubleshooting

### Service Won't Start

1. Check logs: `docker compose logs issuer-api`
2. Verify configuration files are valid HOCON
3. Check port conflicts: `lsof -i :7002`

### Credential Issuance Fails

1. Verify issuer DID is resolvable
2. Check key configuration matches DID
3. Ensure credential configuration ID exists in metadata

### EUDI Wallet Can't Connect

1. Verify HTTPS is working
2. Check CORS headers allow wallet origin
3. Verify `.well-known/openid-credential-issuer` returns valid JSON

### Reset Everything

```bash
cd docker-compose
docker compose --profile identity down -v
docker compose --profile identity up -d
```

## Monitoring

### Prometheus Metrics

Metrics are exposed at `/metrics` on each service port.

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'waltid-issuer'
    static_configs:
      - targets: ['localhost:7002']
```

### Resource Monitoring

```bash
# Container stats
docker stats

# Specific service
docker stats $(docker compose ps -q issuer-api)
```

## Backup and Recovery

### Configuration Backup

```bash
# Backup configs
tar -czf issuer-config-backup.tar.gz docker-compose/issuer-api/config/

# Restore
tar -xzf issuer-config-backup.tar.gz
```

### Database Backup

If using persistent database:
```bash
docker compose exec db pg_dump -U postgres issuer > backup.sql
```

## Updates

### Updating Services

```bash
# Pull new images
./gradlew :waltid-services:waltid-issuer-api:jibDockerBuild
docker tag waltid/issuer-api:latest waltid/issuer-api:stable

# Restart with new image
cd docker-compose
docker compose --profile identity up -d --force-recreate issuer-api
```

### Rolling Updates

For zero-downtime updates:
1. Build new image with different tag
2. Update compose file to use new tag
3. Restart service: `docker compose up -d issuer-api`

## Related Documentation

- [Integration Guide](./integration-guide.md) - Developer integration
- [Credential Formats Reference](./credential-formats.md) - Format details
