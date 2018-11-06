package acorn.com.acorn_app.ui.viewModels;

import android.arch.lifecycle.ViewModel;
import android.support.annotation.Nullable;

import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.data.VideoListLiveData;

public class VideoViewModel extends ViewModel {
    public static final int QUERY_LIMIT = 10;
    private final NetworkDataSource mDataSource;

    public VideoViewModel(NetworkDataSource dataSource) {
        mDataSource = dataSource;
    }

    public VideoListLiveData getVideos(@Nullable String startAt) {
        return mDataSource.getVideos("trendingIndex", startAt, QUERY_LIMIT);
    }
}
