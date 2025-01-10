#!/bin/bash

# Constant

## Url
readonly ISSUER_URL="https://e824-2a02-587-6a2a-fb00-299a-530c-590a-344f.ngrok-free.app"
readonly ISSUER_URL_WITH_STANDARD_VERSION="${ISSUER_URL}/draft11"
readonly EBSI_CONFORMANCE_API_URL="https://api-conformance.ebsi.eu"

readonly ISSUER_DID="did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbtANUSeJyVFB45Gh1at2EMcHbEoMmJVSpaGEu4xGk8b8susD83jxL3jZJ4VbNcq3diik4RVCi3ea6VPfjNNCEyESEWK4w5z89uezUUUc13ssTPkncXEUeoKayqCbX4aJLfW"
readonly JWK='
  {
      "type": "jwk",
      "jwk": {
        "kty": "EC",
        "x": "zK8OWXyBYBH0PJxMf5CsbVeGBDoNNHgcUfXN2fjUazs",
        "y": "FcMlAJxSKsvmN9RQPkPZYvJnju7xZLuVEGHi7zatwX0",
        "crv": "P-256",
        "d": "'$EBSI_CT_PRIVATE_KEY_PARAM'"
    }
  }
'
readonly STANDARD_VERSION="DRAFT11"

## Credential Type
readonly CREDENTIAL_TYPE_PRE_AUTHORIZED_IN_TIME="CTWalletSamePreAuthorisedInTime"

readonly CREDENTIAL_TYPE_PRE_AUTHORIZED_DEFERRED="CTWalletSamePreAuthorisedDeferred"

readonly CREDENTIAL_TYPE_AUTHORIZED_IN_TIME="CTWalletSameAuthorisedInTime"

readonly CREDENTIAL_TYPE_AUTHORIZED_DEFERRED="CTWalletSameAuthorisedDeferred"


## EBSI Conformance API Intent
readonly INITIATE_PRE_AUTHORIZED_IN_TIME="issue_to_holder_initiate_ct_wallet_same_pre_authorised_in_time"
readonly VALIDATE_PRE_AUTHORIZED_IN_TIME="issue_to_holder_validate_ct_wallet_same_pre_authorised_in_time"

readonly INITIATE_PRE_AUTHORIZED_DEFERRED="issue_to_holder_initiate_ct_wallet_same_pre_authorised_deferred"
readonly VALIDATE_PRE_AUTHORIZED_DEFERRED="issue_to_holder_validate_ct_wallet_same_pre_authorised_deferred"


readonly INITIATE_AUTHORIZED_IN_TIME="issue_to_holder_initiate_ct_wallet_same_authorised_in_time"
readonly VALIDATE_AUTHORIZED_IN_TIME="issue_to_holder_validate_ct_wallet_same_authorised_in_time"

readonly INITIATE_AUTHORIZED_DEFERRED="issue_to_holder_initiate_ct_wallet_same_authorised_deferred"
readonly VALIDATE_AUTHORIZED_DEFERRED="issue_to_holder_validate_ct_wallet_same_authorised_deferred"


# Utility Functions
function urldecode() { : "${*//+/ }"; echo -e "${_//%/\\x}"; }

function perform_http_post() {
    local url=$1 data=$2
    curl --silent --show-error --fail --location "$url" \
         --header 'Content-Type: application/json' \
         --data "$data"
}

function perform_http_post_raw() {
    local url=$1 data=$2
    curl --silent --show-error --fail --location "$url" \
         --header 'Content-Type: application/json' \
         --data-raw "$data"
}

function perform_ebsi_intent() {
    local intent=$1 authenticationMethod=$2 issuerStateOrpreAuthorizationCode=$3

    local commonData='"clientId": "'$ISSUER_URL_WITH_STANDARD_VERSION'",
        "did": "'$ISSUER_DID'",
        "credentialIssuer": "'$ISSUER_URL_WITH_STANDARD_VERSION'",
        "credentialIssuerDid": "'$ISSUER_DID'"'

    local additionalField
    case "$authenticationMethod" in
        "PRE_AUTHORIZED")
            additionalField='"preAuthorizedCode": "'$issuerStateOrpreAuthorizationCode'", "userPin": "1234"'
            ;;
        "ID_TOKEN")
            additionalField='"issuerState": "'$issuerStateOrpreAuthorizationCode'"'
            ;;
        *)
            echo "Invalid authentication method: $authenticationMethod" >&2
            exit 1
            ;;
    esac

    local data='{
        "data": {
            '$commonData',
            '$additionalField'
        },
        "intent": "'$intent'"
    }'


    HTTP_RESPONSE=$(perform_http_post "$EBSI_CONFORMANCE_API_URL/conformance/v3/check" "$data")

    validate_ebsi_http_response "$HTTP_RESPONSE"
}

function validate_ebsi_http_response() {
    local response=$1
    local success
    success=$(echo "$response" | jq -r '.success')

    if [[ "$success" != "true" ]]; then
      echo "Error: EBSI CT Intent failed. Response:"
      echo "$response"
      exit 1
    fi
}

function create_credential_offer() {

    local credentialType=$1 authenticationMethod=$2

    local payload='{
      "credentialConfigurationId": "'${credentialType}'_jwt_vc_json",
      "standardVersion": "'${STANDARD_VERSION}'",
      "issuerKey": '${JWK}',
      "issuerDid": "'${ISSUER_DID}'",
      "authenticationMethod": "'${authenticationMethod}'"
      "credentialData": {
        "@context": [
            "https://www.w3.org/2018/credentials/v1"
        ],
        "id": "https://www.w3.org/2018/credentials/123",
        "type": [
            "VerifiableCredential",
            "VerifiableAttestation",
            "'${credentialType}'"
        ],
        "issuanceDate": "2020-03-10T04:24:12Z",
        "credentialSubject": {
            "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz"
        }
      },
      "mapping": {
        "id": "<uuid>",
        "issuer": "<issuerDid>",
        "credentialSubject": {
            "id": "<subjectDid>"
        },
        "issuanceDate": "<timestamp-ebsi>",
        "issued": "<timestamp-ebsi>",
        "validFrom": "<timestamp-ebsi>",
        "expirationDate": "<timestamp-ebsi-in:365d>",
        "credentialSchema": {
          "id": "https://api-conformance.ebsi.eu/trusted-schemas-registry/v3/schemas/z3MgUFUkb722uq4x3dv5yAJmnNmzDFeK5UC8x83QoeLJM",
          "type": "FullJsonSchemaValidator2021"
        }
      },
      "useJar": true
    }'

    perform_http_post_raw "${ISSUER_URL}/openid4vc/jwt/issue" "${payload}"
}

function run_test() {
    local credentialType=$1 initiateIntent=$2 validateIntent=$3 authenticationMethod=$4

    printf "####################\n"
    printf "Start Testing Credential Type: %s\n" "$credentialType"
    printf "####################\n\n"


    # Step 1: Create Credential Offer
    printf "Creating Credential Offer...\n"

    encodedOfferRespone=$(create_credential_offer "$credentialType" "$authenticationMethod")
    decodedOfferResponse=$(urldecode $encodedOfferRespone)
    offerUrl="${decodedOfferResponse##*credential_offer_uri=}"
    printf "Credential Offer URL: %s\n\n" "$offerUrl"


    # Step 2: Get Credential Offer
    printf "Retrieving Credential Offer...\n"
    offer=$(curl -X GET "$offerUrl")


    # Step 3: Extract Issuer State or Pre-Authorized Code and Trigger EBSI CT Test
    printf "Extracting Issuer State or Pre-Authorized Code...\n"

    grants=$(jq -r '.grants' <<< "$offer")

    if [[ "$authenticationMethod" == "PRE_AUTHORIZED" ]]; then
        local preAuthorizationCodeObject
        local preAuthorizationCode
        preAuthorizationCodeObject=$(jq -r '."urn:ietf:params:oauth:grant-type:pre-authorized_code"' <<< "$grants")
        preAuthorizationCode=$(jq -r '."pre-authorized_code"' <<< "$preAuthorizationCodeObject")
        printf "Pre-Authorized Code: %s\n\n" "$preAuthorizationCode"

        printf "Triggering EBSI Conformance API...\n"
        perform_ebsi_intent "$initiateIntent" "$authenticationMethod" "$preAuthorizationCode"
        perform_ebsi_intent "$validateIntent" "$authenticationMethod" "$preAuthorizationCode"

    elif [[ "$authenticationMethod" == "ID_TOKEN" ]]; then
        local authorizationCode
        local issuerState
        authorizationCode=$(jq -r '.authorization_code' <<< "$grants")
        issuerState=$(jq -r '.issuer_state' <<< "$authorizationCode")
        printf "Issuer State: %s\n" "$issuerState"

        printf "Triggering EBSI Conformance API...\n"
        perform_ebsi_intent "$initiateIntent" "$authenticationMethod" "$issuerState"
        perform_ebsi_intent "$validateIntent" "$authenticationMethod" "$issuerState"

    else
        echo "Invalid authentication method: $authenticationMethod" >&2
        exit 1
    fi

    printf "\n####################\n"
    printf "Test Completed Successfully for %s\n" "$credentialType"
    printf "####################\n\n\n"
}

function main() {
    run_test "${CREDENTIAL_TYPE_PRE_AUTHORIZED_IN_TIME}" \
        "${INITIATE_PRE_AUTHORIZED_IN_TIME}" \
        "${VALIDATE_PRE_AUTHORIZED_IN_TIME}" \
        "PRE_AUTHORIZED"

    run_test "${CREDENTIAL_TYPE_PRE_AUTHORIZED_DEFERRED}" \
        "${INITIATE_PRE_AUTHORIZED_DEFERRED}" \
        "${VALIDATE_PRE_AUTHORIZED_DEFERRED}" \
        "PRE_AUTHORIZED"

    run_test "${CREDENTIAL_TYPE_AUTHORIZED_IN_TIME}" \
        "${INITIATE_AUTHORIZED_IN_TIME}" \
        "${VALIDATE_AUTHORIZED_IN_TIME}" \
        "ID_TOKEN"

    run_test "${CREDENTIAL_TYPE_AUTHORIZED_DEFERRED}" \
        "${INITIATE_AUTHORIZED_DEFERRED}" \
        "${VALIDATE_AUTHORIZED_DEFERRED}" \
        "ID_TOKEN"
}

main "$@"