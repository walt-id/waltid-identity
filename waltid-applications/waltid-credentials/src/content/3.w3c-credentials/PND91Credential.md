# PND91Credential

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1", "ndid:context:PND9XContext"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": ["VerifiableCredential", "PND91Credential"],
    "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "PND9XCredential": [
            {
                "taxYear": "2565",
                "txpNid": "3199611153090",
                "txpTtlText": "นาย",
                "txpFName": "สมศักดิ์",
                "txpLName": "มั่งมี",
                "txpBirthDate": "25260909",
                "formInfo": [
                    {
                        "txpAlwSsfFundAmt": 0,
                        "txpTotAssInc408Amt": 0,
                        "txpExe65YearAmt": 0,
                        "soi": "เสรีไทย",
                        "nidPayer401": "0105558007281",
                        "effDate": "25640630",
                        "province": "กรุงเทพมหานคร",
                        "txpMryStaInd": "7",
                        "nidPayer404": "0105558033583",
                        "road": "เสรีไทย",
                        "txpTotAssInc404Amt": 99000,
                        "subInd": "0",
                        "txpAlwIntLoanAmt": 9999.99,
                        "postcode": "10240",
                        "txpAlwRmfAmt": 0,
                        "txpTotAssInc405Amt": 0,
                        "txpAlwLtfAmt": 0,
                        "txpTotAssInc401Amt": 99999,
                        "txpNetTaxAmt": 9999.99,
                        "txpExeGpfAmt": 0,
                        "txpAlwNsavAmt": 0,
                        "txpWhtAmt": 999999.99,
                        "amphoe": "บึงกุ่ม",
                        "txpAlwLiftInsHltAmt": 0,
                        "txpExeCpfAmt": 0,
                        "txpTotAssInc406Amt": 0,
                        "txpStaInd": "8",
                        "uid": "0000600025640630300046410",
                        "txpAlwLiftInsPrmAmt": 0,
                        "txpTotAssInc402Amt": 0,
                        "txpExeSllAmt": 0,
                        "village": "เสริมสุข",
                        "tambon": "คลองกุ่ม",
                        "txpNetIncAmt": 999999.99,
                        "formCode": "ภงด90",
                        "txpTotAssInc407Amt": 0,
                        "txpAddTaxAmt": 0,
                        "namePayer401": "บริษัทเช็กเกอร์ จำกัด",
                        "txpTotAssInc403Amt": 0,
                        "addressNo": "92/222",
                        "txpOvrTaxAmt": 99999.99,
                        "txpAlwLiftInsPensAmt": 0,
                        "txpIncSpecAmt": 0,
                        "txpAlwSsfAmt": 4050
                    }
                ]
            }
        ]
    },
    "credentialStatus": {
        "id": "0xf48a1c11d0d4a4cd7b0aae2407862e67d359f173e2edf105994be0440021f085",
        "type": "NDIDCredentialStatus2021"
    },
    "credentialSchema": {
        "id": "ndid:schema:PND9XSchema",
        "type": "JsonSchemaValidator2018"
    }
}
```

## Manifest

```json
{
    "claims": {
        "ID": "$.credentialSubject.id"
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