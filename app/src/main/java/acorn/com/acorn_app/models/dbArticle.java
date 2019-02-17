package acorn.com.acorn_app.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;

import java.util.Date;

@Entity(tableName = "article_table")
public class dbArticle {
    @PrimaryKey
    @NonNull
    public String objectID;
    public String title;
    public String source;
    public Long pubDate;
    public String imageUrl;
    public String link;
    public String author;
    public String mainTheme;
    public Integer voteCount;
    public Integer commentCount;
    public Long writeDate;
    public int isSaved;
    public String htmlContent;

    public dbArticle(@NonNull String objectID, String title, String source,
                     Long pubDate, String imageUrl, String link, String author,
                     String mainTheme, Integer voteCount, Integer commentCount,
                     Long writeDate, int isSaved, String htmlContent) {

        this.objectID = objectID;
        this.title = title;
        this.source = source;
        this.pubDate = pubDate;
        this.imageUrl = imageUrl;
        this.link = link;
        this.author = author;
        this.mainTheme = mainTheme;
        this.voteCount = voteCount;
        this.commentCount = commentCount;
        this.writeDate = writeDate;
        this.isSaved = isSaved;
        this.htmlContent = htmlContent;
    }

    public dbArticle(Context context, Article article) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String uid = sharedPrefs.getString("uid", "");

        this.objectID = article.getObjectID();
        this.title = article.getTitle();
        this.source = article.getSource();
        this.pubDate = article.getPubDate();
        this.imageUrl = article.getImageUrl();
        this.link = article.getLink();
        this.author = article.getAuthor();
        this.mainTheme = article.getMainTheme();
        this.voteCount = article.getVoteCount();
        this.commentCount = article.getCommentCount();
        this.writeDate = (new Date()).getTime();
        this.isSaved = article.savers.containsKey(uid) ? 1 : 0;
        this.htmlContent = article.htmlContent;
    }
}
