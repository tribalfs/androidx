import android.database.Cursor
import androidx.room.EntityInsertionAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.RoomSQLiteQuery.Companion.acquire
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.query
import androidx.sqlite.db.SupportSQLiteStatement
import java.lang.Class
import javax.`annotation`.processing.Generated
import kotlin.ByteArray
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmStatic

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["unchecked", "deprecation"])
public class MyDao_Impl : MyDao {
    private val __db: RoomDatabase

    private val __insertionAdapterOfMyEntity: EntityInsertionAdapter<MyEntity>

    public constructor(__db: RoomDatabase) {
        this.__db = __db
        this.__insertionAdapterOfMyEntity = object : EntityInsertionAdapter<MyEntity>(__db) {
            public override fun createQuery(): String =
                "INSERT OR ABORT INTO `MyEntity` (`pk`,`byteArray`,`nullableByteArray`) VALUES (?,?,?)"

            public override fun bind(statement: SupportSQLiteStatement, entity: MyEntity): Unit {
                statement.bindLong(1, entity.pk.toLong())
                statement.bindBlob(2, entity.byteArray)
                if (entity.nullableByteArray == null) {
                    statement.bindNull(3)
                } else {
                    statement.bindBlob(3, entity.nullableByteArray)
                }
            }
        }
    }

    public override fun addEntity(item: MyEntity): Unit {
        __db.assertNotSuspendingTransaction()
        __db.beginTransaction()
        try {
            __insertionAdapterOfMyEntity.insert(item)
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
    }

    public override fun getEntity(): MyEntity {
        val _sql: String = "SELECT * FROM MyEntity"
        val _statement: RoomSQLiteQuery = acquire(_sql, 0)
        __db.assertNotSuspendingTransaction()
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
            val _cursorIndexOfPk: Int = getColumnIndexOrThrow(_cursor, "pk")
            val _cursorIndexOfByteArray: Int = getColumnIndexOrThrow(_cursor, "byteArray")
            val _cursorIndexOfNullableByteArray: Int = getColumnIndexOrThrow(_cursor, "nullableByteArray")
            val _result: MyEntity
            if (_cursor.moveToFirst()) {
                val _tmpPk: Int
                _tmpPk = _cursor.getInt(_cursorIndexOfPk)
                val _tmpByteArray: ByteArray
                _tmpByteArray = _cursor.getBlob(_cursorIndexOfByteArray)
                val _tmpNullableByteArray: ByteArray?
                if (_cursor.isNull(_cursorIndexOfNullableByteArray)) {
                    _tmpNullableByteArray = null
                } else {
                    _tmpNullableByteArray = _cursor.getBlob(_cursorIndexOfNullableByteArray)
                }
                _result = MyEntity(_tmpPk,_tmpByteArray,_tmpNullableByteArray)
            } else {
                error("Cursor was empty, but expected a single item.")
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    public companion object {
        @JvmStatic
        public fun getRequiredConverters(): List<Class<*>> = emptyList()
    }
}