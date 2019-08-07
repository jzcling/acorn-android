package acorn.com.acorn_app.ui.activities;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.data.VideoListLiveData;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.ui.adapters.VideoFeedAdapter;
import acorn.com.acorn_app.ui.viewModels.VideoViewModel;
import acorn.com.acorn_app.ui.viewModels.VideoViewModelFactory;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.InjectorUtils;


public class VideoFeedActivity extends AppCompatActivity {
    private static final String TAG = "VideoFeedActivity";

    //Data source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    //View Models
    private VideoViewModel mVideoViewModel;
    private final Map<VideoListLiveData, Observer<List<Video>>> mObservedList = new HashMap<>();

    //RecyclerView
    private RecyclerView mRecyclerView;
    private VideoFeedAdapter mAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private boolean isLoadingMore = false;

    //For restoring state
    private static Parcelable mLlmState;

    // Video list
    VideoListLiveData mVideoListLD;


    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_feed);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up data source
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);

        // Set up recycler view
        mRecyclerView = (RecyclerView) findViewById(R.id.video_feed_rv);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new VideoFeedAdapter(this, mDataSource);
        mRecyclerView.setAdapter(mAdapter);
//        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                loadMoreVideos();
//            }
//        });

        setUpInitialViewModelObserver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mObservedList.size() > 0) {
            for (VideoListLiveData liveData : mObservedList.keySet()) {
                liveData.removeObserver(mObservedList.get(liveData));
            }
            mObservedList.clear();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mLlmState = mLinearLayoutManager.onSaveInstanceState();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setUpInitialViewModelObserver() {
        // Set up view model
        VideoViewModelFactory factory = InjectorUtils.provideVideoViewModelFactory(this.getApplicationContext());
        mVideoViewModel = ViewModelProviders.of(this, factory).get(VideoViewModel.class);

        mVideoListLD = mVideoViewModel.getVideos(null);
        Observer<List<Video>> VideoListObserver = videos -> {
            if (videos != null) {
                mAdapter.setList(videos, () -> {
                    if (mLlmState != null) {
                        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
                    }
                });
            }
        };
        mVideoListLD.observe(this, VideoListObserver);
        mObservedList.put(mVideoListLD, VideoListObserver);
    }

    private void loadMoreVideos() {
        if (isLoadingMore) return;

        int currentPosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
        final int trigger = 5;
        final int initialListCount = mAdapter.getItemCount();
        final String startAt = mAdapter.getLastItem().getTrendingIndex();
        List<Video> currentList = mAdapter.getList();

        if (initialListCount <= trigger) return;

        if (currentPosition > mAdapter.getItemCount() - trigger) {
            isLoadingMore = true;

            VideoListLiveData addListLD = mVideoViewModel.getVideos(startAt);
            Observer<List<Video>> addListObserver = videos -> {
                if (videos != null) {
                    /*
                    initialListCount marks where the end of the list was before additional
                    Videos are loaded. Live data list of additional Videos will start
                    from the last Video in the current list, so startIndex is initialListCount - 1
                    */
                    int startIndex = initialListCount - 1;
                    for (int i = 0; i < videos.size(); i++) {
                        if (currentList.size() < startIndex + i + 1) {
                            Log.d(TAG, "add: " + (startIndex + i));
                            currentList.add(startIndex + i, videos.get(i));
                        } else {
                            Log.d(TAG, "set: " + (startIndex + i));
                            currentList.set(startIndex + i, videos.get(i));
                        }
                    }
                    mAdapter.setList(currentList);
                }
            };
            addListLD.observeForever(addListObserver);
            mObservedList.put(addListLD, addListObserver);

            new Handler().postDelayed(()->isLoadingMore = false,100);
        }
    }
}
