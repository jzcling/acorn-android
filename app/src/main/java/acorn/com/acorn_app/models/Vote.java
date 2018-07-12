package acorn.com.acorn_app.models;

public class Vote {
    private String voteId;
    private int entityId;
    private String userId;
    private int voteType;
    private String voteDate;

    public Vote() {}

    public Vote(int entityId, String userId, int voteType, String voteDate) {
        this.entityId = entityId;
        this.userId = userId;
        this.voteType = voteType;
        this.voteDate = voteDate;
    }

    public String getVoteId() {
        return voteId;
    }

    public int getEntityId() {
        return entityId;
    }

    public String getUserId() {
        return userId;
    }

    public int getVoteType() {
        return voteType;
    }

    public String getVoteDate() { return voteDate; }

    public void setVoteId(String voteId) {
        this.voteId = voteId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setVoteType(int voteType) {
        this.voteType = voteType;
    }

    public void setVoteDate(String voteDate) {
        this.voteDate = voteDate;
    }
}
