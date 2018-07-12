package acorn.com.acorn_app.models;

public class Notif {
    public String type;
//    public List<Integer> index;
    public String articleId;
    public String text;
    public String title;
    public String source;
    public String imageUrl;
    public String theme;
    public String extra;
    public Long timestamp;

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