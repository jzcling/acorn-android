package acorn.com.acorn_app.models;

import com.google.firebase.database.Exclude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class User {
    private String uid;
    private String displayName;
    private String token;
    private String email;
    public boolean isEmailVerified;
    private String device;
    private int status = 0;
    private int points = 0;
    private int targetPoints = 10;
    private Long creationTimeStamp;
    private Long lastSignInTimeStamp;
    private Long lastRecArticlesPushTime = 0L;
    private Long lastRecArticlesScheduleTime = 0L;
    private Long lastRecDealsPushTime = 0L;
    private Long lastRecDealsScheduleTime = 0L;
    private ArrayList<String> subscriptions = new ArrayList<>();
    public final Map<String, Long> createdPosts = new HashMap<>();
    public final Map<String, Long> upvotedItems = new HashMap<>();
    public final Map<String, Long> downvotedItems = new HashMap<>();
    public final Map<String, Integer> commentedItems = new HashMap<>();
    public final Map<String, Long> savedItems = new HashMap<>();
    public final Map<String, Long> sharedItems = new HashMap<>();
    public final Map<String, Long> openedArticles = new HashMap<>();
    public final Map<String, Integer> openedThemes = new HashMap<>();
    public final Map<String, Long> viewedVideos = new HashMap<>();
    private Integer subscriptionsCount = 0;
    private Integer createdPostsCount = 0;
    private Integer upvotedItemsCount = 0;
    private Integer downvotedItemsCount = 0;
    private Integer commentedItemsCount = 0;
    private Integer savedItemsCount = 0;
    private Integer sharedItemsCount = 0;
    public boolean openedSinceLastReport;
    public final Map<String, Long> premiumStatus = new HashMap<>();
    public String referredBy;

    public User() {}

    public User(String uid, String displayName, String token, String email, String device,
                Long creationTimeStamp, Long lastSignInTimeStamp) {
        this.uid = uid;
        this.displayName = displayName;
        this.token = token;
        this.email = email;
        this.device = device;
        this.creationTimeStamp = creationTimeStamp;
        this.lastSignInTimeStamp = lastSignInTimeStamp;
    }

    //Getters

    public String getUid() { return uid; }

    public String getDisplayName() { return displayName; }

    public String getToken() { return token; }

    public String getEmail() { return email; }

    public String getDevice() { return device; }

    public int getStatus() { return status; }

    public int getPoints() { return points; }

    public Long getCreationTimeStamp() { return creationTimeStamp; }

    public Long getLastSignInTimeStamp() { return lastSignInTimeStamp; }

    public Long getLastRecArticlesPushTime() { return lastRecArticlesPushTime; }

    public Long getLastRecArticlesScheduleTime() { return lastRecArticlesScheduleTime; }

    public Long getLastRecDealsPushTime() { return lastRecDealsPushTime; }

    public Long getLastRecDealsScheduleTime() { return lastRecDealsScheduleTime; }

    public ArrayList<String> getSubscriptions() { return subscriptions; }

    public Integer getSubscriptionsCount() { return subscriptionsCount; }

    public Integer getCreatedPostsCount() { return createdPostsCount; }

    public Integer getUpvotedItemsCount() { return upvotedItemsCount; }

    public Integer getDownvotedItemsCount() { return downvotedItemsCount; }

    public Integer getCommentedItemsCount() { return commentedItemsCount; }

    public Integer getSavedItemsCount() { return savedItemsCount; }

    public Integer getSharedItemsCount() { return sharedItemsCount; }

    public int getTargetPoints() { return targetPoints; }

    //Setters

    public void setUid(String uid) { this.uid = uid; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public void setToken(String token) { this.token = token; }

    public void setEmail(String email) { this.email = email; }

    public void setDevice(String device) { this.device = device; }

    public void setStatus(int status) { this.status = status; }

    public void setPoints(int points) { this.points = points; }

    public void setCreationTimeStamp(Long creationTimeStamp) { this.creationTimeStamp = creationTimeStamp; }

    public void setLastSignInTimeStamp(Long lastSignInTimeStamp) { this.lastSignInTimeStamp = lastSignInTimeStamp; }

    public void setLastRecArticlesPushTime(Long lastRecArticlePushTime) { this.lastRecArticlesPushTime = lastRecArticlePushTime; }

    public void setLastRecArticlesScheduleTime(Long lastRecArticlesScheduleTime) { this.lastRecArticlesScheduleTime = lastRecArticlesScheduleTime; }

    public void setLastRecDealsPushTime(Long lastRecDealsPushTime) { this.lastRecDealsPushTime = lastRecDealsPushTime; }

    public void setLastRecDealsScheduleTime(Long lastRecDealsScheduleTime) { this.lastRecDealsScheduleTime = lastRecDealsScheduleTime; }

    public void setSubscriptions(ArrayList<String> subscriptions) { this.subscriptions = subscriptions; }

    public void setSubscriptionsCount(Integer subscriptionsCount) { this.subscriptionsCount = subscriptionsCount; }

    public void setCreatedPostsCount(Integer createdPostsCount) { this.createdPostsCount = createdPostsCount; }

    public void setUpvotedItemsCount(Integer upvotedItemsCount) { this.upvotedItemsCount = upvotedItemsCount; }

    public void setDownvotedItemsCount(Integer downvotedItemsCount) { this.downvotedItemsCount = downvotedItemsCount; }

    public void setCommentedItemsCount(Integer commentedItemsCount) { this.commentedItemsCount = commentedItemsCount; }

    public void setSavedItemsCount(Integer savedItemsCount) { this.savedItemsCount = savedItemsCount; }

    public void setSharedItemsCount(Integer sharedItemsCount) { this.sharedItemsCount = sharedItemsCount; }

    public void setTargetPoints(int targetPoints) { this.targetPoints = targetPoints; }

    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("uid", uid);
        result.put("displayName", displayName);
        result.put("token", token);
        result.put("email", email);
        result.put("isEmailVerified", isEmailVerified);
        result.put("device", device);
        result.put("creationTimeStamp", creationTimeStamp);
        result.put("lastSignInTimeStamp", lastSignInTimeStamp);
        result.put("openedSinceLastReport", openedSinceLastReport);

        return result;
    }
}