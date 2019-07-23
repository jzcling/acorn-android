package acorn.com.acorn_app.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Video {
    private String objectID;
    private String type;
    private String postAuthorUid;
    private String postAuthor;
    private String postText;
    private String postVideoUrl;
    private Long postDate;
    private String title;
    private String source;
    private String author;
    private Long pubDate;
    private String videoUrl;
    public String youtubeVideoId;
    private String mainTheme;
    private String trendingIndex;
    private Integer voteCount = 0;
    private Integer commentCount = 0;
    private Integer saveCount = 0;
    private Integer shareCount = 0;
    private Integer viewCount = 0;
    public List<String> theme = new ArrayList<>();
    public final Map<String, Long> upvoters = new HashMap<>();
    public final Map<String, Long> downvoters = new HashMap<>();
    public final Map<String, Integer> commenters = new HashMap<>();
    public final Map<String, Long> savers = new HashMap<>();
    public final Map<String, Long> sharers = new HashMap<>();
    public final Map<String, Long> viewedBy = new HashMap<>();
    public final Map<String, String> notificationTokens = new HashMap<>();
    public boolean changedSinceLastJob = true;
    public boolean isReported = false;
    public Integer starRatingCount = 0;
    public Long starRatingAverage = 0L;
    public Integer youtubeViewCount = 0;

    public Video() {}

    //Getters
    public String getObjectID() { return objectID; }

    public String getType() { return type; }

    public String getPostAuthorUid() { return postAuthorUid; }

    public String getPostAuthor() { return postAuthor; }

    public String getPostText() { return postText; }

    public String getPostVideoUrl() { return postVideoUrl; }

    public Long getPostDate() { return postDate; }

    public String getTitle() { return title; }

    public String getSource() { return source; }

    public Long getPubDate() { return pubDate; }

    public Integer getVoteCount() { return voteCount; }

    public Integer getCommentCount() { return commentCount; }

    public Integer getSaveCount() { return saveCount; }

    public Integer getShareCount() { return shareCount; }

    public Integer getViewCount() { return viewCount; }

    public String getTrendingIndex() { return trendingIndex; }

    public String getVideoUrl() { return videoUrl; }

    public String getAuthor() { return author; }

    public String getMainTheme() { return mainTheme; }

    //Setters

    public void setObjectID(String objectID) { this.objectID = objectID; }

    public void setType(String type) { this.type = type; }

    public void setPostAuthorUid(String postAuthorUid) { this.postAuthorUid = postAuthorUid; }

    public void setPostAuthor(String postAuthor) { this.postAuthor = postAuthor; }

    public void setPostText(String postText) { this.postText = postText; }

    public void setPostVideoUrl(String postVideoUrl) { this.postVideoUrl = postVideoUrl; }

    public void setPostDate(Long postDate) { this.postDate = postDate; }

    public void setTitle(String title) { this.title = title; }

    public void setSource(String source) { this.source = source; }

    public void setPubDate(Long pubDate) { this.pubDate = pubDate; }

    public void setVoteCount(Integer voteCount) { this.voteCount = voteCount; }

    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }

    public void setSaveCount(Integer saveCount) { this.saveCount = saveCount; }

    public void setShareCount(Integer shareCount) { this.shareCount = shareCount; }

    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public void setTrendingIndex(String trendingIndex) { this.trendingIndex = trendingIndex; }

    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public void setAuthor(String author) { this.author = author; }

    public void setMainTheme(String mainTheme) { this.mainTheme = mainTheme; }

    public Article toArticle() {
        Article article = new Article();

        article.setObjectID(objectID);
        article.setType(type);
        article.setPostAuthorUid(postAuthorUid);
        article.setPostAuthor(postAuthor);
        article.setPostText(postText);
        article.setLink(videoUrl != null ? videoUrl : postVideoUrl);
        article.setPostDate(postDate);
        article.setTitle(title);
        article.setSource(source);
        article.setAuthor(author);
        article.setPubDate(pubDate);
        article.setMainTheme(mainTheme);
        article.setTrendingIndex(trendingIndex);
        article.setVoteCount(voteCount);
        article.setCommentCount(commentCount);
        article.setSaveCount(saveCount);
        article.setShareCount(shareCount);
        article.setOpenCount(viewCount);
        article.theme = theme;
        article.upvoters.putAll(upvoters);
        article.downvoters.putAll(downvoters);
        article.commenters.putAll(commenters);
        article.savers.putAll(savers);
        article.sharers.putAll(sharers);
        article.openedBy.putAll(viewedBy);
        article.notificationTokens.putAll(notificationTokens);
        article.changedSinceLastJob = changedSinceLastJob;
        article.isReported = isReported;
        article.setReadTime(youtubeViewCount);

        return article;
    }
}