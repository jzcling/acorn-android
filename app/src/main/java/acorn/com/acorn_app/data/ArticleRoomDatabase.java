package acorn.com.acorn_app.data;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import acorn.com.acorn_app.models.dbArticle;

@Database(entities = {dbArticle.class}, version = 1, exportSchema = false)
public abstract class ArticleRoomDatabase extends RoomDatabase {
    public abstract ArticleDAO articleDAO();

    private static volatile ArticleRoomDatabase INSTANCE;

    public static ArticleRoomDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (ArticleRoomDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            ArticleRoomDatabase.class, "article_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
