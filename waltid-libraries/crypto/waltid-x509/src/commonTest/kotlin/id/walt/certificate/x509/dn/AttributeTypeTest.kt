package id.walt.certificate.x509.dn

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttributeTypeTest {

    @Test
    fun everyNameShouldBeUnique() {
        val groups = AttributeType.knownTypes.flatMap { it.names + it.oid }
            .map { it.lowercase() }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }

        groups.forEach {
            println(it.key)
        }
        assertTrue(groups.isEmpty())
    }


    @Test
    fun mostCommonNameShouldBeKnow() {
        listOf("cn", "ou", "dc", "o", "c", "st", "L", "UID", "serialNumber", "DN", "STREET").forEach {
            assertNotNull(AttributeType.find(it), "Type not found: '$it'")
        }
    }

    @Test
    fun allAttributeTypeNamesAreValid() {
        val shortNameRegex = Regex("^[a-z][a-z0-9\\-]*$", RegexOption.IGNORE_CASE)
        AttributeType.knownTypes.flatMap {
            it.names
        }.forEach {
            assertTrue(shortNameRegex.matches(it), "Illegal short name: '${it}'")
        }
    }

    @Test
    fun allOidsAreValid() {
        val oidRegs = Regex("^\\d+(\\.\\d+)*$")
        AttributeType.knownTypes.map { it.oid }.forEach {
            assertTrue(oidRegs.matches(it), "Illegal OID: '${it}'")
        }
    }
}