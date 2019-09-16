package acorn.com.acorn_app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import acorn.com.acorn_app.models.dbAddress;

@Database(entities = {dbAddress.class}, version = 1, exportSchema = false)
public abstract class AddressRoomDatabase extends RoomDatabase {
    public abstract AddressDAO addressDAO();

    private static volatile AddressRoomDatabase INSTANCE;

    public static AddressRoomDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AddressRoomDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AddressRoomDatabase.class, "address_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
