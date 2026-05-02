package id.walt.sdjwt

import kotlin.test.*

class PathTokenTest {

    @Test
    fun tokenize_singleKey() {
        assertEquals(listOf(PathToken.Key("firstName")), PathToken.tokenize("firstName"))
    }

    @Test
    fun tokenize_dottedKeys() {
        assertEquals(
            listOf(PathToken.Key("credentialSubject"), PathToken.Key("firstName")),
            PathToken.tokenize("credentialSubject.firstName")
        )
    }

    @Test
    fun tokenize_specificIndex() {
        assertEquals(
            listOf(PathToken.Key("nationalities"), PathToken.Index(0)),
            PathToken.tokenize("nationalities.[0]")
        )
    }

    @Test
    fun tokenize_wildcard() {
        assertEquals(
            listOf(PathToken.Key("colors"), PathToken.Wildcard),
            PathToken.tokenize("colors.[]")
        )
    }

    @Test
    fun tokenize_wildcardWithProperty() {
        assertEquals(
            listOf(PathToken.Key("cars"), PathToken.Wildcard, PathToken.Key("make")),
            PathToken.tokenize("cars.[].make")
        )
    }

    @Test
    fun tokenize_nestedArrayOfArrays() {
        assertEquals(
            listOf(PathToken.Key("contacts"), PathToken.Index(0), PathToken.Index(2)),
            PathToken.tokenize("contacts.[0].[2]")
        )
    }

    @Test
    fun tokenize_emptySegmentRejected() {
        assertFails { PathToken.tokenize("a..b") }
        assertFails { PathToken.tokenize(".a") }
        assertFails { PathToken.tokenize("a.") }
    }

    @Test
    fun tokenize_negativeIndexRejected() {
        assertFails { PathToken.tokenize("a.[-1]") }
    }

    @Test
    fun tokenize_nonNumericIndexRejected() {
        assertFails { PathToken.tokenize("a.[abc]") }
    }

    @Test
    fun tokenize_unbalancedBracketRejected() {
        assertFails { PathToken.tokenize("a.[]x") }
        assertFails { PathToken.tokenize("a.[0") }
        assertFails { PathToken.tokenize("a.0]") }
    }

    @Test
    fun groupByFirstToken_emptyPathsAreFilteredOut() {
        val grouped = listOf(
            emptyList<PathToken>(),
            listOf(PathToken.Key("a")),
        ).groupByFirstToken()
        assertEquals(setOf(PathToken.Key("a")), grouped.keys)
        assertEquals(listOf(emptyList<PathToken>()), grouped[PathToken.Key("a")])
    }

    @Test
    fun groupByFirstToken_collectsTailsForSharedHead() {
        val grouped = listOf(
            listOf(PathToken.Key("a"), PathToken.Key("b")),
            listOf(PathToken.Key("a"), PathToken.Key("c")),
            listOf(PathToken.Key("d")),
        ).groupByFirstToken()
        assertEquals(setOf(PathToken.Key("a"), PathToken.Key("d")), grouped.keys)
        assertEquals(
            listOf(listOf(PathToken.Key("b")), listOf(PathToken.Key("c"))),
            grouped[PathToken.Key("a")]
        )
        assertEquals(listOf(emptyList<PathToken>()), grouped[PathToken.Key("d")])
    }
}
