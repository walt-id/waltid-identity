#!/usr/bin/env sh

npm ci && npm run build && docker build -t waltid/vc-repository . && docker push waltid/vc-repository
