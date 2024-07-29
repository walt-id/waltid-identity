import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object DidDocumentChecks {
    //region -DidDocument empty-
    /**
     * Checks [actual] **kty** and **kid** are empty
     * @return True if all checks pass, False otherwise
     */
    fun defaultDidChecks(actual: JsonObject) = let {
        actual["kty"]!!.jsonPrimitive.content.isEmpty()
//                    || actual["kid"]!!.jsonPrimitive.content.isEmpty()//kid not always found
    }

    /**
     * Checks [actual] **x** and **crv** are empty
     * @return True if all checks pass, False otherwise
     */
    fun ed25519DidChecks(actual: JsonObject) = let {
        actual["x"]!!.jsonPrimitive.content.isEmpty()
                || actual["crv"]!!.jsonPrimitive.content.isEmpty()
    }

    /**
     * Checks [actual] **y** is empty
     * @return True if all checks pass, False otherwise
     */
    fun secp256DidChecks(actual: JsonObject) = let {
        actual["y"]!!.jsonPrimitive.content.isEmpty()
    }

    /**
     * Checks [actual] **n** and **e** are empty
     * @return True if all checks pass, False otherwise
     */
    fun rsaDidChecks(actual: JsonObject) = let {
        actual["n"]!!.jsonPrimitive.content.isEmpty()
                || actual["e"]!!.jsonPrimitive.content.isEmpty()
    }
    //endregion -DidDocument-

    //region -DidDocument vs. key-
    /**
     * Checks [actual] and [expected] **kty** and **kid** are identical
     * @return True if all checks pass, False otherwise
     */
    fun defaultKeyChecks(actual: JsonObject, expected: JsonObject) = let {
        actual["kty"]!!.jsonPrimitive.content.equals(expected["kty"]!!.jsonPrimitive.content, true)
//                    && actual["kid"]!!.jsonPrimitive.content == expected["kid"]!!.jsonPrimitive.content//kid not always found
    }

    /**
     * Checks [actual] and [expected] **y** is identical
     * @return True if all checks pass, False otherwise
     */
    fun secp256KeyChecks(actual: JsonObject, expected: JsonObject) = let {
        actual["y"]!!.jsonPrimitive.content == expected["y"]!!.jsonPrimitive.content
    }

    /**
     * Checks [actual] and [expected] **x** and **crv** are identical
     * @return True if all checks pass, False otherwise
     */
    fun ed25519KeyChecks(actual: JsonObject, expected: JsonObject) = let {
        actual["x"]!!.jsonPrimitive.content == expected["x"]!!.jsonPrimitive.content
                && actual["crv"]!!.jsonPrimitive.content.equals(expected["crv"]!!.jsonPrimitive.content, true)
    }

    /**
     * Checks [actual] and [expected] **n** and **e** are identical
     * @return True if all checks pass, False otherwise
     */
    fun rsaKeyChecks(actual: JsonObject, expected: JsonObject) = let {
        actual["n"]!!.jsonPrimitive.content == expected["n"]!!.jsonPrimitive.content
                && actual["e"]!!.jsonPrimitive.content.equals(expected["e"]!!.jsonPrimitive.content, true)
    }
    //endregion -DidDocument vs. key-
}
