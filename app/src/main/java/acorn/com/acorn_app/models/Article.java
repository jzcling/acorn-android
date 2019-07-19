package acorn.com.acorn_app.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Article {
    private Integer entityId;
    private String objectID;
    private String type;
    private String postAuthorUid;
    private String postAuthor;
    private String postText;
    private String postImageUrl;
    private Long postDate;
    private String title;
    private String source;
    private Long pubDate;
    private String imageUrl;
    private String link;
    private String author;
    private String mainTheme;
    private Integer readTime;
    private String trendingIndex;
    private Integer voteCount = 0;
    private Integer commentCount = 0;
    private Integer saveCount = 0;
    private Integer shareCount = 0;
    private Integer openCount = 0;
    public List<String> category = new ArrayList<>();
    public List<String> theme = new ArrayList<>();
    public final Map<String, Long> upvoters = new HashMap<>();
    public final Map<String, Long> downvoters = new HashMap<>();
    public final Map<String, Integer> commenters = new HashMap<>();
    public final Map<String, Long> savers = new HashMap<>();
    public final Map<String, Long> sharers = new HashMap<>();
    public final Map<String, Long> openedBy = new HashMap<>();
    public final Map<String, String> notificationTokens = new HashMap<>();
    public boolean changedSinceLastJob = true;
    public boolean isReported = false;
    public String htmlContent;
    public String selector;
    public Long reminderDate;
    public boolean hasAddress = false;
    public final Map<String, Long> duplicates = new HashMap<>();

    public Article() {}

    public Article(Integer entityId, String objectID, String type, String postAuthorUid, String postAuthor,
                   String postText, String postImageUrl, Long postDate, String title, String source, Long pubDate,
                   String trendingIndex, String imageUrl, String link, String author, String mainTheme,
                   Integer readTime) {
        this.entityId = entityId;
        this.objectID = objectID;
        this.type = type;
        this.postAuthorUid = postAuthorUid;
        this.postAuthor = postAuthor;
        this.postText = postText;
        this.postImageUrl = postImageUrl;
        this.postDate = postDate;
        this.title = title;
        this.source = source;
        this.pubDate = pubDate;
        this.trendingIndex = trendingIndex;
        this.imageUrl = imageUrl;
        this.link = link;
        this.author = author;
        this.mainTheme = mainTheme;
        this.readTime = readTime;
    }

    //Getters
    public Integer getEntityId() { return entityId; }

    public String getObjectID() { return objectID; }

    public String getType() { return type; }

    public String getPostAuthorUid() { return postAuthorUid; }

    public String getPostAuthor() { return postAuthor; }

    public String getPostText() { return postText; }

    public String getPostImageUrl() { return postImageUrl; }

    public Long getPostDate() { return postDate; }

    public String getTitle() { return title; }

    public String getSource() { return source; }

    public Long getPubDate() { return pubDate; }

    public Integer getVoteCount() { return voteCount; }

    public Integer getCommentCount() { return commentCount; }

    public Integer getSaveCount() { return saveCount; }

    public Integer getShareCount() { return shareCount; }

    public Integer getOpenCount() { return openCount; }

    public String getTrendingIndex() { return trendingIndex; }

    public String getImageUrl() { return imageUrl; }

    public String getLink() { return link; }

    public String getAuthor() { return author; }

    public String getMainTheme() { return mainTheme; }

    public Integer getReadTime() { return readTime; }

    //Setters

    public void setEntityId(Integer entityId) { this.entityId = entityId; }

    public void setObjectID(String objectID) { this.objectID = objectID; }

    public void setType(String type) { this.type = type; }

    public void setPostAuthorUid(String postAuthorUid) { this.postAuthorUid = postAuthorUid; }

    public void setPostAuthor(String postAuthor) { this.postAuthor = postAuthor; }

    public void setPostText(String postText) { this.postText = postText; }

    public void setPostImageUrl(String postImageUrl) { this.postImageUrl = postImageUrl; }

    public void setPostDate(Long postDate) { this.postDate = postDate; }

    public void setTitle(String title) { this.title = title; }

    public void setSource(String source) { this.source = source; }

    public void setPubDate(Long pubDate) { this.pubDate = pubDate; }

    public void setVoteCount(Integer voteCount) { this.voteCount = voteCount; }

    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }

    public void setSaveCount(Integer saveCount) { this.saveCount = saveCount; }

    public void setShareCount(Integer shareCount) { this.shareCount = shareCount; }

    public void setOpenCount(Integer openCount) { this.openCount = openCount; }

    public void setTrendingIndex(String trendingIndex) { this.trendingIndex = trendingIndex; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public void setLink(String link) { this.link = link; }

    public void setAuthor(String author) { this.author = author; }

    public void setMainTheme(String mainTheme) { this.mainTheme = mainTheme; }

    public void setReadTime(Integer readTime) { this.readTime = readTime; }
}