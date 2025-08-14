# Using RADIUS with ktor-authnz

## Install a radius server

In this example, we will install the popular open-source "FreeRADIUS" server.
Install "freeradius" with your package manager (e.g. `sudo pacman -S freeradius`).

You will find the server configuration depending on your distribution at:
- /etc/freeradius/<version>/
- /etc/raddb/

For me, it is at `/etc/raddb/`.

### Configure keys
Create your keys using: `/etc/raddb/certs/bootstrap`

You can also investigate the files to customize properties of the generated certificates:
- `ca.cnf`
- `client.cnf`
- `server.cnf`

Or simply create your own certificates & keys if you want to sign them with a different CA (by default, the bootstrap script generates a new CA to sign the keys).

### Configure client

The "client" is the application sending the authentication request to FreeRADIUS.
Configure the clients in `/etc/raddb/clients.conf`:

By default, my configuration file contains this configuration entry:
```hocon
client localhost {
    ipaddr              = 127.0.0.1
    secret              = testing123
}
```

You can also setup a custom client: 
```hocon
client my_local_app {
    ipaddr              = 127.0.0.1
    secret              = your_super_secret_key # This must match ktor-authnz config
    nas_identifier      = my_nas_id             # This must match ktor-authnz config
}
```

### Define a test user

Uncomment an example from the `/etc/raddb/mods-config/files/authorize` file:

```ini
bob    Cleartext-Password := "hello"
```

### Run the server
You can enable full debugging with `-X`.

Run the FreeRADIUS server (depends on the name it has in your distribution):
- `sudo radiusd -X`
- `sudo freeradius -X`

You will see a lot of output, ending with `Ready to process requests`.
Your server is now running.

You can also test the server with `radtest` like this:
- `radtest <username> <password> <radius_host> <port> <secret>`

So in our example:
- `radtest bob hello localhost 1812 testing123`

You should find the message:
- `Sent Access-Request Id ... Received Access-Accept Id ...`
if the password is correct.

If the password is incorrect, you should see:
- `Sent Access-Request Id ... Received Access-Reject Id ...`

## Configure Enterprise

### Setup auth method

In your `auth.conf`, set the following configuration:

```hocon
# Configure the Auth Flow (refer to: waltid-ktor-authnz)
authFlow = {
    method: radius
    config: {
        radiusServerHost: "localhost"
        radiusServerPort: 1812
        radiusServerSecret: "testing123"
        #radiusNasIdentifier: "xx" # set if you setup a `nas_identifier = ...` in your clients.conf
    }
    expiration: "7d" # optional: Set expiration time for login tokens, e.g. a week
    ok: true # Auth flow ends successfuly with this step
}
```

### Create account for auth method

`POST /v1/admin/account/register`
```json
{
  "profile": {
    "name": "Example RADIUS Account",
    "email": "myradius@example.org",
    "addressCountry": "AT",
    "address": "Hauptstraße 1, 1234 XYZ"
  },
  "preferences": {
    "timeZone": "UTC",
    "languagePreference": "EN"
  },
  "initialAuth": {
    "type": "radius",
    "identifier": {
      "type": "radius",
      "host": "localhost:1812",
      "name": "bob"
    }
  }
}
```

### Test login

Endpoint request:
`POST /auth/account/radius`

Documentation:
```
Login with account. Applied Auth flow configuration on this route is:

Method: radius
-> Flow end (success)
```

Request body:
```json
{
  "username": "bob",
  "password": "hello"
}
```

As curl:
```curl
curl -X 'POST' \
  'http://waltid.enterprise.localhost:3000/auth/account/radius' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "username": "bob",
  "password": "hello"
}'
```

Response body:
```json
{
  "session_id": "34b0dd50-7095-4877-8095-4e34044fcf3b",
  "status": "OK",
  "token": "eyJhbGciOiJFZERTQSJ9...",
  "expiration": "2025-08-20T13:43:16.463720303Z"
}
```
