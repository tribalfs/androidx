import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.RoomSQLiteQuery.Companion.acquire
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.query
import java.lang.Class
import java.util.ArrayList
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.jvm.JvmStatic

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["unchecked", "deprecation"])
public class MyDao_Impl : MyDao {
    private val __db: RoomDatabase

    public constructor(__db: RoomDatabase) {
        this.__db = __db
    }

    public override fun queryOfList(): List<MyEntity> {
        val _sql: String = "SELECT * FROM MyEntity"
        val _statement: RoomSQLiteQuery = acquire(_sql, 0)
        __db.assertNotSuspendingTransaction()
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
            val _cursorIndexOfPk: Int = getColumnIndexOrThrow(_cursor, "pk")
            val _cursorIndexOfOther: Int = getColumnIndexOrThrow(_cursor, "other")
            val _result: MutableList<MyEntity> = ArrayList<MyEntity>(_cursor.getCount())
            while (_cursor.moveToNext()) {
                val _item: MyEntity
                val _tmpPk: Int
                _tmpPk = _cursor.getInt(_cursorIndexOfPk)
                val _tmpOther: String
                _tmpOther = _cursor.getString(_cursorIndexOfOther)
                _item = MyEntity(_tmpPk,_tmpOther)
                _result.add(_item)
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    public override fun queryOfNullableList(): List<MyEntity>? {
        val _sql: String = "SELECT * FROM MyEntity"
        val _statement: RoomSQLiteQuery = acquire(_sql, 0)
        __db.assertNotSuspendingTransaction()
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
            val _cursorIndexOfPk: Int = getColumnIndexOrThrow(_cursor, "pk")
            val _cursorIndexOfOther: Int = getColumnIndexOrThrow(_cursor, "other")
            val _result: MutableList<MyEntity> = ArrayList<MyEntity>(_cursor.getCount())
            while (_cursor.moveToNext()) {
                val _item: MyEntity
                val _tmpPk: Int
                _tmpPk = _cursor.getInt(_cursorIndexOfPk)
                val _tmpOther: String
                _tmpOther = _cursor.getString(_cursorIndexOfOther)
                _item = MyEntity(_tmpPk,_tmpOther)
                _result.add(_item)
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    public override fun queryOfNullableEntityList(): List<MyNullableEntity?> {
        val _sql: String = "SELECT * FROM MyEntity"
        val _statement: RoomSQLiteQuery = acquire(_sql, 0)
        __db.assertNotSuspendingTransaction()
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
            val _cursorIndexOfPk: Int = getColumnIndexOrThrow(_cursor, "pk")
            val _cursorIndexOfOther: Int = getColumnIndexOrThrow(_cursor, "other")
            val _result: MutableList<MyNullableEntity?> = ArrayList<MyNullableEntity?>(_cursor.getCount())
            while (_cursor.moveToNext()) {
                val _item: MyNullableEntity?
                val _tmpPk: Int?
                if (_cursor.isNull(_cursorIndexOfPk)) {
                    _tmpPk = null
                } else {
                    _tmpPk = _cursor.getInt(_cursorIndexOfPk)
                }
                val _tmpOther: String?
                if (_cursor.isNull(_cursorIndexOfOther)) {
                    _tmpOther = null
                } else {
                    _tmpOther = _cursor.getString(_cursorIndexOfOther)
                }
                _item = MyNullableEntity(_tmpPk,_tmpOther)
                _result.add(_item)
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    public override fun queryOfNullableListWithNullableEntity(): List<MyNullableEntity>? {
        val _sql: String = "SELECT * FROM MyEntity"
        val _statement: RoomSQLiteQuery = acquire(_sql, 0)
        __db.assertNotSuspendingTransaction()
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
            val _cursorIndexOfPk: Int = getColumnIndexOrThrow(_cursor, "pk")
            val _cursorIndexOfOther: Int = getColumnIndexOrThrow(_cursor, "other")
            val _result: MutableList<MyNullableEntity> = ArrayList<MyNullableEntity>(_cursor.getCount())
            while (_cursor.moveToNext()) {
                val _item: MyNullableEntity
                val _tmpPk: Int?
                if (_cursor.isNull(_cursorIndexOfPk)) {
                    _tmpPk = null
                } else {
                    _tmpPk = _cursor.getInt(_cursorIndexOfPk)
                }
                val _tmpOther: String?
                if (_cursor.isNull(_cursorIndexOfOther)) {
                    _tmpOther = null
                } else {
                    _tmpOther = _cursor.getString(_cursorIndexOfOther)
                }
                _item = MyNullableEntity(_tmpPk,_tmpOther)
                _result.add(_item)
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