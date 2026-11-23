#!/usr/bin/env bash
set -euo pipefail

identity_root="$(git rev-parse --show-toplevel)"
runtime_root="${RUNNER_TEMP:?RUNNER_TEMP is required}/wallet-browser-e2e"
evidence_root="$runtime_root/evidence"
evidence_dir="$evidence_root/live-status"
service_root="$identity_root/waltid-services/waltid-wallet-api2"
wallet_jar="$service_root/build/libs/waltid-wallet-api2-all.jar"
app_root="$identity_root/waltid-applications/waltid-wallet-demo-compose/webApp/build/dist/wasmJs/productionExecutable"
visual_root="$identity_root/waltid-applications/waltid-wallet-demo-compose/webApp/visual"
issuer_script="$identity_root/waltid-applications/mobile-e2e-fixtures/status/issuer.py"
python="$RUNNER_TEMP/wallet-browser-python/bin/python"

wallet_pid=""
issuer_pid=""
app_pid=""

stop_process() {
  local pid="${1:-}"
  [[ -z "$pid" ]] && return 0
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

cleanup() {
  local result=$?
  stop_process "$app_pid"
  stop_process "$issuer_pid"
  stop_process "$wallet_pid"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ -n "$(git -C "$identity_root" status --porcelain)" ]]; then
  echo "The real browser E2E requires a clean Identity checkout" >&2
  exit 1
fi
[[ -f "$wallet_jar" ]] || { echo "Missing built Wallet API 2 JAR: $wallet_jar" >&2; exit 1; }
[[ -f "$app_root/index.html" ]] || { echo "Missing built production browser host: $app_root" >&2; exit 1; }
[[ ! -e "$evidence_dir" ]] || { echo "Evidence directory already exists: $evidence_dir" >&2; exit 1; }
mkdir -p "$runtime_root/config" "$runtime_root/data" "$evidence_root"
cp -R "$service_root/config/." "$runtime_root/config/"

# The wallet's outbound guard remains enabled. Only this isolated test issuer's localhost host is trusted.
cat > "$runtime_root/config/network-fetch-guard.conf" <<'CONFIG'
allowLoopback = false
allowPrivateNetworks = false
trustedHostSuffixes = ["localhost"]
CONFIG
sed -i 's/^webPort[[:space:]]*=.*/webPort = 7018/' "$runtime_root/config/web.conf"
sed -i 's/^webHost[[:space:]]*=.*/webHost = "127.0.0.1"/' "$runtime_root/config/web.conf"
sed -i 's#^publicBaseUrl[[:space:]]*=.*#publicBaseUrl = "http://127.0.0.1:7018"#' \
  "$runtime_root/config/wallet-service.conf"
sed -i "s#^jdbcUrl[[:space:]]*=.*#jdbcUrl = \"jdbc:sqlite:$runtime_root/data/wallet2.db\"#" \
  "$runtime_root/config/wallet2-persistence.conf"

source_revision="$(git -C "$identity_root" rev-parse HEAD)"
jar_sha256="$(sha256sum "$wallet_jar" | awk '{print $1}')"
startup_receipt="$evidence_root/startup.json"
python3 - "$startup_receipt" "$source_revision" "$jar_sha256" <<'PY'
import json
import sys

path, source_revision, jar_sha256 = sys.argv[1:]
with open(path, "w", encoding="utf-8") as receipt:
    json.dump({
        "sourceRevision": source_revision,
        "walletApi2JarSha256": jar_sha256,
        "phases": {},
    }, receipt, indent=2)
    receipt.write("\n")
PY

record_phase() {
  local phase="$1"
  local state="$2"
  python3 - "$startup_receipt" "$phase" "$state" <<'PY'
import json
import sys

path, phase, state = sys.argv[1:]
with open(path, encoding="utf-8") as receipt:
    report = json.load(receipt)
report["phases"][phase] = state
with open(path, "w", encoding="utf-8") as receipt:
    json.dump(report, receipt, indent=2)
    receipt.write("\n")
PY
}

python3 - "$runtime_root/backend-producer.json" "$source_revision" "$jar_sha256" <<'PY'
import json
import sys

path, source_revision, jar_sha256 = sys.argv[1:]
with open(path, "w", encoding="utf-8") as receipt:
    json.dump({
        "serviceSourceRevision": source_revision,
        "serviceArtifactSha256": jar_sha256,
    }, receipt, indent=2)
    receipt.write("\n")
PY

# Trust a generated test-only CA for both the browser's Linux trust store and the JVM's
# default public roots. The issuer's control listener remains plain HTTP on loopback.
openssl req -x509 -newkey rsa:2048 -nodes -days 2 \
  -keyout "$runtime_root/issuer-ca.key" -out "$runtime_root/issuer-ca.crt" \
  -subj "/CN=Wallet browser E2E test CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"
openssl req -newkey rsa:2048 -nodes \
  -keyout "$runtime_root/localhost.key" -out "$runtime_root/localhost.csr" \
  -subj "/CN=localhost"
cat > "$runtime_root/localhost.ext" <<'EXTENSIONS'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost
EXTENSIONS
openssl x509 -req -in "$runtime_root/localhost.csr" \
  -CA "$runtime_root/issuer-ca.crt" -CAkey "$runtime_root/issuer-ca.key" \
  -CAcreateserial -out "$runtime_root/localhost.crt" -days 2 -sha256 \
  -extfile "$runtime_root/localhost.ext"
install -m 0644 "$runtime_root/issuer-ca.crt" /usr/local/share/ca-certificates/wallet-browser-e2e.crt
update-ca-certificates
mkdir -p "$HOME/.pki/nssdb"
if [[ ! -f "$HOME/.pki/nssdb/cert9.db" ]]; then
  certutil -N --empty-password -d "sql:$HOME/.pki/nssdb"
fi
certutil -A -n "Wallet browser E2E test CA" -t "C,," \
  -d "sql:$HOME/.pki/nssdb" -i "$runtime_root/issuer-ca.crt"
cp "$JAVA_HOME/lib/security/cacerts" "$runtime_root/jvm-truststore"
keytool -importcert -noprompt -trustcacerts -alias wallet-browser-e2e-test-ca \
  -file "$runtime_root/issuer-ca.crt" -keystore "$runtime_root/jvm-truststore" \
  -storepass changeit

cd "$runtime_root"
java -Djavax.net.ssl.trustStore="$runtime_root/jvm-truststore" \
  -Djavax.net.ssl.trustStorePassword=changeit -jar "$wallet_jar" > wallet-api2.log 2>&1 &
wallet_pid=$!
record_phase "Wallet API 2" "starting"
"$python" "$issuer_script" \
  --issuer-url https://localhost:8089 --port 8089 --control-port 8090 --ttl 20 \
  --tls-cert "$runtime_root/localhost.crt" --tls-key "$runtime_root/localhost.key" \
  > status-issuer.log 2>&1 &
issuer_pid=$!
record_phase "Controlled issuer" "starting"
python3 -m http.server 8074 --bind 127.0.0.1 --directory "$app_root" \
  > browser-host.log 2>&1 &
app_pid=$!
record_phase "Browser host" "starting"

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempt=0
  for ((attempt = 1; attempt <= 90; attempt += 1)); do
    if curl --fail --silent --show-error --max-time 2 "$url" >/dev/null 2>&1; then
      record_phase "$label" "ready"
      echo "$label is ready"
      return 0
    fi
    if ! kill -0 "$wallet_pid" 2>/dev/null && [[ "$label" == "Wallet API 2" ]]; then
      record_phase "$label" "process-exited-before-ready"
      echo "Wallet API 2 exited before becoming ready" >&2
      return 1
    fi
    if ! kill -0 "$issuer_pid" 2>/dev/null && [[ "$label" == "Controlled issuer" || "$label" == "TLS issuer" ]]; then
      record_phase "$label" "process-exited-before-ready"
      echo "$label process exited before becoming ready" >&2
      return 1
    fi
    if ! kill -0 "$app_pid" 2>/dev/null && [[ "$label" == "Browser host" ]]; then
      record_phase "$label" "process-exited-before-ready"
      echo "Browser host exited before becoming ready" >&2
      return 1
    fi
    sleep 2
  done
  record_phase "$label" "readiness-timeout"
  echo "$label did not become ready at $url" >&2
  return 1
}

wait_for_url http://127.0.0.1:7018/wallet "Wallet API 2"
wait_for_url http://127.0.0.1:8090/state "Controlled issuer"
wait_for_url https://localhost:8089/.well-known/openid-credential-issuer "TLS issuer"
wait_for_url http://127.0.0.1:8074/ "Browser host"

cd "$visual_root"
WALLET_API2_E2E_BASE_URL=http://127.0.0.1:7018 \
WALLET_STATUS_CONTROL_URL=http://127.0.0.1:8090 \
WALLET_BROWSER_APP_URL=http://127.0.0.1:8074 \
WALLET_API2_E2E_PRODUCER="$runtime_root/backend-producer.json" \
WALLET_BROWSER_EVIDENCE_DIR="$evidence_dir" \
node live-status.cjs
