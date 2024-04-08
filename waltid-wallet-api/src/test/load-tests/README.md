# Load-test for the walt.id wallet API

## Single call

Use CURL for testing a single API call

```
curl -w "@curl-format.txt"  -X 'POST' \
  'http://0.0.0.0:7001/wallet-api/auth/login' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "type": "email",
  "email": "user@email.com",
  "password": "password"
}'
```

## Load tests

Use [k6](https://k6.io/) for running the load tests in this folder. For example:

```
k6 run login-scenarios.js
```