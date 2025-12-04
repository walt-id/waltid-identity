# WalletHolderCredential

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1", "ndid:context:walletHolder"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": ["VerifiableCredential", "WalletHolderCredential"],
    "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "walletHolderCredential": {
            "firstName": "สมศักดิ์",
            "lastName": "มั่งมี",
            "address": "92/222 ซอยเสรีไทย  กรุงเทพมหานคร 10240 THAILAND",
            "identifier": "3199611153090",
            "namespace": "citizen_id",
            "serviceID": "001.cust_info_001",
            "requestID": "1f8ba8218162e354c028be342f507517e6bbceae9af3ff689551eea3981a0189",
            "requestMessage": "ท่านกำลังยืนยันตัวตนเพื่อใช้ตามวัตถุประสงค์ของ National Digital ID Wallet และประสงค์ให้ส่งข้อมูลจาก ธนาคารกรุงศรี (Transaction Ref: 31817034)",
            "salt": "tlVqHnQ8HfipI9r2WnL83QPH6Z01yBo0DQaQv7RyYSQ=",
            "aal": 2.2,
            "ial": 2.3,
            "idp": "DC9E1ACB-D450-438F-9558-FE7672F3F3EB"
        }
    },
    "credentialStatus": {
        "id": "0x2ef196ba560d7d72f13d80f405841bc84cec229b70a0a05557c5388d215bb224",
        "type": "NDIDCredentialStatus2021"
    },
    "credentialSchema": {
        "id": "ndid:schema:walletHolder",
        "type": "JsonSchemaValidator2018"
    }
}
```

## Manifest

```json
{
    "claims": {
        "Credential Subject ID": "$.credentialSubject.id",
        "First Name": "$.credentialSubject.walletHolderCredential.firstName",
        "Last Name": "$.credentialSubject.walletHolderCredential.lastName",
        "Address": "$.credentialSubject.walletHolderCredential.address",
        "Identifier": "$.credentialSubject.walletHolderCredential.identifier",
        "Namespace": "$.credentialSubject.walletHolderCredential.namespace",
        "Service ID": "$.credentialSubject.walletHolderCredential.serviceID",
        "Request ID": "$.credentialSubject.walletHolderCredential.requestID",
        "Request Message": "$.credentialSubject.walletHolderCredential.requestMessage",
        "AAL (Authentication Assurance Level)": "$.credentialSubject.walletHolderCredential.aal",
        "IAL (Identity Assurance Level)": "$.credentialSubject.walletHolderCredential.ial",
        "IDP": "$.credentialSubject.walletHolderCredential.idp"
    }
}
```

## Mapping example

```json
{
    "id": "<uuid>",
    "issuer": "<issuerDid>",
    "credentialSubject": {
        "id": "<subjectDid>"
    },
    "issuanceDate": "<timestamp>"
}
```