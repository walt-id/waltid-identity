#!/bin/bash

set -eu pipefail

echo "Attempting to connect to wallet-api:${WALLET_BACKEND_PORT}"
until $(nc -zv localhost "${WALLET_BACKEND_PORT}"); do
    sleep 5
done
echo "Connected!"

newman run /postman/collection.json --environment /postman/dev.json --bail

exit 0