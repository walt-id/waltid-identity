package id.walt.mobile.test.backend

import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Public-demo acceptance vectors, shared by Android journeys and deployed-service checks. */
enum class DemoPayment { MINIMAL, SCHEDULED, RECURRING;
    fun payload(now: Instant = Clock.System.now()): JsonObject {
        val required = Json.parseToJsonElement("""{
            "transaction_id":"${if (this == MINIMAL) "8D8AC610-566D-4EF0-9C22-186B2A5ED793" else "wal-1665-${name.lowercase()}"}",
            "payee":{"name":"Super Store","id":"merchant-001"},"currency":"EUR","amount":11.56
        }""").jsonObject
        if (this == MINIMAL) return required
        val optional = Json.parseToJsonElement("""{
            "payee":{"name":"Super Store","id":"merchant-001","logo":"data:image/png;base64,AA==","website":"https://example.com"},
            "pisp":{"legal_name":"Example Payments Ltd","brand_name":"Example Payments","domain_name":"payments.example"},
            "amount_estimated":false,"amount_earmarked":false,"sct_inst":true
        }""").jsonObject
        val schedule = when (this) {
            SCHEDULED -> buildJsonObject { put("execution_date", (now + 1.days).toString()) }
            RECURRING -> buildJsonObject {
                putJsonObject("recurrence") {
                    put("start_date", (now + 1.days).toString())
                    put("end_date", (now + 365.days).toString())
                    put("number", 12)
                    put("frequency", "MNTH")
                    put("mit_options", Json.parseToJsonElement("""{
                        "amount_variable":false,"min_amount":0,"max_amount":9007199254740993.01,
                        "total_amount":120.00,"initial_amount":-1.23,"initial_amount_number":2.0,
                        "apr":0.1234567890123456789
                    }"""))
                }
            }
            MINIMAL -> error("Minimal payment has no schedule")
        }
        return JsonObject(required + optional + schedule + ("date_time" to JsonPrimitive(now.toString())))
    }
}
