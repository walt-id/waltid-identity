# Using OIDC with ktor-authnz

_Note: Tested with Keycloak 26.4.2_

## Setting up a Keycloak server

### Download & run Keycloak

_Note: You can oftentimes also download & install Keycloak from your operating systems package
manager, or alternatively from docker. This guide will show the general way for development use._

_Note: This guide will not go in-depth into how to make your Keycloak production-grade etc., and
instead focuses purely on the configuration with the walt.id integration._

- Download Keycloak from https://www.keycloak.org/downloads - the releases can also be found with
  direct download links from GitHub: https://github.com/keycloak/keycloak/releases/
- For example:
  `wget https://github.com/keycloak/keycloak/releases/download/26.4.2/keycloak-26.4.2.zip`
- Unzip the downloaded archive: `unzip keycloak-26.4.2.zip`
- Enter the directory: `cd keycloak-26.4.2`
- Run Keycloak: `./bin/kc.sh start-dev` (this will start it in development mode - do not use this
  mode in production)

After running the Keycloak application, you should see a log message like this:

```
...
INFO  [io.quarkus] (main) Keycloak 26.4.2 on JVM (powered by Quarkus 3.27.0) started in 7.823s. Listening on: http://0.0.0.0:8080
```

Test if it is working as intended: `curl -I localhost:8080` should show `HTTP/1.1 200 OK`

#### Initial setup of Keycloak

##### Creating administration user

Accessing the stated URL in your browser (http://localhost:8080/) should show you the page:

```markdown
*Create an administrative user*

To get started with Keycloak, you first create an administrative user.

Username*: _____
Password*: _____
Password confirmation*: _____
```

Enter some administrator details in the form. I will use the user "admin" for this example.

After creation, you should find the success page, telling you the user was successfully created.

Press the button `[Open administration console]` and login with your newly created user.

You should now find yourself at http://localhost:8080/admin/master/console/ - in the "master" realm.

### Configuring a client for waltid-ktor-authnz at Keycloak

- In your selected Keycloak realm, visit `Manage > Clients` and click
  `[Create client]` (http://localhost:8080/admin/master/console/#/master/clients/add-client)
- Under 1) General settings
    - Set `Client type` to `OpenID Connect`
    - Give a `Client ID`, e.g. `waltid_ktor_authnz`
    - You can optionally provide a friendly name at `Name`, and a `Description`
- Under 2) Capability config
    - Toggle `Client authentication` to `On` (in other IdPs than Keycloak, this setting might be
      called "Confidential client")
    - In `Authentication flow` keep the `Standard flow`, this refers to the recommended "
      Authorization Code Flow"
        - do NOT enable any of the legacy flows (e.g. Direct access grants, Implicit flow)
    - Set `PKCE Method` to `S256` (optional, but recommended for higher security - this refers to
      `pkceEnabled = true` in the waltid-ktor-authnz configuration)
        - use the secure `S256` method (best practise), do NOT use `plain`
- Under `3) Login settings`, you have to configure some URLs:
    - `Root URL`: Ktor application base URL, e.g. `http://localhost:8080`
    - `Home URL`: usually the same URL as `Root URL` (This is the link Keycloak will use for "back
      to application" buttons)
    - `Valid redirect URIs`: Enter the exact, full URL defined in your OIDC Auth Config, e.g.
      `http://localhost:8080/auth/oidc/callback`
        - this is a critical security setting - Keycloak will only redirect to this specified URL
          after a successful login. Do not use wildcards (*) for this, as it is insecure.
    - `Valid post logout redirect URIs`: Enter `+`, this is a shortcut that tells Keycloak to reuse
      the list from `Valid redirect URIs`
    - `Web origins`: Enter `+`, this tells Keycloak to allow CORS requests from the **origins** of
      your `Valid redirect URIs` (http://localhost:8080)
        - this is necessary for browser-based OIDC flows like Front-Channel Logout
- You will be redirected to the newly created clients details page.
    - Scroll down to `Logout settings` and choose between `Front channel logout` and
      `Backchannel logout`
        - Recommended: use `backchannel logout`. You can do so by configuration `Logout settings`
          like this:
            - `Front channel logout`: `Off`
            - `Backchannel logout URL`: `<the backchannel logout url>`, e.g.
              `http://authnz-example.localhost:8088/auth/flows/oidc-example/oidc/logout/backchannel`
                - Note: waltid-ktor-authnz always provides theses endpoints for the "oidc" flow (at
                  the same level of the `/oidc/callback` endpoint):
                    - `POST oidc/logout/backchannel`
                    - `GET oidc/logout/frontchannel`
            - `Backchannel logout session requred`: `On`

Note down the following values shown in the Keycloak Web Interface:

- Note the following values at the newly created clients details page:
    - `Settings > General settings > Client ID`
        - _this will go into the ktor-authnz OIDC config as `"clientId": "<client id here>"`_
    - at `Credentials > Client Authenticator > Client Id and Secret` there is a hidden field
      `Client secret` - press the buttons to the right of the text field to show and copy the value
        - _this will go into the ktor-authnz OIDC config
          as `"clientSecret": "<client secret here>"`_
    - at `Settings > Capability config > PKCE Method`
        - _if this setting is turned on (if it is set to `S256`) set `"pkceEnabled": true`_ in the
          ktor-authnz OIDC config
    - at `Settings > Access settings > Valid redirect URIs`
        - _this will go into the ktor-authnz OIDC config as `"callbackUri": "<https:// URI here>"`_
- At the global `Realm settings` page in the section
  `General` (http://localhost:8080/admin/master/console/#/master/realm-settings) scroll down to the
  very bottom of the page to find `Endpoints` listing the link `OpenID Endpoint Configuration`
    - The link will usually looks like this (note the `/.well-known/openid-configuration`
      suffix): http://localhost:8080/realms/master/.well-known/openid-configuration
    - _this will go into the ktor-authnz OIDC config
      as `"openIdConfigurationUrl": "<http:// link here>"`_

## Edit ktor-authnz configuration

In this example, I have the waltid-ktor-authnz backend running at
`http://authnz-example.localhost:8080`.

Below is a flow configuration that matches what we setup with Keycloak above:

```json
{
  "method": "oidc",
  "config": {
    "openIdConfigurationUrl": "http://localhost:8080/realms/master/.well-known/openid-configuration",
    "clientId": "waltid_ktor_authnz",
    "clientSecret": "fzYFC6oAgbjozv8NoaXuOIfPxmT4XoVM",
    "pkceEnabled": true,
    "callbackUri": "http://authnz-example.localhost:8088/auth/flows/oidc-example/oidc/callback",
    "redirectAfterLogin": "http://authnz-example.localhost:8088/protected"
  },
  "success": true
}
```

Explanation of config values:

- You can provide either `openIdConfiguration` or `openIdConfigurationUrl`: `openIdConfiguration`
  requires the full OIDC configuration as JSON, the easier option is to supply the dynamic
  configuration endpoint (typically a `/.well-known/openid-configuration` suffixed URL) in
  `openIdConfigurationUrl`.
- `clientId` & `clientSecret`: data for this OIDC client to use for the authorization code flow with
  your IDP
- `pkceEnabled`: whether to use PKCE with the Authorization Code Flow (recommended)
- `callbackUri`: the callback URL of the ktor-authnz backend, which your IDP will redirect the user
  to
- `redirectAfterLogin`: where the ktor-authnz backend will redirect the user from the callback page

## Request flow

- User client initiates login at ktor-authnz backend, e.g. calling
  `http://authnz-example.localhost:8088/auth/flows/oidc-example/oidc/auth`
- After the auth flow evaluation, the ktor-authnz backend will answer accordingly:
  ```json
  {
    "session_id": "e0dc0dfe-cbfa-461a-b665-a456ba75e05e",
    "status": "CONTINUE_NEXT_STEP",
    "current_method": "oidc",
    "next_method": ["oidc"],
    "next_step": {
      "type": "redirect",
      "url": "http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fauthnz-example.localhost%3A8088%2Fauth%2Fflows%2Foidc-example%2Foidc%2Fcallback&state=pa0vlLnWrEYmEcZyige3XC47UTlBykxIUAazNztMqcM&nonce=a11kjBnJrNOLNOl0b8Byf0FRE9Hw1XJFIODw4U0GwbA&code_challenge=tbsTiQPedzF6qtmMP8_7nmITAEH_o85v5WywStR9w2k&code_challenge_method=S256"
    },
    "next_step_description": "The OIDC method requires you to open the Authentication URL in your web browser, and follow the steps of your Identity Provider from there. Please go ahead and open the authentication URL \"http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fauthnz-example.localhost%3A8088%2Fauth%2Fflows%2Foidc-example%2Foidc%2Fcallback&state=pa0vlLnWrEYmEcZyige3XC47UTlBykxIUAazNztMqcM&nonce=a11kjBnJrNOLNOl0b8Byf0FRE9Hw1XJFIODw4U0GwbA&code_challenge=tbsTiQPedzF6qtmMP8_7nmITAEH_o85v5WywStR9w2k&code_challenge_method=S256\" in your web browser.",
    "next_step_informational_message": "The \"oidc\" authentication method requires multiple-steps for authentication. Follow the steps in `next_step` (AuthSessionNextStepRedirectData) to complete the authentication method."
  }
  ```
- The client sees `next_step.type = redirect` and thus opens URL `next_step.url` (the IDP URL)
- The user interacts with the Keycloak login interface (possibly gives consent if configured at the
  IDP)
- If the user logged in successfully with the IDP, the IDP will redirect back to the ktor-authnz
  callback URL (configured in `callbackUri`)
- The ktor-authnz backend (on the `callbackUri` page) will exchange the tokens (/ perform PKCE), and
  after validation will mark the authnz session as successful, etc
- After the ktor-authnz backend completed its verifications, it will redirect the browser to the URL
  defined in `redirectAfterLogin` (typically a frontend URL)

## Use with Enterprise

### Enterprise `auth.conf` configuration

```hocon
# Configure the Auth Flow (refer to: waltid-ktor-authnz)
authFlow = {
    method: oidc,
    config: {
        openIdConfigurationUrl: "http://localhost:8080/realms/master/.well-known/openid-configuration",
        clientId: "waltid_ktor_authnz",
        clientSecret: "fzYFC6oAgbjozv8NoaXuOIfPxmT4XoVM",
        callbackUri: "http://waltid.enterprise.localhost:3000/auth/account/oidc/callback",
        pkceEnabled: true,
        redirectAfterLogin: "http://waltid.enterprise.localhost:3000"
    },
    success: true
}
```

### Keycloak configuration:

- Root URL: `http://waltid.enterprise.localhost:3000`
- Home URL: `http://waltid.enterprise.localhost:3000`
- Valid redirect URIs: `http://waltid.enterprise.localhost:3000/auth/account/oidc/callback`
- Valid post logout redirect URIs: `+`
- Web origins: `+`
- Admin URL: `http://waltid.enterprise.localhost:3000`
- Backchannel logout URL:
  `http://waltid.enterprise.localhost:3000/auth/account/oidc/logout/backchannel`

### Create a user in Keycloak:

- Click `Users > User list > [Add user]`
- enter Username (other data is optional)
- You are now on the `User details` page of the newly created user. Go to the tab `Credentials` and
  press the button `[Set password]` to set a password for the user, so the user can login.
- Note the user ID on the `Details` page, this is the OIDC subject.

### Add OIDC user to Enterprise

`POST /v1/admin/account/register`

```json
{
  "profile": {
    "name": "Test User",
    "email": "testuser@walt.id",
    "addressCountry": "AT",
    "address": "12345 Vienna"
  },
  "preferences": {
    "timeZone": "UTC",
    "languagePreference": "EN"
  },
  "initialAuth": {
    "type": "oidc",
    "identifier": {
      "type": "oidc",
      "issuer": "http://localhost:8080/realms/master",
      "subject": "412cf56f-f85a-4247-91a6-f538867e2470"
    }
  }
}
```

`201 Created`

Set items in your request:

- `issuer`: The issuer value of your IDP, can be found in the OpenID Endpoint Configuration (for
  Keycloak, go to the global
  `Configure > Realm settings > General > Endpoints > OpenID Endpoint Configuration` (very bottom of
  the page)), e.g. `http://localhost:8080/realms/master/.well-known/openid-configuration` - in this
  JSON document the very first attribute usually is already the `issuer` to use
- `subject`: The `sub` value of the user at the IDP to link, in Keycloak:
  `Manage > Users > User list > (Username) > ID`

Alternatively, it can be set for an already existing account:
`POST /v1/admin/account/auth/{account-id}/add-initial`

```json
{
  "type": "oidc",
  "identifier": {
    "type": "oidc",
    "issuer": "http://localhost:8080/realms/master",
    "subject": "412cf56f-f85a-4247-91a6-f538867e2470"
  }
}
```

`201 Created`

As you can see, the "`oidc`" Identifier type uses the combination of `issuer` and `subject` as a
unique identifier (as a subject at an IDP has to be unique for the IDP) for the account. When a user
is logged in with the OIDC method, the resulting OIDC `ID Token` is resolved into this combination
and as such the linked account.

### User login

`http://waltid.enterprise.localhost:3000/auth/account/oidc/auth`

```json
{
  "session_id": "bdb2a9a8-a56d-4ae4-8ea6-3585539b5285",
  "status": "CONTINUE_NEXT_STEP",
  "current_method": "oidc",
  "next_method": [
    "oidc"
  ],
  "next_step": {
    "type": "redirect",
    "url": "http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fwaltid.enterprise.localhost%3A3000%2Fauth%2Faccount%2Foidc%2Fcallback&state=gnMiC87YaRlnpMaLHqgmWAEdquAzAKzSCNSJdyAEpo8&nonce=JDBsZK9IBl_xMFavT3u0M2xob4kYnZbiHUbSoZFOOrA&code_challenge=80P_Ui2zTq26R5DmU_O4EHTOhWHaOtdF-oIl6CIaomw&code_challenge_method=S256"
  },
  "next_step_description": "The OIDC method requires you to open the Authentication URL in your web browser, and follow the steps of your Identity Provider from there. Please go ahead and open the authentication URL \"http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fwaltid.enterprise.localhost%3A3000%2Fauth%2Faccount%2Foidc%2Fcallback&state=gnMiC87YaRlnpMaLHqgmWAEdquAzAKzSCNSJdyAEpo8&nonce=JDBsZK9IBl_xMFavT3u0M2xob4kYnZbiHUbSoZFOOrA&code_challenge=80P_Ui2zTq26R5DmU_O4EHTOhWHaOtdF-oIl6CIaomw&code_challenge_method=S256\" in your web browser.",
  "next_step_informational_message": "The \"oidc\" authentication method requires multiple-steps for authentication. Follow the steps in `next_step` (AuthSessionNextStepRedirectData) to complete the authentication method."
}
```

Visit specified URL at IDP (typically automatically opened via JavaScript):
`http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fwaltid.enterprise.localhost%3A3000%2Fauth%2Faccount%2Foidc%2Fcallback&state=gnMiC87YaRlnpMaLHqgmWAEdquAzAKzSCNSJdyAEpo8&nonce=JDBsZK9IBl_xMFavT3u0M2xob4kYnZbiHUbSoZFOOrA&code_challenge=80P_Ui2zTq26R5DmU_O4EHTOhWHaOtdF-oIl6CIaomw&code_challenge_method=S256`

IDP (Keycloak) displays user sign in page:

```markdown
# Sign in to your account

Username or email: ______
Password: ______

[Sign in]
```

After pressing `[Sign in]`, User is redirected to the Enterprise Callback (specified in
`callbackUri`), which then redirects the user to the page specified in auth flow config value
`redirectAfterLogin` (typically a frontend page).

Test: `GET http://waltid.enterprise.localhost:3000/v1/account/info`:

```json
{
  "_id": "eaf0ad58-d430-4fd4-ac05-c9b64d6b25e5",
  "type": "USER",
  "profile": {
    "name": "Test User",
    "email": "testuser@walt.id",
    "addressCountry": "AT",
    "address": "12345 Vienna"
  },
  "accountPreferences": {
    "timeZone": "UTC",
    "languagePreference": "EN"
  }
}
```
