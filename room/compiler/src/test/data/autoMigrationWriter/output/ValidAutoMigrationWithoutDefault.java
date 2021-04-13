package foo.bar;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.lang.Override;
import java.lang.SuppressWarnings;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
class AutoMigration_1_2_Impl extends Migration {
    private final ValidAutoMigrationWithoutDefault callback = new ValidAutoMigrationWithoutDefault();

    public AutoMigration_1_2_Impl() {
        super(1, 2);
    }

    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE `Song` ADD COLUMN `artistId` INTEGER DEFAULT NULL");
        callback.onPostMigrate(database);
    }
}
