#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Create an Apple Distribution .p12 for GitHub Actions.

  prepare-ios-distribution-cert.sh csr [out-dir]
      Writes ios-distribution.key and ios-distribution.csr.
      Upload the CSR in Apple Developer. Keep the .key; Apple never gives it back.

  prepare-ios-distribution-cert.sh p12 <certificate.cer> <ios-distribution.key> [out-dir]
      Combines Apple's downloaded .cer with the private key into ios-distribution.p12.
      Prompts for the .p12 password unless IOS_DISTRIBUTION_CERTIFICATE_PASSWORD is set.

  prepare-ios-distribution-cert.sh encode <ios-distribution.p12>
      Prints the base64 blob for gh secret set IOS_DISTRIBUTION_CERTIFICATE_P12.
USAGE
  exit 2
}

command="${1:-}"
[[ -n "$command" ]] || usage

csr() {
  local out_dir="${1:-ios-distribution-ci}"
  mkdir -p "$out_dir"
  umask 077
  openssl genrsa -out "$out_dir/ios-distribution.key" 2048
  openssl req -new \
    -key "$out_dir/ios-distribution.key" \
    -out "$out_dir/ios-distribution.csr" \
    -subj "/CN=walt.id CI Apple Distribution/C=AT"
  echo "Wrote $out_dir/ios-distribution.csr"
  echo "Upload that CSR in Apple Developer → Certificates → Apple Distribution."
  echo "Keep $out_dir/ios-distribution.key. The .cer Apple returns is useless without it."
}

p12() {
  local cer="${1:-}"
  local key="${2:-}"
  local out_dir="${3:-$(dirname "${key:-.}")}"
  [[ -f "$cer" && -f "$key" ]] || usage
  mkdir -p "$out_dir"
  umask 077
  local pem="$out_dir/ios-distribution.pem"
  if openssl x509 -inform DER -in "$cer" -noout 2>/dev/null; then
    openssl x509 -inform DER -in "$cer" -out "$pem"
  else
    openssl x509 -inform PEM -in "$cer" -out "$pem"
  fi
  openssl pkcs12 -export \
    -inkey "$key" \
    -in "$pem" \
    -out "$out_dir/ios-distribution.p12" \
    -name "walt.id CI Apple Distribution" \
    ${IOS_DISTRIBUTION_CERTIFICATE_PASSWORD:+-passout pass:$IOS_DISTRIBUTION_CERTIFICATE_PASSWORD}
  echo "Wrote $out_dir/ios-distribution.p12"
}

encode() {
  local p12="${1:-}"
  [[ -f "$p12" ]] || usage
  if base64 -i "$p12" >/dev/null 2>&1; then
    base64 -i "$p12"
  else
    base64 -w0 "$p12"
    echo
  fi
}

case "$command" in
  csr) csr "${2:-ios-distribution-ci}" ;;
  p12) p12 "${2:-}" "${3:-}" "${4:-}" ;;
  encode) encode "${2:-}" ;;
  *) usage ;;
esac
