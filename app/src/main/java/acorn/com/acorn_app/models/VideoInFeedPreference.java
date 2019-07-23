package acorn.com.acorn_app.models;

import java.util.HashMap;
import java.util.Map;

public class VideoInFeedPreference {
    public Boolean showVideos = true;
    public Map<String, Long> channelsToRemove = new HashMap<>();

    public VideoInFeedPreference() {}

    public VideoInFeedPreference(Boolean showVideos, Map<String, Long> channelsToRemove) {
        this.showVideos = showVideos;
        this.channelsToRemove = channelsToRemove;
    }
}
