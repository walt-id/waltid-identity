import id.walt.mdoc.objects.serverretrieval.ServerResponse
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.mdoc.objects.serverretrieval.ServerRequest
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.credsdata.MobileDrivingLicenceJws
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JsonSerializationTest {

    @Test
    // from ISO/IEC 18013-5:2021(E), D4.2.1.1, page 120
    fun serverRequest() {
        val input = """
            {
              "version": "1.0",
              "token": "0w4P4mDP_yxnB4iL4KsYwQ",
              "docRequests": [{
                "docType": "org.iso.18013.5.1.mDL",
                "nameSpaces": {
                  "org.iso.18013.5.1": {
                    "family_name": true,
                    "document_number": true,
                    "driving_privileges": true,
                    "issue_date": true,
                    "expiry_date": true,
                    "portrait": false
                  }
                }
              }]
            }
        """.trimIndent()

        val serverRequest = Json.decodeFromString<ServerRequest>(input)

        assertEquals(serverRequest.version, "1.0")
        val docRequest = serverRequest.docRequests[0]
        assertEquals(docRequest.docType, "org.iso.18013.5.1.mDL")
        val itemRequests = docRequest.namespaces["org.iso.18013.5.1"]
        assertNotNull(itemRequests)
        assertEquals(itemRequests["family_name"], true)
        assertEquals(itemRequests["document_number"], true)
        assertEquals(itemRequests["driving_privileges"], true)
        assertEquals(itemRequests["issue_date"], true)
        assertEquals(itemRequests["expiry_date"], true)
        assertEquals(itemRequests["portrait"], false)
    }

    @Test
    fun mDLAsJSON() {
        val mdl = Mdl(
            familyName = "Mustermann",
            givenName = "Max",
            birthDate = LocalDate.parse("1970-01-01"),
            issueDate = LocalDate.parse("2018-08-09"),
            expiryDate = LocalDate.parse("2024-10-20"),
            issuingCountry = "AT",
            issuingAuthority = "LPD Steiermark",
            documentNumber = "A/3f984/019",
            portrait = Random.nextBytes(16),
            drivingPrivileges = listOf(
                DrivingPrivilege(
                    vehicleCategoryCode = "A",
                    issueDate = LocalDate.parse("2018-08-09"),
                    expiryDate = LocalDate.parse("2024-10-20")
                )
            ),
            unDistinguishingSign = "AT"
        )

        val serialized = Json.encodeToString(mdl)

        assertContains(serialized, "LPD Steiermark")
    }

    @Test
    // from ISO/IEC 18013-5:2021(E), D4.2.1.2, page 121
    fun serverResponse() {
        /**
         * Payload in JWS is:
         * {
         *   "doctype": "org.iso.18013.5.1.mDL",
         *   "namespaces": {
         *     "org.iso.18013.5.1": {
         *       "family_name": "Doe",
         *       "given_name": "Jane",
         *       "issue_date": "2019-10-20",
         *       "expiry_date": "2024-10-20",
         *       "document_number": "123456789",
         *       "portrait": "_9j_4AAQSkZJRgABAQEAkACQAAD_2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b_2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb_wAARCAAYAGQDASIAAhEBAxEB_8QAGwAAAwEAAwEAAAAAAAAAAAAAAAUGBAECAwf_xAAyEAABAwMDAgUCAwkAAAAAAAABAgMEAAURBhIhEzEUFVFhcSJBB4GhFjVCUnORssHx_8QAFQEBAQAAAAAAAAAAAAAAAAAAAAH_xAAaEQEBAQADAQAAAAAAAAAAAAAAAUERITFh_9oADAMBAAIRAxEAPwClu94i2iMpx9aSvH0NA_Us-w_3Xnp-8-dwlyOh0NrhRt37s8A5zgetK9R6fjLbuN0dUtbvSyhPZKSABn37Ufh_-5X_AOuf8U0hXeZq8InORLfb3py2iQooOO3fGAePet1i1BHvTbmxCmXWuVoUc4HqDUlbkzJ1_mu6dcEUEEqLpBBBPpg9_wBPWvXTS0tM3mMtC_H9FZK92RxkEfOTTC-mr2tUl10Qbc9KZa5W6FYAHrwDx84p3Z7vHvEPxEfcnadq0q7pNTehun5PcN2O_wBXxt_7XhoZhUqDdY5UUodQlG7GcEhQzQN7zrCLbX0sx20zF_x7XMBPtnByacXG4MW2CuVJJCEjsOST9gKgdVWeNZlw2Y24lSVFa1HJUcivoT6o6Y48WWg2eD1cY_WmGpn9tykIddtL6IqzhLu7v8cYP96qYz6JUdt9o5bcSFJPsai9YRpaoqJDLzCrQgp6bTJAxxjPAx-p70ya1VAgWqApUd9KHWyEIbAVt2nbjJIpg36ivosbDTnQ66nFFITv24wO_Y0lja88RJaZ8u29RYTnr5xk4_lrm-so1KxAkx5keMjnaiSoJUSVAdhn0rHc3rrpm5x1KuTs1t3koXnBweRgk4-RSe9lXlFcA5GaKJyz3KJ4-3vxd_T6qCndjOPyrJp-zeSQlx-v19zhXu2bccAYxk-lFFFLJOjk-MXJt1wegledwSM9_sCCOPat1i05GswcUlannnBtUtQxx6AUUUC5_RSes6YNxeiMu8LaCSQR6dxx85p3ZrRHs0ToR9ysnctau6jRRQYdQ6b88eZc8V0OkCMdPdnP5imVxtzFyhKiyQShX3HdJ9RRRT4J0aIUUJYcuz6oqVZDO3gfHOM9_tVPDitQorcdhO1tsYAoooF190_GvaEFxSmnkcJcTzx6EfcVhiaPSma3JuM96epvG1Kxgcdgck5HtRRSClooooP_2Q",
         *       "driving_privileges": [
         *         {
         *           "vehicle_category_code": "A",
         *           "issue_date": "2018-08-09",
         *           "expiry_date": "2024-10-20"
         *         },
         *         {
         *           "vehicle_category_code": "B",
         *           "issue_date": "2017-02-23",
         *           "expiry_date": "2024-10-20"
         *         }
         *       ]
         *     }
         *   },
         *   "iat": 1609855200,
         *   "exp": 1609855320
         * }
         */
        val input = """
            {
              "version": "1.0",
              "documents":
              ["eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsIng1YyI6WyJNSUlCN3pDQ0FaYWdBd0lCQWdJVUZhcDZiTHFR
              T0h0NGROZXNvUy9EYm9IU1BjMHdDZ1lJS29aSXpqMEVBd0l3SXpFVU1CSUdBMVVFQXd3TGRYUnZjR2xoSUdsaFkyRX
              hDekFKQmdOVkJBWVRBbFZUTUI0WERUSXdNVEF3TVRBd01EQXdNRm9YRFRJeE1UQXdNVEF3TURBd01Gb3dJakVUTUJ
              FR0ExVUVBd3dLZFhSdmNHbGhJR3AzY3pFTE1Ba0dBMVVFQmhNQ1ZWTXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9Q
              UU1CQndOQ0FBUzRLYTVzUUtCOHQ2b0JJMjM4bXRkOFdRaTdoRWhsNE1YQ21jU0V5a3hUbzdjNUVndHRHQnkxRm5yS
              1BXQlo4MXFJcXpubzNQdDNyRVhpSUw3cHhHUERvNEdvTUlHbE1CNEdBMVVkRWdRWE1CV0JFMlY0WVcxd2JHVkFaWGh
              oYlhCc1pTNWpiMjB3SEFZRFZSMGZCQlV3RXpBUm9BK2dEWUlMWlhoaGJYQnNaUzVqYjIwd0hRWURWUjBPQkJZRUZPR
              3RtWFAxUmZxdjhmeW9BcFBUVyswa2ttc3BNQjhHQTFVZEl3UVlNQmFBRkZUNkk0T2dUQ2pnMlRCNUltSElERWlCMH
              NBTE1BNEdBMVVkRHdFQi93UUVBd0lIZ0RBVkJnTlZIU1VCQWY4RUN6QUpCZ2NvZ1l4ZEJRRURNQW9HQ0NxR1NNNDlC
              QU1DQTBjQU1FUUNJQ1Q5OU5zREsxeGhlWFcyTTNVcmVzNzhOYlVuNGFyRjh6K1RDZ0VvWlF3VkFpQjRiL1Uxazg4V0
              hEK01sZmxiM0NkSHpQd1RoNmZGYVAycGFMVnZRZHJsZHc9PSJdfQ.eyJkb2N0eXBlIjoib3JnLmlzby4xODAxMy41L
              jEubURMIiwibmFtZXNwYWNlcyI6eyJvcmcuaXNvLjE4MDEzLjUuMSI6eyJmYW1pbHlfbmFtZSI6IkRvZSIsImdpdmV
              uX25hbWUiOiJKYW5lIiwiaXNzdWVfZGF0ZSI6IjIwMTktMTAtMjAiLCJleHBpcnlfZGF0ZSI6IjIwMjQtMTAtMjAiL
              CJkb2N1bWVudF9udW1iZXIiOiIxMjM0NTY3ODkiLCJwb3J0cmFpdCI6Il85al80QUFRU2taSlJnQUJBUUVBa0FDUUF
              BRF8yd0JEQUJNTkRoRU9EQk1SRHhFVkZCTVhIVEFmSFJvYUhUb3FMQ013UlQxSlIwUTlRMEZNVm0xZFRGRm9Va0ZEW
              DRKZ2FIRjFlM3g3U2x5R2tJVjNqMjE0ZTNiXzJ3QkRBUlFWRlIwWkhUZ2ZIemgyVDBOUGRuWjJkbloyZG5aMmRuWjJ
              kbloyZG5aMmRuWjJkbloyZG5aMmRuWjJkbloyZG5aMmRuWjJkbloyZG5aMmRuWjJkbmJfd0FBUkNBQVlBR1FEQVNJQ
              UFoRUJBeEVCXzhRQUd3QUFBd0VBQXdFQUFBQUFBQUFBQUFBQUFBVUdCQUVDQXdmX3hBQXlFQUFCQXdNREFnVUNBd2t
              BQUFBQUFBQUJBZ01FQUFVUkJoSWhFekVVRlZGaGNTSkJCNEdoRmpWQ1VuT1Jzc0h4XzhRQUZRRUJBUUFBQUFBQUFBQ
              UFBQUFBQUFBQUFBSF94QUFhRVFFQkFRQURBUUFBQUFBQUFBQUFBQUFBQVVFUklURmhfOW9BREFNQkFBSVJBeEVBUHd
              DbHU5NGkyaU1weDlhU3ZIME5BX1VzLXdfM1hucC04LWR3bHlPaDBOcmhSdDM3czhBNXpnZXRLOVI2ZmpMYnVOMGRVd
              GJ2U3loUFpLU0FCbjM3VWZoXy01WF9BT3VmOFUwaFhlWnE4SW5PUkxmYjNweTJpUW9vT08zZkdBZVBldDFpMUJIdlR
              ibXhDbVhXdVZvVWM0SHFEVWxia3pKMV9tdTZkY0VVRUVxTHBCQkJQcGc5X3dCUFd2WFRTMHRNM21NdENfSDlGWks5M
              lJ4a0VmT1RUQy1tcjJ0VWwxMFFiYzlLWmE1VzZGWUFIcndEeDg0cDNaN3ZIdkVQeEVmY25hZHEwcTdwTlRlaHVuNVB
              jTjJPX3dCWHh0XzdYaG9aaFVxRGRZNVVVb2RRbEc3R2NFaFF6UU43enJDTGJYMHN4MjB6Rl94N1hNQlB0bkJ5YWNYR
              zRNVzJDdVZKSkNFanNPU1Q5Z0tnZFZXZU5abHcyWTI0bFNWRmExSEpVY2l2b1Q2bzZZNDhXV2cyZUQxY1lfV21HcG4
              5dHlrSWRkdEw2SXF6aEx1N3Y4Y1lQOTZxWXo2SlVkdDlvNWJjU0ZKUHNhaTlZUnBhb3FKREx6Q3JRZ3A2YlRKQXh4a
              lBBeC1wNzB5YTFWQWdXcUFwVWQ5S0hXeUVJYkFWdDJuYmpKSXBnMzZpdm9zYkRUblE2Nm5GRklUdjI0d09fWTBsamE
              4OFJKYVo4dTI5UllUbnI1eGs0X2xybS1zbzFLeEFreDVrZU1qbmFpU29KVVNWQWRobjBySGMzcnJwbTV4MUt1VHMxd
              DNrb1huQndlUmdrNC1SU2U5bFhsRmNBNUdhS0p5ejNLSjQtM3Z4ZF9UNnFDbmRqT1B5ckpwLXplU1FseC12MTl6aFh
              1MmJjY0FZeGstbEZGRkxKT2prLU1YSnQxd2VnbGVkd1NNOV9zQ0NPUGF0MWkwNUdzd2NVbGFubm5CdFV0UXh4NkFVV
              VVDNV9SU2VzNllOeGVpTXU4TGFDU1FSNmR4eDg1cDNaclJIczBUb1I5eXNuY3RhdTZqUlJRWWRRNmI4OGVaYzhWME9
              rQ01kUGRuUDVpbVZ4dHpGeWhLaXlRU2hYM0hkSjlSUlJUNEowYUlVVUpZY3V6Nm9xVlpETzNnZkhPTTlfdFZQRGl0U
              W9yY2RoTzF0c1lBb29vRjE5MF9HdmFFRnhTbW5rY0pjVHp4NkVmY1ZoaWFQU21hM0p1TTk2ZXB2RzFLeGdjZGdjazV
              IdFJSU0Nsb29vb1BfMlEiLCJkcml2aW5nX3ByaXZpbGVnZXMiOlt7InZlaGljbGVfY2F0ZWdvcnlfY29kZSI6IkEiL
              CJpc3N1ZV9kYXRlIjoiMjAxOC0wOC0wOSIsImV4cGlyeV9kYXRlIjoiMjAyNC0xMC0yMCJ9LHsidmVoaWNsZV9jYXR
              lZ29yeV9jb2RlIjoiQiIsImlzc3VlX2RhdGUiOiIyMDE3LTAyLTIzIiwiZXhwaXJ5X2RhdGUiOiIyMDI0LTEwLTIwI
              n1dfX0sImlhdCI6MTYwOTg1NTIwMCwiZXhwIjoxNjA5ODU1MzIwfQ.JRjQgYpNthh52j3xQ1f6tkoKRBsF8YwH6NlK
              Yg2n_pyayOoQyrRPO0aPBeVJ5lgKBzLumjamuvr3C824R_RYHQ"
              ]
            }
        """.trimIndent()

        val serverResponse = Json.decodeFromString<ServerResponse>(input)

        val payload = serverResponse.documents.first()

        println("Server response document 0: $payload")

        val jwsPayload = payload.decodeJws().payload

        val mdlJws = Json.decodeFromString<MobileDrivingLicenceJws>(jwsPayload.toString())

        assertEquals(mdlJws.doctype, "org.iso.18013.5.1.mDL")
        val mdl = mdlJws.namespaces.mdl
        assertEquals(mdl.familyName, "Doe")
        assertEquals(mdl.givenName, "Jane")
        assertEquals(mdl.issueDate, LocalDate.parse("2019-10-20"))
        assertEquals(mdl.expiryDate, LocalDate.parse("2024-10-20"))
        assertEquals(mdl.documentNumber, "123456789")
        assertEquals(mdl.drivingPrivileges.size, 2)
        assertContains(
            mdl.drivingPrivileges, DrivingPrivilege(
                vehicleCategoryCode = "A",
                issueDate = LocalDate.parse("2018-08-09"),
                expiryDate = LocalDate.parse("2024-10-20")
            )
        )
        assertContains(
            mdl.drivingPrivileges, DrivingPrivilege(
                vehicleCategoryCode = "B",
                issueDate = LocalDate.parse("2017-02-23"),
                expiryDate = LocalDate.parse("2024-10-20")
            )
        )
    }
}
