package foo.bar

import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.`annotation`.processing.Generated
import kotlin.Suppress
import kotlin.Unit

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["unchecked", "deprecation"])
public class MyDatabase_AutoMigration_1_2_Impl : Migration {
    private val callback: AutoMigrationSpec = ValidAutoMigrationWithDefault()

    public constructor() : super(1, 2)

    public override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("ALTER TABLE `Song` ADD COLUMN `artistId` INTEGER NOT NULL DEFAULT 0")
        callback.onPostMigrate(database)
    }
}