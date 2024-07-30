## Most simple auth

### 1. Request (Client -> AuthKit): UserPass

Choose from:

### 1.1 Plain
```http request
POST /auth/userpass
```
```json
{
  "username": "abc",
  "password": "xyz"
}
```

### 1.2 Basic
```http request
POST /auth/userpass/basic
Authorization: Basic YWJjOnh5eg==
```

### 1.3 Form
```http request
POST /auth/userpass/form
Content-Type: application/x-www-form-urlencoded

username=abc&password=xyz
```

### 2. Response (AuthKit -> Client): AuthSession
```json5
{
  "session": "a662b620-7d89-41bf-a823-63d0179d82f1", // Session ID
  "status": "ok", //  ok, continue_next_step, fail
  
  // Due to success, the following is available:
  "user": "dcedede5-b4e1-49e4-b62b-4801b8343311",
  "auth_token": "einahf0Gohng4phoob9aeso3ilethaef5eec", // Token generated, used for subsequent authentication
  //"valid_until": "2024-07-25T21:53:47Z"
}
```

### 3. Use authentication (Client -> Server): SomeRequest
```http request
GET /my-secure-service/xyz
Authorization: Bearer einahf0Gohng4phoob9aeso3ilethaef5eec
```
