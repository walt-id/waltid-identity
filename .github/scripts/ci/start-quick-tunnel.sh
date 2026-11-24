#!/usr/bin/env bash
set -euo pipefail

cloudflared=$1
origin=$2
log_file=$3
pid_file=$4
max_attempts=3
startup_timeout=60
tunnel_url_pattern='https://[a-z0-9-]+\.trycloudflare\.com'

for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  "$cloudflared" tunnel --url "$origin" </dev/null >"$log_file" 2>&1 &
  tunnel_pid=$!
  printf '%s\n' "$tunnel_pid" >"$pid_file"

  for ((elapsed = 0; elapsed < startup_timeout; elapsed++)); do
    kill -0 "$tunnel_pid" 2>/dev/null || break
    if [[ $(<"$log_file") =~ $tunnel_url_pattern ]]; then
      printf '%s\n' "${BASH_REMATCH[0]}"
      exit 0
    fi
    sleep 1
  done

  kill -KILL "$tunnel_pid" 2>/dev/null || true
  wait "$tunnel_pid" 2>/dev/null || true
  rm -f "$pid_file"
  printf 'Cloudflare tunnel startup attempt %s/%s did not produce a URL.\n' "$attempt" "$max_attempts" >&2
  cat "$log_file" >&2
  if ((attempt < max_attempts)); then
    sleep 5
  fi
done

echo '::error::Cloudflare tunnel startup failed after three attempts.' >&2
exit 1
