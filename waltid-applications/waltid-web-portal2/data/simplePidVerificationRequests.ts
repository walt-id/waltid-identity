export interface SimplePidVerificationRequestOption {
  id: string;
  label: string;
  description: string;
  requestBody: Record<string, unknown>;
}

interface SimplePidRequestMaterial {
  signedRequest?: boolean;
  encryptedResponse?: boolean;
  clientId?: string;
  key?: Record<string, unknown>;
  x5c?: string[];
  verifierInfo?: Array<Record<string, unknown>>;
}

const GERMAN_EUDI_WALLET_KEY = {
  type: "jwk",
  jwk: {
    kty: "EC",
    crv: "P-256",
    x: "8VoEZqKzypAcaCIQ8hrd2YIRCExkSvJRYiWxi0YNDCY",
    y: "B19upk_qV_Rzu5y75SJVdUV8aTSUerbDCXAJdkPaZpc",
    d: "1FcMsm7HhM0LK8nRFyDLuy4Ggn1Yg_6yRy5mXjwNV-s",
  },
};

const GERMAN_EUDI_WALLET_X5C = [
  "MIIDNjCCAt2gAwIBAgIQBvlTHoTShzBBTeJJVOwBjTAKBggqhkjOPQQDAjCBjzEeMBwGA1UEAxMVUmVhZGVyIENBIENlcnRpZmljYXRlMQswCQYDVQQGEwJGUjEVMBMGA1UEChMMRVVESVcgVW5mb2xkMR4wHAYDVQQLExVDZXJ0aWZpY2F0ZSBBdXRob3JpdHkxKTAnBgNVBAUTIDQwNDE0MjQzNDQ0NTQ2NDc0ODQ5NEE0QjRDNEQ0RTRGMB4XDTI2MDYwNDE1MzIyMFoXDTI5MDkwMzE1MzIyMFowdTFGMEQGA1UEAxM9bWRvYyBSZWFkZXIgQXV0aGVudGljYXRpb24gQ2VydGlmaWNhdGUgdmVyaWZpZXIyLmRlbW8ud2FsdC5pZDELMAkGA1UEBhMCQVQxDzANBgNVBAgTBlZpZW5uYTENMAsGA1UEChMEV2FsdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABPFaBGais8qQHGgiEPIa3dmCEQhMZEryUWIlsYtGDQwmB19upk/qV/Rzu5y75SJVdUV8aTSUerbDCXAJdkPaZpejggEyMIIBLjAdBgNVHQ4EFgQUDvjneLBmEZV3pDIWnd/t7GPR9c4wDgYDVR0PAQH/BAQDAgeAMCEGA1UdEQQaMBiCFnZlcmlmaWVyMi5kZW1vLndhbHQuaWQwFQYDVR0lAQH/BAswCQYHKIGMXQUBBjBSBgNVHR8ESzBJMEegRaBDhkFodHRwczovL3VuZm9sZC5tZG9jLm9ubGluZS9DZXJ0aWZpY2F0ZXMvMS9SZWFkZXJDYUNlcnRpZmljYXRlLmNybDAfBgNVHSMEGDAWgBQN9ximilsuU4x4quk+86Tge3GOoTBOBgNVHRIERzBFhhtodHRwczovL3VuZm9sZC5tZG9jLm9ubGluZS+BJnJlYWRlcmNhY2VydGlmaWNhdGVAdW5mb2xkLm1kb2Mub25saW5lMAoGCCqGSM49BAMCA0cAMEQCIDNZhYs23qySJvJvJBOk6Et2uSvF/OimwjJYOtlfIU+mAiASMBqnOCZJt+C6blPRVj1mBbp2/+EzYsUCwmQTaANRQA==",
];

const GERMAN_VERIFIER_INFO = [
  {
    format: "jwt",
    data: "eyJ0eXAiOiJyYy13cnArand0IiwieDVjIjpbIk1JSUNMekNDQWRTZ0F3SUJBZ0lVSHlSakU0NjZZQTd0Yzg4OGswM091MlFvZEY0d0NnWUlLb1pJemowRUF3SXdLREVMTUFrR0ExVUVCaE1DUkVVeEdUQVhCZ05WQkFNTUVFZGxjbTFoYmlCU1pXZHBjM1J5WVhJd0hoY05Nall3TVRFMk1URXhOVFUwV2hjTk1qZ3dNVEUyTVRFeE5UVTBXakFvTVFzd0NRWURWUVFHRXdKRVJURVpNQmNHQTFVRUF3d1FSMlZ5YldGdUlGSmxaMmx6ZEhKaGNqQlpNQk1HQnlxR1NNNDlBZ0VHQ0NxR1NNNDlBd0VIQTBJQUJNZWZZMlg0aXhmUmtXRXZwOWdyRjJpMjF6NlBLWnNyOHp6QmFKLytHbm90Q2VIMmNKNkd0TGh4WGhIZkpqckVUc01OSUdoVmFKb0hvSGNaVEJISnJmeWpnZHN3Z2Rnd0hRWURWUjBPQkJZRUZLbkNvOW92YmF4VTdzNjVUdWdzeVN3QWc0QXpNQjhHQTFVZEl3UVlNQmFBRktuQ285b3ZiYXhVN3M2NVR1Z3N5U3dBZzRBek1CSUdBMVVkRXdFQi93UUlNQVlCQWY4Q0FRQXdEZ1lEVlIwUEFRSC9CQVFEQWdFR01Db0dBMVVkRWdRak1DR0dIMmgwZEhCek9pOHZjMkZ1WkdKdmVDNWxkV1JwTFhkaGJHeGxkQzV2Y21jd1JnWURWUjBmQkQ4d1BUQTdvRG1nTjRZMWFIUjBjSE02THk5ellXNWtZbTk0TG1WMVpHa3RkMkZzYkdWMExtOXlaeTl6ZEdGMGRYTXRiV0Z1WVdkbGJXVnVkQzlqY213d0NnWUlLb1pJemowRUF3SURTUUF3UmdJaEFJWTdFUnBSckRSbDBscjVINXV4ako4M0pSNHF1YTJzZlBLeFgrcGw0UXcrQWlFQTJxTDZMWFZPUkEycjJWWmpTRWtuZmNpd0lHN2xhQTEya2pueUdBRDNWL0E9Il0sImFsZyI6IkVTMjU2In0.eyJuYW1lIjoiSGFja2F0aG9uIC0gV2FsdCIsInN1Yl9sbiI6IkhhY2thdGhvbiAtIFdhbHQiLCJzdWIiOiJFVUlERS1EQUREQjI2NkM4N0MxNTEyIiwiY291bnRyeSI6IkRFIiwicmVnaXN0cnlfdXJpIjoiaHR0cHM6Ly9zYW5kYm94LmV1ZGktd2FsbGV0Lm9yZyIsInNydl9kZXNjcmlwdGlvbiI6W1t7ImxhbmciOiJlbiIsImNvbnRlbnQiOiJXZWIgUmVseWluZyBQYXJ0eSJ9XV0sImVudGl0bGVtZW50cyI6WyJodHRwczovL3VyaS5ldHNpLm9yZy8xOTQ3NS9FbnRpdGxlbWVudC9TZXJ2aWNlX1Byb3ZpZGVyIl0sInByaXZhY3lfcG9saWN5IjoiaHR0cHM6Ly93YWx0LmlkIiwiaW5mb191cmkiOiJodHRwczovL3NhbmRib3guZXVkaS13YWxsZXQub3JnIiwic3VwcG9ydF91cmkiOiJodHRwczovL3dhbHQuaWQiLCJzdXBlcnZpc29yeV9hdXRob3JpdHkiOnsiZW1haWwiOiJwb3N0c3RlbGxlQGJmZGkuYnVuZC5kZSIsInBob25lIjoiKzQ5ICgwKTIyOC05OTc3OTktMCIsInVyaSI6Imh0dHBzOi8vd3d3LmJmZGkuYnVuZC5kZS9FTi9Ib21lL2hvbWVfbm9kZS5odG1sIn0sImlhdCI6MTc4MDU2NzY3OSwic3RhdHVzIjp7InN0YXR1c19saXN0Ijp7ImlkeCI6MjQ2MywidXJpIjoiaHR0cHM6Ly9zYW5kYm94LmV1ZGktd2FsbGV0Lm9yZy9zdGF0dXMtbWFuYWdlbWVudC9zdGF0dXMtbGlzdCJ9fSwicHVycG9zZSI6W3sibGFuZyI6ImVuLVVTIiwiY29udGVudCI6IlRlc3QgY29tcGxpYW5jZSB3aXRoIFdhbHQuaWQgUlAifV0sImNyZWRlbnRpYWxzIjpbeyJmb3JtYXQiOiJtc29fbWRvYyIsIm1ldGEiOnsiZG9jdHlwZV92YWx1ZSI6ImV1LmV1cm9wYS5lYy5ldWRpLnBpZC4xIn0sImNsYWltIjpbeyJwYXRoIjpbImZhbWlseV9uYW1lIiwiZ2l2ZW5fbmFtZSIsImJpcnRoX2RhdGUiXX1dfV19.sILGOnOzYfX5VbfjNUQfOgRqziL7Gshq_LSSUJp--kkELqEdETfA2BJ09wJvX2_KJLVFJG3XYlIt_81pu41Rbg",
  }
]

const FRANCE_IDENTITE_KEY = {
  type: "jwk",
  jwk: {
    kty: "EC",
    crv: "P-256",
    x: "8VoEZqKzypAcaCIQ8hrd2YIRCExkSvJRYiWxi0YNDCY",
    y: "B19upk_qV_Rzu5y75SJVdUV8aTSUerbDCXAJdkPaZpc",
    d: "1FcMsm7HhM0LK8nRFyDLuy4Ggn1Yg_6yRy5mXjwNV-s",
  },
};

const FRANCE_IDENTITE_X5C =
  ["MIIDNjCCAt2gAwIBAgIQBvlTHoTShzBBTeJJVOwBjTAKBggqhkjOPQQDAjCBjzEeMBwGA1UEAxMVUmVhZGVyIENBIENlcnRpZmljYXRlMQswCQYDVQQGEwJGUjEVMBMGA1UEChMMRVVESVcgVW5mb2xkMR4wHAYDVQQLExVDZXJ0aWZpY2F0ZSBBdXRob3JpdHkxKTAnBgNVBAUTIDQwNDE0MjQzNDQ0NTQ2NDc0ODQ5NEE0QjRDNEQ0RTRGMB4XDTI2MDYwNDE1MzIyMFoXDTI5MDkwMzE1MzIyMFowdTFGMEQGA1UEAxM9bWRvYyBSZWFkZXIgQXV0aGVudGljYXRpb24gQ2VydGlmaWNhdGUgdmVyaWZpZXIyLmRlbW8ud2FsdC5pZDELMAkGA1UEBhMCQVQxDzANBgNVBAgTBlZpZW5uYTENMAsGA1UEChMEV2FsdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABPFaBGais8qQHGgiEPIa3dmCEQhMZEryUWIlsYtGDQwmB19upk/qV/Rzu5y75SJVdUV8aTSUerbDCXAJdkPaZpejggEyMIIBLjAdBgNVHQ4EFgQUDvjneLBmEZV3pDIWnd/t7GPR9c4wDgYDVR0PAQH/BAQDAgeAMCEGA1UdEQQaMBiCFnZlcmlmaWVyMi5kZW1vLndhbHQuaWQwFQYDVR0lAQH/BAswCQYHKIGMXQUBBjBSBgNVHR8ESzBJMEegRaBDhkFodHRwczovL3VuZm9sZC5tZG9jLm9ubGluZS9DZXJ0aWZpY2F0ZXMvMS9SZWFkZXJDYUNlcnRpZmljYXRlLmNybDAfBgNVHSMEGDAWgBQN9ximilsuU4x4quk+86Tge3GOoTBOBgNVHRIERzBFhhtodHRwczovL3VuZm9sZC5tZG9jLm9ubGluZS+BJnJlYWRlcmNhY2VydGlmaWNhdGVAdW5mb2xkLm1kb2Mub25saW5lMAoGCCqGSM49BAMCA0cAMEQCIDNZhYs23qySJvJvJBOk6Et2uSvF/OimwjJYOtlfIU+mAiASMBqnOCZJt+C6blPRVj1mBbp2/+EzYsUCwmQTaANRQA=="];

const EUDI_REFERENCE_WALLET_KEY = {
  type: "jwk",
  jwk: {
    "kty": "EC",
    "x": "pTqq2Z8iAVzn8pwWRpppkR7xybCZesbzEaeTXdNjbCg",
    "y": "45GitmCjPgSyGFI0ESOJHHTit5WQZGwEKv0TvM4TV48",
    "crv": "P-256",
    "d": "sD_7b_rtg-FrCB7slY7ptvPv7TwcvjP4XAaM9QbEVRc"
  },
};
const EUDI_REFERENCE_WALLET_X5C =
  ["MIIC/TCCAqKgAwIBAgIUf758xBTTuRahUhgYewpVPUGRIpMwCgYIKoZIzj0EAwIwVzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxsZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA3MTMxNTM0MzZaFw0yODA3MTIxNTM0MzVaMGUxJjAkBgNVBAMMHXdhbHQuaWQgSWRlbnRpdHkgVmVyaWZpY2F0aW9uMQswCQYDVQQGEwJBVDEVMBMGA1UECgwMd2FsdC5pZCBHbWJIMRcwFQYDVQRhDA5WQVRBVFU2ODE3NDYyNDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABKU6qtmfIgFc5/KcFkaaaZEe8cmwmXrG8xGnk13TY2wo45GitmCjPgSyGFI0ESOJHHTit5WQZGwEKv0TvM4TV4+jggE8MIIBODAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFEJQUL4QuBDwnURcjb+rEAjuJ9xJMFkGCCsGAQUFBwEBBE0wSzBJBggrBgEFBQcwAoY9aHR0cHM6Ly9wcmVwcm9kLnBraS5ldWRpdy5kZXYvYWlhL1BJRElzc3VlckNBMDItRVUuY2FjZXJ0LnBlbTAiBgNVHREEGzAZhhdodHRwczovL3dhbHQuaWQvY29udGFjdDAUBgNVHSAEDTALMAkGBwQAi+xGAQIwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5ldWRpdy5kZXYvY3JsL3BpZF9DQV9FVV8wMi5jcmwwHQYDVR0OBBYEFC2G7JAsGgICW28fEnTAVDwV3xRXMA4GA1UdDwEB/wQEAwIHgDAKBggqhkjOPQQDAgNJADBGAiEAhEn5wgGXoL8RhnoYamf9/kXYqSHxJR8+rPP9AQW4WIACIQCuezmCV++XiNd+HX+OGj0Aes+PgW1z9bHBjAKyXASPUQ=="];

const PID_CREDENTIAL_QUERY = {
  credentials: [
    {
      id: "pid",
      format: "mso_mdoc",
      meta: {
        doctype_value: "eu.europa.ec.eudi.pid.1",
      },
      claims: [
        {
          path: ["eu.europa.ec.eudi.pid.1", "family_name"],
        },
        {
          path: ["eu.europa.ec.eudi.pid.1", "given_name"],
        },
        {
          path: ["eu.europa.ec.eudi.pid.1", "birth_date"],
        },
      ],
    },
  ],
};

function createPidRequestBody(
  material: SimplePidRequestMaterial = {},
): Record<string, unknown> {
  const coreFlow: Record<string, unknown> = {
    dcql_query: PID_CREDENTIAL_QUERY,
  };

  if (material.signedRequest !== undefined) {
    coreFlow.signed_request = material.signedRequest;
  }
  if (material.encryptedResponse !== undefined) {
    coreFlow.encrypted_response = material.encryptedResponse;
  }
  if (material.clientId) {
    coreFlow.clientId = material.clientId;
  }
  if (material.key) {
    coreFlow.key = material.key;
  }
  if (material.x5c) {
    coreFlow.x5c = material.x5c;
  }
  if (material.verifierInfo) {
    coreFlow.verifier_info = material.verifierInfo;
  }

  return {
    flow_type: "cross_device",
    core_flow: coreFlow,
  };
}

export const SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS: SimplePidVerificationRequestOption[] =
  [
    {
      id: "basic-pid",
      label: "Basic PID",
      description:
        "Unsigned PID request without wallet-specific client identity, keys, certificates, or verifier metadata.",
      requestBody: createPidRequestBody(),
    },
    {
      id: "german-eudi-wallet",
      label: "German EUDI Wallet 🇩🇪",
      description:
        "Signed x509_hash request with German EUDI verifier metadata.",
      requestBody: createPidRequestBody({
        signedRequest: true,
        clientId: "x509_hash:YLg9XAMTIMecqGH-2CtrxXB3L3gfBu9V5I2vrqy-zDk",
        key: GERMAN_EUDI_WALLET_KEY,
        x5c: GERMAN_EUDI_WALLET_X5C,
        verifierInfo: GERMAN_VERIFIER_INFO
      }),
    },
    {
      id: "france-identite",
      label: "France Identité 🇫🇷",
      description:
        "Signed and encrypted x509_san_dns PID request for France Identité.",
      requestBody: createPidRequestBody({
        signedRequest: true,
        encryptedResponse: true,
        clientId: "x509_san_dns:verifier2.demo.walt.id",
        key: FRANCE_IDENTITE_KEY,
        x5c: FRANCE_IDENTITE_X5C,
      }),
    },
    {
      id: "eudi-reference-wallet",
      label: "EUDI Reference Wallet 🇪🇺",
      description:
        "Signed and encrypted x509_san_dns PID request for the EUDI Reference Wallet.",
      requestBody: createPidRequestBody({
        signedRequest: true,
        encryptedResponse: true,
        clientId: "x509_hash:",
        key: EUDI_REFERENCE_WALLET_KEY,
        x5c: EUDI_REFERENCE_WALLET_X5C,
      }),
    },
  ];

export function getSimplePidVerificationRequestOption(
  id: string,
): SimplePidVerificationRequestOption {
  return (
    SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS.find(
      (option) => option.id === id,
    ) ?? SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS[0]!
  );
}
