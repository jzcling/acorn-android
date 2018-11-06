package acorn.com.acorn_app.ui.activities;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
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
        mLinearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new VideoFeedAdapter(this, mDataSource);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                loadMoreVideos();
            }
        });

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
                /*
                1 - While child listener adds Videos incrementally to live data list,
                we add to adapter list if it had not already been added.
                2 - On any changes to live data list after adapter list is set,
                update changes in-situ.
                This way, adapter list expands up to size of all observed live data lists
                (includes all loadMoreVideos lists), with no repeat Videos on changes.
                */
                List<Video> currentList = mAdapter.getList();
                for (int i = 0; i < videos.size(); i++) {
                    if (currentList.size() < i+1) {
                        //1
                        currentList.add(i, videos.get(i));
                        Log.d(TAG, "added: " + currentList.size());
                    } else {
                        //2
                        currentList.set(i, videos.get(i));
                        Log.d(TAG, "set: " + currentList.size());
                    }
                }
                mAdapter.setList(currentList);
            }
        };
        mVideoListLD.observeForever(VideoListObserver);
        mObservedList.put(mVideoListLD, VideoListObserver);
        if (mLlmState != null) {
            mLinearLayoutManager.onRestoreInstanceState(mLlmState);
        }

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
