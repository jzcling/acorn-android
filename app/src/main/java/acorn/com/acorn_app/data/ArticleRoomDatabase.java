package acorn.com.acorn_app.data;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
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
