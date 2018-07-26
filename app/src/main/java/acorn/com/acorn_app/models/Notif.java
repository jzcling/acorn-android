package acorn.com.acorn_app.models;

public class Notif {
    public final String type;
//    public List<Integer> index;
    public final String articleId;
    public final String text;
    public final String title;
    public final String source;
    public final String imageUrl;
    public final String theme;
    public final String extra;
    public final Long timestamp;

    public Notif(String type, String articleId, String text, String title,
                 String source, String imageUrl, String theme, String extra, Long timestamp) {
        this.type = type;
//        this.index = index;
        this.articleId = articleId;
        this.text = text;
        this.title = title;
        this.source = source;
        this.imageUrl = imageUrl;
        this.theme = theme;
        this.extra = extra;
        this.timestamp = timestamp;
    }
}