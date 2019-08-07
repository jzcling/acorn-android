package acorn.com.acorn_app.models;

public class TimeLog {
    public String itemId;
    public String userId;
    public Long openTime;
    public Long closeTime;
    public Long activeTime;
    public Float percentReadTimeActive;
    public Float percentScroll;
    public String type;

    public TimeLog() {}
}
