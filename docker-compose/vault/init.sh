#!/bin/sh

set -e

echo "Initialize Vault (if not already initialized)"
if ! vault status | grep -q "Initialized"; then
    vault operator init -key-shares=1 -key-threshold=1 > /vault/file/init.log
    vault operator unseal $(grep 'Unseal Key 1:' /vault/file/init.log | awk '{print $NF}')
    vault login $(grep 'Initial Root Token:' /vault/file/init.log | awk '{print $NF}')
fi

vault login "$VAULT_DEV_ROOT_TOKEN_ID"

echo "Enable Transit Secrets Engine"
vault secrets enable transit

echo "Create an encryption key"
vault write -f transit/keys/my-encryption-key

echo "Enable Userpass Authentication"
vault auth enable userpass

echo "Create a User with Userpass Authentication"
vault write auth/userpass/users/my-user password=my-password policies=transit-policy

echo "Enable AppRole Authentication"
vault auth enable approle

echo "Create a Policy for Transit Secrets Engine"
vault policy write transit-policy - <<EOF
path "transit/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
EOF

echo "Create an AppRole with the defined policy"
vault write auth/approle/role/my-role \
    token_policies="transit-policy" \
    token_ttl=120m \
    token_max_ttl=120m

echo "Generate Secret ID"
SECRET_ID=$(vault write -f -field=secret_id auth/approle/role/my-role/secret-id)

# Output Role ID
ROLE_ID=$(vault read -field=role_id auth/approle/role/my-role/role-id)
echo "Role ID: $ROLE_ID"

# Output Secret ID
echo "Secret ID: $SECRET_ID"