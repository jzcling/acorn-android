package acorn.com.acorn_app.models;

public class Notif {
    public final String type;
    public final String articleId;
    public final String text;
    public final String title;
    public final String source;
    public final String imageUrl;
    public final String theme;
    public final String extra;
    public final Long timestamp;
    public final String link;

    public Notif(String type, String articleId, String text, String title,
                 String source, String imageUrl, String theme, String extra,
                 Long timestamp, String link) {
        this.type = type;
        this.articleId = articleId;
        this.text = text;
        this.title = title;
        this.source = source;
        this.imageUrl = imageUrl;
        this.theme = theme;
        this.extra = extra;
        this.timestamp = timestamp;
        this.link = link;
    }
}