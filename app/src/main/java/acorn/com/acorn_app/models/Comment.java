package acorn.com.acorn_app.models;

public class Comment {
    private String commentId;
    private String uid;
    private String userDisplayName;
    private String commentText;
    private String imageUrl;
    private String localImageUri;
    private Long pubDate;
    private boolean isUrl;
    private String urlSource;
    private String urlLink;
    public boolean isReported = false;

    public Comment() {}

    public Comment(String uid, String userDisplayName, String commentText,
                   String imageUrl, String localImageUri, Long pubDate,
                   boolean isUrl, String urlSource, String urlLink) {
        this.uid = uid;
        this.userDisplayName = userDisplayName;
        this.commentText = commentText;
        this.imageUrl = imageUrl;
        this.localImageUri = localImageUri;
        this.pubDate = pubDate;
        this.isUrl = isUrl;
        this.urlSource = urlSource;
        this.urlLink = urlLink;
    }

    public String getCommentId() { return commentId; }

    public String getUid() {
        return uid;
    }

    public String getUserDisplayName() { return userDisplayName; }

    public String getCommentText() {
        return commentText;
    }

    public String getImageUrl() { return imageUrl; }

    public String getLocalImageUri() { return localImageUri; }

    public Long getPubDate() {
        return pubDate;
    }

    public boolean getIsUrl() { return isUrl; }

    public String geturlSource() { return urlSource; }

    public String getUrlLink() { return urlLink; }

    public void setCommentId(String commentId) { this.commentId = commentId; }

    public void setUid(String uid) { this.uid = uid; }

    public void setUserDisplayName(String userDisplayName) { this.userDisplayName = userDisplayName; }

    public void setCommentText(String commentText) { this.commentText = commentText; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public void setLocalImageUri(String localImageUri) { this.localImageUri = localImageUri; }

    public void setPubDate(Long pubDate) { this.pubDate = pubDate; }

    public void setIsUrl(boolean isUrl) { this.isUrl = isUrl; }

    public void seturlSource(String urlSource) { this.urlSource = urlSource; }

    public void setUrlLink(String urlLink) { this.urlLink = urlLink; }
}
