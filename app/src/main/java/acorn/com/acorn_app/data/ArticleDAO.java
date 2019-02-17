package acorn.com.acorn_app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import acorn.com.acorn_app.models.dbArticle;

@Dao
public interface ArticleDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(dbArticle article);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<dbArticle> articles);

    @Update
    void update(dbArticle article);

    @Update
    void update(dbArticle... articles);

    @Query("DELETE FROM article_table")
    void deleteAll();

    @Query("DELETE FROM article_table " +
            "WHERE (writeDate < :cutOffDate AND isSaved != 1)")
    void deleteOld(Long cutOffDate);

    @Query("SELECT * FROM article_table " +
            "WHERE objectID = :objectID")
    dbArticle getDbArticle(String objectID);
}
