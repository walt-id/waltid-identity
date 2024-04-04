#!/usr/bin/env sh
# Custom entrypoint that prepares permissions for non-root users

# Give non-root user permission to mounted data directory, config directory, ...
chown -R waltid:waltid /waltid-verifier-api

# Execute as non-root user
exec runuser -u waltid /waltid-verifier-api/bin/waltid-verifier-api -- "$@"
