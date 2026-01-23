#!/usr/bin/env sh

npm install && npm run build && docker build -t waltid/vc-repository . && docker push waltid/vc-repository
