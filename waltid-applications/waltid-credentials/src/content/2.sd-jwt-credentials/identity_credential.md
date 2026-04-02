# Identity Credential

This is an example of an IETF SD-JWT (Selective Disclosure JWT) Verifiable Credential for identity verification. 

Source: https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/

## Example SD-JWT VC

```json
{
  "vct": "https://credentials.example.com/identity_credential",
  "given_name": "John",
  "family_name": "Doe",
  "email": "johndoe@example.com",
  "phone_number": "+1-202-555-0101",
  "address": {
    "street_address": "123 Main St",
    "locality": "Anytown",
    "region": "Anystate",
    "country": "US"
  },
  "birthdate": "1940-01-01",
  "is_over_18": true,
  "is_over_21": true,
  "is_over_65": true
}
```


## Usage

This credential demonstrates the IETF SD-JWT standard for selective disclosure of verifiable credential claims, allowing users to prove specific attributes without revealing their entire identity.
Further information how SD-JWT VCs can be issued can be found here: https://docs.walt.id/concepts/digital-credentials/sd-jwt-vc
