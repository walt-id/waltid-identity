package id.walt.holderpolicies

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.holderpolicies.HolderPolicy.HolderPolicyAction
import id.walt.holderpolicies.checks.ApplyAllHolderPolicyCheck
import id.walt.holderpolicies.checks.BasicHolderPolicyCheck
import id.walt.holderpolicies.checks.DcqlHolderPolicyCheck
import id.walt.holderpolicies.checks.JsonSchemaHolderPolicyCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class HolderPolicyEngineTest {

    val waltidIssuedJoseSignedW3CCredential = suspend {
        CredentialParser.detectAndParse(
            """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3B1cmwuaW1zZ2xvYmFsLm9yZy9zcGVjL29iL3YzcDAvY29udGV4dC5qc29uIl0sImlkIjoidXJuOnV1aWQ6ZjdmNDMzMTItMWRmMi00YzNiLWI5NWUtNzIxNTBiMzlhZjQyIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk9wZW5CYWRnZUNyZWRlbnRpYWwiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sIm5hbWUiOiJKb2JzIGZvciB0aGUgRnV0dXJlIChKRkYpIiwidXJsIjoiaHR0cHM6Ly93d3cuamZmLm9yZy8iLCJpbWFnZSI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMS0yMDIyL2ltYWdlcy9KRkZfTG9nb0xvY2t1cC5wbmciLCJpZCI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIn0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7InR5cGUiOlsiQWNoaWV2ZW1lbnRTdWJqZWN0Il0sImFjaGlldmVtZW50Ijp7ImlkIjoidXJuOnV1aWQ6YWMyNTRiZDUtOGZhZC00YmIxLTlkMjktZWZkOTM4NTM2OTI2IiwidHlwZSI6WyJBY2hpZXZlbWVudCJdLCJuYW1lIjoiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSIsImRlc2NyaXB0aW9uIjoiVGhpcyB3YWxsZXQgc3VwcG9ydHMgdGhlIHVzZSBvZiBXM0MgVmVyaWZpYWJsZSBDcmVkZW50aWFscyBhbmQgaGFzIGRlbW9uc3RyYXRlZCBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cgZHVyaW5nIEpGRiB4IFZDLUVEVSBQbHVnRmVzdCAzLiIsImNyaXRlcmlhIjp7InR5cGUiOiJDcml0ZXJpYSIsIm5hcnJhdGl2ZSI6IldhbGxldCBzb2x1dGlvbnMgcHJvdmlkZXJzIGVhcm5lZCB0aGlzIGJhZGdlIGJ5IGRlbW9uc3RyYXRpbmcgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93LiBUaGlzIGluY2x1ZGVzIHN1Y2Nlc3NmdWxseSByZWNlaXZpbmcgYSBwcmVzZW50YXRpb24gcmVxdWVzdCwgYWxsb3dpbmcgdGhlIGhvbGRlciB0byBzZWxlY3QgYXQgbGVhc3QgdHdvIHR5cGVzIG9mIHZlcmlmaWFibGUgY3JlZGVudGlhbHMgdG8gY3JlYXRlIGEgdmVyaWZpYWJsZSBwcmVzZW50YXRpb24sIHJldHVybmluZyB0aGUgcHJlc2VudGF0aW9uIHRvIHRoZSByZXF1ZXN0b3IsIGFuZCBwYXNzaW5nIHZlcmlmaWNhdGlvbiBvZiB0aGUgcHJlc2VudGF0aW9uIGFuZCB0aGUgaW5jbHVkZWQgY3JlZGVudGlhbHMuIn0sImltYWdlIjp7ImlkIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0zLTIwMjMvaW1hZ2VzL0pGRi1WQy1FRFUtUExVR0ZFU1QzLWJhZGdlLWltYWdlLnBuZyIsInR5cGUiOiJJbWFnZSJ9fSwiaWQiOiJkaWQ6a2V5Ono2TWtrdlhTVFlhMWZ0aVNhOVpZdmlhZjNURVBIVnlWUDFWaEI3TXNqdTlFcjdMRyJ9LCJpc3N1YW5jZURhdGUiOiIyMDI1LTAzLTE5VDEzOjU0OjA1LjkwMTk2ODAzN1oiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDMtMTlUMTM6NTQ6MDUuOTAyMDIwMDM4WiJ9LCJqdGkiOiJ1cm46dXVpZDpmN2Y0MzMxMi0xZGYyLTRjM2ItYjk1ZS03MjE1MGIzOWFmNDIiLCJleHAiOjE3NzM5Mjg0NDUsImlhdCI6MTc0MjM5MjQ0NSwibmJmIjoxNzQyMzkyNDQ1fQ.eppq-IFQ5LW7UZ514jvn9rgrHQ23iDTsCGDq3EH6FbYf-RkN763eAEmuYIzXykgc5bRqdHOrmV_IJ4VUJedkBg
    """.trimIndent()
        ).second
    }

    val ossIssuedW3CSdJwt = suspend {
        CredentialParser.detectAndParse(
            """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3B1cmwuaW1zZ2xvYmFsLm9yZy9zcGVjL29iL3YzcDAvY29udGV4dC5qc29uIl0sImlkIjoidXJuOnV1aWQ6YmRmMjE2ZGItOTJmOC00MTYwLThiYTYtNmRlNDliODRhNGQ5IiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk9wZW5CYWRnZUNyZWRlbnRpYWwiXSwiaXNzdWVyIjp7InR5cGUiOlsiUHJvZmlsZSJdLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIiwiaWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCJ9LCJjcmVkZW50aWFsU3ViamVjdCI6eyJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX0sImlkIjoiZGlkOmtleTp6Nk1ra3ZYU1RZYTFmdGlTYTlaWXZpYWYzVEVQSFZ5VlAxVmhCN01zanU5RXI3TEcifSwiaXNzdWFuY2VEYXRlIjoiMjAyNS0wNC0wOFQxOTo1MzozOS4zMjUzODIyMTdaIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA0LTA4VDE5OjUzOjM5LjMyNTQwMzkxN1oiLCJfc2QiOlsibVZkTkExaS1ZSjdQT0FWMmFNTjV2Xy1BU3pHOVhfSC12WDZ2NEpySWR3YyJdfSwianRpIjoidXJuOnV1aWQ6YmRmMjE2ZGItOTJmOC00MTYwLThiYTYtNmRlNDliODRhNGQ5IiwiZXhwIjoxNzc1Njc4MDE5LCJpYXQiOjE3NDQxNDIwMTksIm5iZiI6MTc0NDE0MjAxOX0.OZ3EywnJ_KDTPH5-S0OC3jN67pIdrJAwTugHh53jrI471TAbCCJ48-YVgX5l8ZLWLeKQMdFyvNpNQLqsbu9ECA~WyIzX0hndTl4UDh2T25qaW03UE4yc3FRPT0iLCJuYW1lIiwiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSJd
    """.trimIndent()
        ).second
    }

    val ossIssuedW3CSdJwtWithoutName = suspend {
        CredentialParser.detectAndParse(
            """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3B1cmwuaW1zZ2xvYmFsLm9yZy9zcGVjL29iL3YzcDAvY29udGV4dC5qc29uIl0sImlkIjoidXJuOnV1aWQ6YmRmMjE2ZGItOTJmOC00MTYwLThiYTYtNmRlNDliODRhNGQ5IiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk9wZW5CYWRnZUNyZWRlbnRpYWwiXSwiaXNzdWVyIjp7InR5cGUiOlsiUHJvZmlsZSJdLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIiwiaWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCJ9LCJjcmVkZW50aWFsU3ViamVjdCI6eyJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX0sImlkIjoiZGlkOmtleTp6Nk1ra3ZYU1RZYTFmdGlTYTlaWXZpYWYzVEVQSFZ5VlAxVmhCN01zanU5RXI3TEcifSwiaXNzdWFuY2VEYXRlIjoiMjAyNS0wNC0wOFQxOTo1MzozOS4zMjUzODIyMTdaIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA0LTA4VDE5OjUzOjM5LjMyNTQwMzkxN1oiLCJfc2QiOlsibVZkTkExaS1ZSjdQT0FWMmFNTjV2Xy1BU3pHOVhfSC12WDZ2NEpySWR3YyJdfSwianRpIjoidXJuOnV1aWQ6YmRmMjE2ZGItOTJmOC00MTYwLThiYTYtNmRlNDliODRhNGQ5IiwiZXhwIjoxNzc1Njc4MDE5LCJpYXQiOjE3NDQxNDIwMTksIm5iZiI6MTc0NDE0MjAxOX0.OZ3EywnJ_KDTPH5-S0OC3jN67pIdrJAwTugHh53jrI471TAbCCJ48-YVgX5l8ZLWLeKQMdFyvNpNQLqsbu9ECA
    """.trimIndent()
        ).second
    }

    val ossIssuedW3CSdJwt2 = suspend {
        CredentialParser.detectAndParse(
            """
        eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2t2WFNUWWExZnRpU2E5Wll2aWFmM1RFUEhWeVZQMVZoQjdNc2p1OUVyN0xHIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnL25zL2NyZWRlbnRpYWxzL3YyIiwiaHR0cHM6Ly9wdXJsLmltc2dsb2JhbC5vcmcvc3BlYy9vYi92M3AwL2NvbnRleHQuanNvbiJdLCJpZCI6InVybjp1dWlkOjI1MjkyOWRjLThmMTUtNGMyOS05MjQ5LTUxM2QxMGNjNjc4YiIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJPcGVuQmFkZ2VDcmVkZW50aWFsIl0sImlzc3VlciI6eyJ0eXBlIjpbIlByb2ZpbGUiXSwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyIsImlkIjoiZGlkOmtleTp6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AifSwiY3JlZGVudGlhbFN1YmplY3QiOnsidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn0sIl9zZCI6WyJVTlprdVp6WTZrdW96RHFiQjlTZ08yZFE1aElKMDJ1R3ZfVTQyMDlGRF9jIiwiRUFqdUdjQjMwbnpPSVhVOHN0RE5KS0ltbjhXVjZCNFhsRkdyNlVDOTVtcyJdfSwiaWQiOiJkaWQ6a2V5Ono2TWtrdlhTVFlhMWZ0aVNhOVpZdmlhZjNURVBIVnlWUDFWaEI3TXNqdTlFcjdMRyJ9LCJpc3N1YW5jZURhdGUiOiIyMDI1LTA0LTA4VDIzOjI5OjUxLjQ1NjQwMDYwMVoiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjYtMDQtMDhUMjM6Mjk6NTEuNDU2NDIxNjAxWiIsIl9zZCI6WyItYnBYUUhXS0lseGtiaF95Y2VVRzMwYktwS3lyZjE0OUdoY0hGei1EUXpFIl19LCJqdGkiOiJ1cm46dXVpZDoyNTI5MjlkYy04ZjE1LTRjMjktOTI0OS01MTNkMTBjYzY3OGIiLCJleHAiOjE3NzU2OTA5OTEsImlhdCI6MTc0NDE1NDk5MSwibmJmIjoxNzQ0MTU0OTkxfQ.mI0HjLiauyZ0nN_KZMyWLuJqGuWXLyrNw9EWg_4mvLt1jJ_4Y8MFb7L1Zw04ColQ2_7pNRh45xPgacVNKOnSDg~WyJ0QlFGazR2WlF4d05zNmN1SzFqRUtRPT0iLCJuYW1lIiwiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSJd~WyJqRDE3ZFRCOWFwSWRpblQ4b0hpVmNRPT0iLCJkZXNjcmlwdGlvbiIsIlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iXQ~WyJya01mMkthSkdDWkJLdERDRkpSeTNBPT0iLCJjcml0ZXJpYSIseyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9XQ
    """.trimIndent()
        ).second
    }

    suspend fun basicTest(policy: HolderPolicy, expected: HolderPolicyAction) = runTest {
        val credentialsWithSameIssuer = flowOf(waltidIssuedJoseSignedW3CCredential(), ossIssuedW3CSdJwt(), ossIssuedW3CSdJwt2())
        val policies = flowOf(policy)
        println("\nEvaluating policy \"${policy.description ?: "unnamed"}\": ${policy.serialized()}")

        val result = HolderPolicyEngine.evaluate(policies, credentialsWithSameIssuer)
        println("Result: $result (expected: $expected)")
        check(result == expected)
    }

    @Test
    fun testHolderPolicyBasicBlock() = runTest {
        basicTest(
            HolderPolicy(
                priority = 1,
                description = "Block all from issuer did:key:...Nywp",
                check = BasicHolderPolicyCheck(
                    issuer = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
                ),
                action = HolderPolicyAction.BLOCK
            ),

            expected = HolderPolicyAction.BLOCK
        )
    }

    @Test
    fun testHolderPolicyBasicAllow() = runTest {
        basicTest(
            HolderPolicy(
                priority = 1,
                description = "Allow all from issuer did:key:...Nywp",
                check = BasicHolderPolicyCheck(
                    issuer = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
                ),
                action = HolderPolicyAction.ALLOW
            ),

            expected = HolderPolicyAction.ALLOW
        )
    }


    private suspend fun multiPolicyTest(policies: Flow<HolderPolicy>, expected: HolderPolicyAction?) {
        val credentialsWithSameIssuer = flowOf(waltidIssuedJoseSignedW3CCredential(), ossIssuedW3CSdJwt(), ossIssuedW3CSdJwt2())

        val result = HolderPolicyEngine.evaluate(policies, credentialsWithSameIssuer)
        println("Result: $result (expected: $expected)")
        check(result == expected)
    }

    @Test
    fun testPriority() = runTest {
        val listA = flowOf(
            HolderPolicy(
                priority = 1, // Executed first: ALLOW
                description = "Allow all for Issuer did:key:...Nywp",
                check = BasicHolderPolicyCheck(
                    issuer = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
                ),
                action = HolderPolicyAction.ALLOW
            ),
            HolderPolicy(
                priority = 99, // Executed last: BLOCK
                description = "Block all for Subject did:key:...Er7LG",
                check = BasicHolderPolicyCheck(
                    subject = "did:key:z6MkkvXSTYa1ftiSa9ZYviaf3TEPHVyVP1VhB7Msju9Er7LG"
                ),
                action = HolderPolicyAction.BLOCK
            )
        )
        multiPolicyTest(listA, HolderPolicyAction.ALLOW)

        val listB = flowOf(
            HolderPolicy(
                priority = 99, // Executed last: ALLOW
                description = "Allow all for Issuer did:key:...Nywp",
                check = BasicHolderPolicyCheck(
                    issuer = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
                ),
                action = HolderPolicyAction.ALLOW
            ),
            HolderPolicy(
                priority = 1, // Executed first: BLOCK
                description = "Block all for Subject did:key:...Er7LG",
                check = BasicHolderPolicyCheck(
                    subject = "did:key:z6MkkvXSTYa1ftiSa9ZYviaf3TEPHVyVP1VhB7Msju9Er7LG"
                ),
                action = HolderPolicyAction.BLOCK
            )
        )
        multiPolicyTest(listB, HolderPolicyAction.BLOCK)
    }

    @Test
    fun testNoMatching() = runTest {
        multiPolicyTest(
            flowOf(
                HolderPolicy(
                    priority = 1,
                    apply = BasicHolderPolicyCheck(issuer = "did:web:xxxyyy"),
                    action = HolderPolicyAction.ALLOW,
                )
            ),
            expected = null
        )
    }

    @Test
    fun testDefaultDeny() = runTest {
        multiPolicyTest(
            flowOf(
                HolderPolicy(
                    priority = 1,
                    apply = BasicHolderPolicyCheck(issuer = "did:web:xxxyyy"),
                    action = HolderPolicyAction.ALLOW,
                ),
                HolderPolicy(
                    priority = 99,
                    apply = ApplyAllHolderPolicyCheck(),
                    action = HolderPolicyAction.BLOCK,
                )
            ),
            expected = HolderPolicyAction.BLOCK
        )
    }

    @Test
    fun testDefaultAllow() = runTest {
        multiPolicyTest(
            flowOf(
                HolderPolicy(
                    priority = 1,
                    apply = BasicHolderPolicyCheck(issuer = "did:web:blacklisted"),
                    action = HolderPolicyAction.BLOCK,
                ),
                HolderPolicy(
                    priority = 99,
                    apply = ApplyAllHolderPolicyCheck(),
                    action = HolderPolicyAction.ALLOW,
                )
            ),
            expected = HolderPolicyAction.ALLOW
        )
    }

    @Test
    fun testClaimMatching() = runTest {
        multiPolicyTest(
            flowOf(
                HolderPolicy(
                    priority = 1,
                    check = BasicHolderPolicyCheck(claimsPresent = listOf("name")),
                    action = HolderPolicyAction.BLOCK,
                ),
                HolderPolicy(
                    priority = 99,
                    apply = ApplyAllHolderPolicyCheck(),
                    action = HolderPolicyAction.ALLOW,
                )
            ),
            expected = HolderPolicyAction.BLOCK
        )

        multiPolicyTest(
            flowOf(
                HolderPolicy(
                    priority = 1,
                    check = BasicHolderPolicyCheck(claimsPresent = listOf("nameXXX")),
                    action = HolderPolicyAction.BLOCK,
                ),
                HolderPolicy(
                    priority = 99,
                    apply = ApplyAllHolderPolicyCheck(),
                    action = HolderPolicyAction.ALLOW,
                )
            ),
            expected = HolderPolicyAction.ALLOW
        )

        multiPolicyTest(
            flowOf(
                HolderPolicy(
                    priority = 1,
                    check = BasicHolderPolicyCheck(claimsValues = mapOf("name" to JsonPrimitive("JFF x vc-edu PlugFest 3 Interoperability"))),
                    action = HolderPolicyAction.BLOCK,
                ),
                HolderPolicy(
                    priority = 99,
                    apply = ApplyAllHolderPolicyCheck(),
                    action = HolderPolicyAction.ALLOW,
                )
            ),
            expected = HolderPolicyAction.BLOCK
        )
    }

    private suspend fun customCredentialTest(
        policies: Flow<HolderPolicy>,
        credentials: Flow<DigitalCredential>,
        expected: HolderPolicyAction?
    ) {
        val result = HolderPolicyEngine.evaluate(policies, credentials)
        println("Result: $result (expected: $expected)")
        check(result == expected)
    }


    @Test
    fun testJsonSchemaMatching() = runTest {
        val policies = flowOf(
            HolderPolicy(
                priority = 1,
                check = JsonSchemaHolderPolicyCheck(
                    schema = Json.decodeFromString<JsonObject>(
                        // language=JSON
                        """
                                   {
                                      "type": "object",
                                      "properties": {
                                        "vc": {
                                          "type": "object",
                                          "properties": {
                                            "name": {
                                              "type": "string"
                                            }
                                          },
                                          "required": ["name"]
                                        }
                                      },
                                      "required": ["vc"]
                                    }
                                """.trimIndent()
                    )
                ),
                action = HolderPolicyAction.BLOCK,
                description = "Block if sharing name"
            ),

            HolderPolicy(
                priority = 10,
                check = BasicHolderPolicyCheck(
                    claimsPresent = listOf("@context"),
                ),
                action = HolderPolicyAction.ALLOW,
                description = "Allow if sharing credentialSubject"
            ),

            HolderPolicy(
                priority = 99,
                apply = ApplyAllHolderPolicyCheck(),
                action = HolderPolicyAction.BLOCK,
                description = "Default block"
            ),
        )

        customCredentialTest(
            policies,
            credentials = flowOf(ossIssuedW3CSdJwt()), // with name
            expected = HolderPolicyAction.BLOCK
        )

        customCredentialTest(
            policies,
            credentials = flowOf(ossIssuedW3CSdJwtWithoutName()), // without name
            expected = HolderPolicyAction.ALLOW
        )
    }

    val sdJwtVcSignedExample2 = suspend {
        CredentialParser.detectAndParse(
            """
        eyJraWQiOiJKMUZ3SlA4N0M2LVFOX1dTSU9tSkFRYzZuNUNRX2JaZGFGSjVHRG5XMVJrIiwidHlwIjoidmMrc2Qtand0IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJodHRwczovL3RyaWFsLmF1dGhsZXRlLm5ldCIsIl9zZCI6WyIwczNiazZYcC02ZW1rV3cxTFd2OWthaGk5YjNUV0c2NDJLV3dpZEZKeHlvIiwiM3BSUGNUUjItSkJySjhVRjVkZGhtcDhwbDA3MEpheWpoMWEzZVVWRDZNZyIsIjZ6UEtKS2pzc2Y0Q1JNNmhUeDZZVUdQdzRBbm1ZWHZocnFuZDlmdTZMcUkiLCJBVnFKdDdkcWNEVWZLNmJPSEg0UTFEODVfMVNmLXRwM0d0QlM1Mk1Bb3FVIiwiQldydzdVV2YzdjF4cjdOOXFoSFpXMHMwa0FERDVGbFgtUmNQV2dCZEFJOCIsIkhtcHVfdVo4dWo4b2ViMXIyaGg2YmdUY3dNbEJoVHNrUjIxR2tZZVd4TW8iLCJNQ1JpVkRYc3I3MTJ1WW9NRUtWeEJfMmFxX0oweENfa08yNTdwQ3N0RlB3IiwiTUc3WElRV1Y5RFE2dC12WDdmZERUblZ6bnpTZUwwY2gtX0NtNkkyM3ZDWSIsIlB5VEVrajdFdUhScGljdFk5Z1ZpQTVhcTBrYTd2SzJZdDRrX04wbzlTb3ciXSwiaWF0IjoxNzA1MDE4MjA1LCJ2Y3QiOiJodHRwczovL2NyZWRlbnRpYWxzLmV4YW1wbGUuY29tL2lkZW50aXR5X2NyZWRlbnRpYWwiLCJfc2RfYWxnIjoic2hhLTI1NiJ9.ll4JdW-ksNDyVGx-OTueQYojpUYXhUZ6J31fFKGall2SsT5LQt-I5w24AiYhDvxYWRGRCmJF5UI-_3SpNE83wQ~WyJJZEJuZ2xIcF9URGRyeUwycGJLZVNBIiwic3ViIiwiMTAwNCJd~WyJDd0lYNU11clBMZ1VFRnA2U2JhM0dnIiwiZ2l2ZW5fbmFtZSIsIkluZ2EiXQ~WyJveUYtR3Q5LXVwa1FkU0ZMX0pTekNnIiwiZmFtaWx5X25hbWUiLCJTaWx2ZXJzdG9uZSJd~WyJMZG9oSjQ5d2gwcTBubWNJUG92SVhnIiwiYmlydGhkYXRlIiwiMTk5MS0xMS0wNiJd~
    """.trimIndent()
        ).second
    }

    @Test
    fun testDcql() = runTest {
        customCredentialTest(
            policies = flowOf(
                HolderPolicy(
                    priority = 1,
                    description = "Block if sharing address",
                    check = DcqlHolderPolicyCheck(
                        dcqlQuery = Json.decodeFromString<DcqlQuery>(
                            //language=JSON
                            """
                    {
                      "credentials": [
                        {
                          "id": "pid",
                          "format": "dc+sd-jwt",
                          "meta": {
                            "vct_values": ["https://credentials.example.com/identity_credential"]
                          },
                          "claims": [
                            {"path": ["given_name"]},
                            {"path": ["family_name"]},
                            {"path": ["address", "street_address"]}
                          ]
                        },
                        {
                          "id": "mdl",
                          "format": "mso_mdoc",
                          "meta": {
                            "doctype_value": "org.iso.7367.1.mVRC"
                          },
                          "claims": [
                            {"path": ["org.iso.7367.1", "vehicle_holder"]},
                            {"path": ["org.iso.18013.5.1", "first_name"]}
                          ]
                        }
                    ]
                    }
                """.trimIndent()
                        )
                    ),
                    action = HolderPolicyAction.BLOCK
                ),
                HolderPolicy(
                    priority = 5,
                    description = "Allow if sharing birthdate",
                    check = DcqlHolderPolicyCheck(
                        dcqlQuery = Json.decodeFromString<DcqlQuery>(
                            //language=JSON
                            """
                    {
                      "credentials": [
                        {
                          "id": "pid",
                          "format": "dc+sd-jwt",
                          "meta": {
                            "vct_values": ["https://credentials.example.com/identity_credential"]
                          },
                          "claims": [
                            {"path": ["given_name"]},
                            {"path": ["family_name"]},
                            {"path": ["birthdate"]}
                          ]
                        },
                        {
                          "id": "mdl",
                          "format": "mso_mdoc",
                          "meta": {
                            "doctype_value": "org.iso.7367.1.mVRC"
                          },
                          "claims": [
                            {"path": ["org.iso.7367.1", "vehicle_holder"]},
                            {"path": ["org.iso.18013.5.1", "first_name"]}
                          ]
                        }
                    ]
                    }
                """.trimIndent()
                        )
                    ),
                    action = HolderPolicyAction.ALLOW
                )
            ),
            credentials = flowOf(sdJwtVcSignedExample2()),
            expected = HolderPolicyAction.ALLOW
        )
    }

}
