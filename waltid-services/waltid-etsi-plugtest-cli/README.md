# ETSI Plugtest CLI

A CLI tool for generating and validating SD-JWT-VC and mdoc EAAs for ETSI plugtest compliance testing.

## Features

- **Generate** SD-JWT-VC and mdoc EAAs based on ETSI test case definitions
- **Validate** vendor-submitted EAAs from the ETSI portal zip download
- **Generate XML reports** following ETSI verification report format

## Configuration

The CLI uses HOCON configuration files, following the same pattern as other walt.id services.

### Config File Locations

Configuration files are searched in the following order:

1. `--config` CLI option (if provided)
2. `config/etsi.conf` (relative to working directory)
3. `etsi.conf` (relative to working directory)  
4. `~/.config/waltid/etsi.conf`

### Path Resolution

Paths in the configuration file can be:
- **Absolute paths**: Used as-is (e.g., `/home/user/keys/issuer.jwk`)
- **Relative paths**: Resolved relative to the config file directory first, then the working directory

When running via Gradle, the working directory is the module directory (`waltid-services/waltid-etsi-plugtest-cli`).

### Example Configuration

See `config/etsi.conf` for a full example with all options documented.

```hocon
# config/etsi.conf

issuer {
    # Paths relative to config file directory
    keyFile = "key.jwk"
    certificateFile = "cert.pem"
    
    # Or use absolute paths
    # keyFile = "/path/to/issuer-key.jwk"
    # certificateFile = "/path/to/issuer-cert.pem"
    
    # Issuer URL for SD-JWT-VC
    url = "https://issuer.example.com"
}

holder {
    # Path to holder JWK key file (for key binding)
    keyFile = "holder-key.jwk"
}

output {
    directory = "output"
    fileNamePattern = "{testCaseId}"
}

sdjwt {
    vct = "urn:etsi:eaa:credential"
}
```

### Inline Keys and Certificates

You can also provide keys and certificates inline in the config:

```hocon
issuer {
    keyJwk = """
    {
      "type": "jwk",
      "jwk": {
        "kty": "EC",
        "d": "your-private-key-d",
        "crv": "P-256",
        "kid": "issuer-key-id",
        "x": "your-public-key-x",
        "y": "your-public-key-y"
      }
    }
    """
    
    certificatePem = """
    -----BEGIN CERTIFICATE-----
    MIICCTCCAbCgAwIBAgIU...
    -----END CERTIFICATE-----
    """
    
    url = "https://issuer.example.com"
}
```

## Gradle Commands

### Build the CLI
```bash
cd waltid-identity
./gradlew :waltid-services:waltid-etsi-plugtest-cli:build
```

### List test cases
```bash
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="list -t /path/to/test-cases.json"
```

### Generate SD-JWT-VC EAAs
```bash
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="generate -t /path/to/test-cases.json -f sd-jwt-vc -o ./output"
```

### Generate with explicit config file
```bash
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="generate --config ./my-config.conf -t /path/to/test-cases.json -f sd-jwt-vc"
```

### Generate specific test case
```bash
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="generate -t /path/to/test-cases.json --test-case SJV-EAA-1 -o ./output"
```

### Validate vendor EAAs from zip
```bash
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="validate -z /path/to/vendors.zip -o ./verification-reports"
```

### Validate with content validation against test cases
```bash
# Enable content validation by providing test-cases.json
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="validate -z /path/to/vendors.zip -t /path/to/test-cases.json -o ./verification-reports"
```

### Validate with filters
```bash
# Filter by vendor
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="validate -z /path/to/vendors.zip -v VENDOR-A"

# Filter by format
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="validate -z /path/to/vendors.zip -f sd-jwt-vc"

# Summary only (no report files)
./gradlew :waltid-services:waltid-etsi-plugtest-cli:run \
  --args="validate -z /path/to/vendors.zip --summary"
```

## CLI Options

### Global Options
| Option | Description |
|--------|-------------|
| `--help` | Show help message |

### Generate Command Options
| Option | Description |
|--------|-------------|
| `--config` | Path to HOCON configuration file |
| `-t, --test-cases` | Path to test-cases.json (required) |
| `-o, --output` | Output directory (overrides config) |
| `-f, --format` | Filter by format: `sd-jwt-vc` or `mdoc` |
| `-p, --profile` | Filter by profile name |
| `--test-case` | Generate specific test case by ID |
| `-k, --issuer-key` | Path to issuer JWK key file (overrides config) |
| `-c, --issuer-cert` | Path to issuer certificate PEM file (overrides config) |
| `--holder-key` | Path to holder JWK key file (overrides config) |
| `--issuer-url` | Issuer URL for SD-JWT-VC (overrides config) |
| `--vct` | Verifiable Credential Type (overrides config) |

### List Command Options
| Option | Description |
|--------|-------------|
| `-t, --test-cases` | Path to test-cases.json (required) |
| `-f, --format` | Filter by format: `sd-jwt-vc` or `mdoc` |
| `-p, --profile` | Filter by profile name |

### Validate Command Options
| Option | Description |
|--------|-------------|
| `-z, --zip` | Path to vendor zip file from ETSI portal (required) |
| `-t, --test-cases` | Path to test-cases.json for content validation (optional) |
| `-o, --output` | Output directory for XML reports (default: `verification-reports`) |
| `-v, --vendor` | Filter by vendor name |
| `--test-case` | Filter by test case ID |
| `-f, --format` | Filter by format: `sd-jwt-vc` or `mdoc` |
| `-s, --summary` | Print summary only, don't generate report files |

## Validation Types

### Signature Validation (always performed)
- Verifies the cryptographic signature of the credential
- Extracts the signer key from x5c certificate chain
- Returns VALID, INVALID, or INDETERMINATE

### Content Validation (when `-t` option is provided)
- Checks if the credential contains all required fields from the test case definition
- For SD-JWT-VC: validates protected header and payload fields
- For mdoc: validates protected header, MSO payload, and namespace data elements
- Reports missing and present fields in the XML report

## Validation Workflow

1. **Download** the vendor zip file from the ETSI Plugtest Portal
2. **Run validation** using the `validate` command
3. **Review** the generated XML reports
4. **Upload** the XML reports back to the ETSI portal

### Zip File Structure

The validator expects the zip file to have the following structure:
```
vendors.zip
├── VENDOR-A/
│   ├── SJV-EAA-1.json
│   ├── SJV-QEAA-2.json
│   └── MDL-EAA-1.cbor
├── VENDOR-B/
│   ├── SJV-EAA-1.json
│   └── MDOC-QEAA-1.cbor
└── ...
```

### XML Report Format

Generated reports follow the ETSI verification report format:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<VALID>
  <VerifiedAt>2026-05-13T12:00:00Z</VerifiedAt>
  <Hash>64:11:CA:29:15:03:3C:A4:75:3F:4F:1A:53:34:80:2E:23:7E:2A:4E</Hash>
  <VerifiedFile>SJV-EAA-1.json</VerifiedFile>
  <Vendor>VENDOR-A</Vendor>
  <TestCaseId>SJV-EAA-1</TestCaseId>
  <Details>Detection: SDJWTVC/sdjwtvc</Details>
</VALID>
```

Report root elements:
- `<VALID />` - Credential verified successfully
- `<INVALID />` - Credential verification failed
- `<INDETERMINATE />` - Could not determine validity

### Report Naming Convention

Reports are named following ETSI conventions:
```
Verification_of_<VENDOR>_<TEST-CASE-ID>.xml
```

Example: `Verification_of_VENDOR-A_SJV-EAA-1.xml`

## Configuration Priority

CLI options take precedence over configuration file values:

1. CLI option (highest priority)
2. Configuration file value
3. Default value (lowest priority)

## Output Files

### Generation
- **SD-JWT-VC**: Generated as `.json` files containing the compact SD-JWT-VC string
- **mdoc**: Generated as `.cbor` files containing the binary CBOR-encoded document

### Validation
- **XML Reports**: Generated as `.xml` files following ETSI verification report format

## File Naming

The output file name pattern can be configured via `output.fileNamePattern`. 
Supported placeholders:
- `{testCaseId}` - The test case ID (e.g., "SJV-EAA-1")

Example: `fileNamePattern = "WALT-{testCaseId}"` produces files like `WALT-SJV-EAA-1.json`
