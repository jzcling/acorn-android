package acorn.com.acorn_app.models;

public class PremiumStatus {
    public Long start = 0L;
    public Long end = 0L;

    public PremiumStatus() {}

    public PremiumStatus(Long start, Long end) {
        this.start = start;
        this.end = end;
    }
}
