@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.db

import app.softwork.uuid.isValidUuidString
import org.jetbrains.exposed.dao.id.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.nio.ByteBuffer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

///**
// * Identity table with a key column having type [Uuid]. Unique identifiers are generated before
// * insertion by [Uuid.Companion.generateUuid] with [SecureRandom] by default.
// *
// * @param name of the table.
// * @param columnName for a primary key column, `"id"` by default.
// * @param random is used to generate unique Uuids.
// */
//@OptIn(ExperimentalUuidApi::class)
//public open class KotlinxUuidTable(
//    name: String = "",
//    columnName: String = "id",
//) : IdTable<CompositeID>(name) {
//    override val id: Column<EntityID<ComparableUuid>> = kotlinxUuid(columnName)
//        .autoGenerate()
//        .entityId()
//
//    override val primaryKey: PrimaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }
//}

/**
 * Creates a binary column, with the specified [name], for storing Uuids.
 * Unlike the [Table.uuid] function, this one registers [kotlinx.uuid.Uuid] type instead of [java.util.Uuid].
 **/
public fun Table.kotlinxUuid(name: String): Column<Uuid> {
    return registerColumn(name, UuidColumnType())
}

/*class TypedUUIDTransformer<T : Uuid>() : ColumnTransformer<Uuid, T> {
    override fun unwrap(value: T): Uuid = Uuid.parse(value.toString())

    override fun wrap(value: Uuid): T = value.toString()
}

fun <T : Uuid> Table.typedUUID(name: String, factory: UUIDTypeFactory<T>): Column<T> =
    registerColumn(name, UUIDColumnType()).transform(TypedUUIDTransformer(factory))*/

/**
 * Configure column to generate Uuid via [Uuid.Companion.generateUuid]
 * with the specified [random] that is backed by [SecureRandom] by default.
 * Remember that using a [SecureRandom] may require to seed the system random source
 * otherwise a system may get stuck.
 **/
public fun Column<Uuid>.autoGenerate(): Column<Uuid> = apply {
    defaultValueFun = { Uuid.random() }
}

/**
 * A [Uuid] column type for registering in exposed tables.
 * @see kotlinxUuid to see how it is used
 */
public class UuidColumnType : ColumnType<Uuid>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.uuidType()

    override fun valueFromDB(value: Any): Uuid = when {
        value is java.util.UUID -> value.toKotlinUuid()
        value is Uuid -> value
        value is ByteArray -> ByteBuffer.wrap(value).let { b -> valueFromDB(java.util.UUID(b.long, b.long)) }
        value is String && Uuid.isValidUuidString(value) -> Uuid.parse(value)
        value is String -> valueFromDB(value.toByteArray())
        else -> error("Unexpected value of type Uuid: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Uuid): Any = currentDialect.dataTypeProvider.uuidToDB(valueToUuid(value))

    override fun nonNullValueToString(value: Uuid): String = "'${valueToUuid(value)}'"

    internal fun valueToUuid(value: Any): java.util.UUID = when (value) {
        is java.util.UUID -> value
        is Uuid -> value.toJavaUuid()
        is String -> java.util.UUID.fromString(value)
        is ByteArray -> ByteBuffer.wrap(value).let { java.util.UUID(it.long, it.long) }
        else -> error("Unexpected value of type Uuid: ${value.javaClass.canonicalName}")
    }
}
