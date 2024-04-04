#!/usr/bin/env sh
# Custom entrypoint that prepares permissions for non-root users

# Give non-root user permission to mounted data directory, config directory, ...
chown -R waltid:waltid /waltid-wallet-api

# Execute as non-root user
exec runuser -u waltid /waltid-wallet-api/bin/waltid-wallet-api -- "$@"
