package acorn.com.acorn_app.data;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import android.os.Handler;
import androidx.annotation.NonNull;

import android.util.ArraySet;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.models.VideoInFeedPreference;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;

import static acorn.com.acorn_app.data.NetworkDataSource.PREFERENCES_REF;
import static acorn.com.acorn_app.data.NetworkDataSource.VIDEOS_IN_FEED_PREF_REF;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mSharedPreferences;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserThemePrefs;

public class FeedListLiveData extends LiveData<List<Object>> {
    private static final String TAG = "FeedListLiveData";

    private final DatabaseReference mDatabaseReference = FirebaseDatabase.getInstance().getReference();

    private Query query;
    private List<Query> queryList;
    public Map<Query, ValueEventListener> savedArticlesQueryList = new HashMap<>();
    private Map<Query, ValueEventListener> algoliaArticlesQueryList = new HashMap<>();
    private Map<Query, ValueEventListener> videosQueryList = new HashMap<>();

    // States: 0 = Recent, 1 = Trending, 2 = Saved articles, 3 = Search, 4 = Deals
    // -1 = mainTheme, -2 = source
    private int state = 3;
    private List<Integer> searchStates = new ArrayList<>();

    private final MyChildEventListener childListener = new MyChildEventListener();

    private final List<String> mItemIds = new ArrayList<>();
    private final List<Object> mItemList = new ArrayList<>();
    private List<String> mThemeList = new ArrayList<>();
    private int mSeed = 1;

    private boolean listenerRemovePending = false;
    private final Handler handler = new Handler();
    private final Runnable removeListener = () -> {
            if (state == 2){
                for (Query query : savedArticlesQueryList.keySet()) {
                    query.removeEventListener(savedArticlesQueryList.get(query));
                    Log.d(TAG, "listener removed");
                }
            } else if (searchStates.contains(state)) {
                for (Query query : algoliaArticlesQueryList.keySet()) {
                    query.removeEventListener(algoliaArticlesQueryList.get(query));
                }
                for (Query query : videosQueryList.keySet()) {
                    query.removeEventListener(videosQueryList.get(query));
                }
            } else {
                query.removeEventListener(childListener);
            }
            Log.d(TAG, "all listeners removed");
            listenerRemovePending = false;
    };

    private DataSnapshot mHitsSnap;
    private TaskCompletionSource<List<Object>> mArticleSource;
    private boolean isPendingRefresh = false;
    private final Runnable setArticleList = () -> {
        mItemList.clear();
        mItemIds.clear();

        List<Task<Boolean>> taskList = new ArrayList<>();
        if (mHitsSnap.exists()) {
            for (DataSnapshot snap : mHitsSnap.getChildren()) {
                Article article = snap.getValue(Article.class);
                if (article != null) {
                    String toLoadId = article.getObjectID();

                    TaskCompletionSource<Boolean> dbSource = new TaskCompletionSource<>();
                    Task<Boolean> dbTask = dbSource.getTask();
                    taskList.add(dbTask);

                    Query articleQuery = mDatabaseReference.child("article/" + toLoadId);
                    MainFeedValueEventListener articleValueListener = new MainFeedValueEventListener(dbSource);
                    algoliaArticlesQueryList.put(articleQuery, articleValueListener);
                    articleQuery.addValueEventListener(articleValueListener);
                }
            }

            Tasks.whenAll(taskList).addOnSuccessListener(aVoid -> {
                mArticleSource.trySetResult(mItemList);
                isPendingRefresh = false;
            });
        }
    };

    public FeedListLiveData(Query query, List<String> themeList, int seed) {
        this.query = query;
        this.mThemeList = themeList;
        this.mSeed = seed;
        this.searchStates.add(-2);
        this.searchStates.add(-1);
        this.searchStates.add(3);
    }

    public FeedListLiveData(Query query, int state) {
        this.query = query;
        this.state = state;
        this.searchStates.add(-2);
        this.searchStates.add(-1);
        this.searchStates.add(3);
    }

    @Override
    protected void onActive() {
        Log.d(TAG, "onActive");
        if (listenerRemovePending) {
            handler.removeCallbacks(removeListener);
        } else {
            if (state == 2) { // Saved Articles
                query.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        // remove all articles that have been unsaved
                        // when a delete happens, onDataChange is triggered as well, returning a null dataSnapshot
                        // here we first remove any changed article, and add it back if there is any data
                        // the net effect is that articles that are unsaved are removed and
                        // saved articles that change are updated
                        List<String> articleIds = new ArrayList<>(mItemIds);
                        for (DataSnapshot snap : dataSnapshot.getChildren()) {
                            articleIds.remove(snap.getKey());
                        }
                        if (articleIds.size() > 0) {
                            for (String id : articleIds) {
                                int index = mItemIds.indexOf(id);
                                if (index > -1) {
                                    mItemIds.remove(index);
                                    mItemList.remove(index);
                                    Query articleQuery = mDatabaseReference.child("article/" + id);

                                    ValueEventListener listener = savedArticlesQueryList.get(articleQuery);
                                    if (listener != null)
                                        articleQuery.removeEventListener(listener);
                                    savedArticlesQueryList.remove(articleQuery);
                                }
                            }
                        }

                        List<Task<Boolean>> taskList = new ArrayList<>();
                        for (DataSnapshot snap : dataSnapshot.getChildren()) {
                            String id = snap.getKey();

                            TaskCompletionSource<Boolean> dbSource = new TaskCompletionSource<>();
                            Task<Boolean> dbTask = dbSource.getTask();
                            taskList.add(dbTask);

                            Query articleQuery = mDatabaseReference.child("article/" + id);
                            SavedArticlesValueEventListener articleValueListener = new SavedArticlesValueEventListener(dbSource);
                            savedArticlesQueryList.put(articleQuery, articleValueListener);
                            articleQuery.addValueEventListener(articleValueListener);
                        }
                        Tasks.whenAll(taskList).addOnCompleteListener(task -> setValue(mItemList));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        setValue(new ArrayList<>());
                    }
                });
            } else if (searchStates.contains(state)) { // Subscriptions, Trending, Deals
                mArticleSource = new TaskCompletionSource<>();
                Task<List<Object>> articleTask = mArticleSource.getTask();

                query.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        // clear all data when getting refreshed feed
                        Log.d(TAG, "hits data changed");
                        if (isPendingRefresh) handler.removeCallbacks(setArticleList);

                        mHitsSnap = dataSnapshot;
                        handler.postDelayed(setArticleList,500);
                        isPendingRefresh = true;
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        setValue(new ArrayList<>());
                    }
                });

                boolean showVideos = mSharedPreferences.getBoolean("videosInFeed", true);
                if (showVideos) {
                    TaskCompletionSource<List<Video>> videoSource = new TaskCompletionSource<>();
                    Task<List<Video>> videoTask = videoSource.getTask();
                    Long cutoffDate = -DateUtils.getThreeDaysAgoMidnight();
                    Query videoQuery = mDatabaseReference.child("video").orderByChild("pubDate").endAt(cutoffDate);
                    VideoValueEventListener videoValueListener = new VideoValueEventListener(videoSource);
                    videoQuery.addListenerForSingleValueEvent(videoValueListener);

                    Set<String> channelsToRemove = mSharedPreferences.getStringSet(
                            "videosInFeedChannelsToRemove", new ArraySet<>());


                    Tasks.whenAll(articleTask, videoTask).addOnSuccessListener(aVoid -> {
                        List<Video> videos = videoTask.getResult();
                        List<Video> selectedVideos = new ArrayList<>();
                        if (videos != null) {
                            for (Video video : videos) {
                                if (mThemeList.contains(video.getMainTheme()) &&
                                        !channelsToRemove.contains(video.getSource())) {
                                    selectedVideos.add(video);
                                }
                            }
                            Collections.shuffle(selectedVideos, new Random(mSeed));
                            int sizeLimit = Math.min(mItemList.size() / 5, selectedVideos.size());
                            for (int i = 0; i < sizeLimit; i++) {
                                // after every 5th article, accommodating the new data indicator
                                mItemList.add(1 + (i + 1) * 5, selectedVideos.get(i));
                                mItemIds.add(1 + (i + 1) * 5, selectedVideos.get(i).getObjectID());

                                Query selectedVideoQuery = mDatabaseReference.child("video/" + selectedVideos.get(i).getObjectID());
                                SelectedVideoValueEventListener selectedVideoValueListener = new SelectedVideoValueEventListener();
                                videosQueryList.put(selectedVideoQuery, selectedVideoValueListener);
                                selectedVideoQuery.addValueEventListener(selectedVideoValueListener);
                            }
                        }
                        setValue(mItemList);
                    });
                } else {
                    Tasks.whenAll(articleTask).addOnSuccessListener(
                            aVoid -> setValue(mItemList));
                }
            } else {
                query.addChildEventListener(childListener);
            }
        }
        listenerRemovePending = false;
    }

    @Override
    protected void onInactive() {
        Log.d(TAG, "onInactive");
        handler.postDelayed(removeListener, 500);
        listenerRemovePending = true;
    }

    private class MyChildEventListener implements ChildEventListener {

        @Override
        public void onChildAdded(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildAdded");
            Article article = dataSnapshot.getValue(Article.class);

            if (!mItemIds.contains(dataSnapshot.getKey())) {
                if (article != null && !article.isReported) {
                    mItemIds.add(dataSnapshot.getKey());
                    mItemList.add(article);
                }
            }
            setValue(mItemList);
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildChanged");
            Article newArticle = dataSnapshot.getValue(Article.class);
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mItemIds.indexOf(articleKey);
            if (articleIndex > -1) {
                if (newArticle != null) {
                    if (newArticle.isReported) {
                        mItemIds.remove(articleIndex);
                        mItemList.remove(articleIndex);
                    } else {
                        // Replace with the new data
                        mItemList.set(articleIndex, newArticle);
                    }
                }
            }

            setValue(mItemList);
        }

        @Override
        public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "onChildRemoved");
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mItemIds.indexOf(articleKey);
            if (articleIndex > -1) {
                // Remove data from the list
                mItemIds.remove(articleIndex);
                mItemList.remove(articleIndex);
            }

            setValue(mItemList);
        }

        @Override
        public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildMoved");
            Article movedArticle = dataSnapshot.getValue(Article.class);
            String articleKey = dataSnapshot.getKey();

            int oldIndex = mItemIds.indexOf(articleKey);
            if (oldIndex > -1) {
                // Remove data from old position
                mItemIds.remove(oldIndex);
                mItemList.remove(oldIndex);

                // Add data in new position
                int newIndex = previousChildKey == null ? 0 : mItemIds.indexOf(previousChildKey) + 1;
                mItemIds.add(articleKey);
                mItemList.add(newIndex, movedArticle);
            }

            setValue(mItemList);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) { }
    }

    private class MainFeedValueEventListener implements ValueEventListener {
        private TaskCompletionSource<Boolean> dbSource;

        private MainFeedValueEventListener(TaskCompletionSource<Boolean> dbSource) {
            this.dbSource = dbSource;
        }

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "article data changed");
            if (dataSnapshot.exists()) {
                Article article = dataSnapshot.getValue(Article.class);
                String articleId = dataSnapshot.getKey();
                if (article != null) {
                    boolean duplicateExists = false;
                    if (article.duplicates.size() > 0) {
                        for (String id : article.duplicates.keySet()) {
                            if (mItemIds.contains(id)) {
                                duplicateExists = true;
                            }
                        }
                    }

                    if (!duplicateExists) {
                        int index = mItemIds.indexOf(articleId);
                        if (index > -1) {
                            mItemIds.set(index, articleId);
                            mItemList.set(index, article);
                        } else {
                            mItemIds.add(articleId);
                            mItemList.add(article);
                        }
                    } else {
                        int index = mItemIds.indexOf(articleId);
                        if (index > -1) {
                            // Remove data from the list
                            mItemIds.remove(index);
                            mItemList.remove(index);
                        }
                    }
                } else {
                    int index = mItemIds.indexOf(articleId);
                    if (index > -1) {
                        // Remove data from the list
                        mItemIds.remove(index);
                        mItemList.remove(index);
                    }
                }

                if (!dbSource.trySetResult(true)) {
                    Log.d(TAG, "article changed");
                    setValue(mItemList);
                }
            } else {
                dbSource.trySetException(new Exception("No data exists"));
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            dbSource.trySetException(databaseError.toException());
        }
    }

    private class SavedArticlesValueEventListener implements ValueEventListener {
        private TaskCompletionSource<Boolean> dbSource;

        private SavedArticlesValueEventListener(TaskCompletionSource<Boolean> dbSource) {
            this.dbSource = dbSource;
        }

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//            Log.d(TAG, "onDataChanged");
            if (dataSnapshot.exists()) {
                Article article = dataSnapshot.getValue(Article.class);
                String articleId = dataSnapshot.getKey();
                if (article != null) {
                    int index = mItemIds.indexOf(articleId);
                    if (index > -1) {
                        mItemIds.set(index, articleId);
                        mItemList.set(index, article);
                    } else {
                        mItemIds.add(articleId);
                        mItemList.add(article);
                    }
                } else {
                    int index = mItemIds.indexOf(articleId);
                    if (index > -1) {
                        // Remove data from the list
                        mItemIds.remove(index);
                        mItemList.remove(index);
                    }
                }

                if (!dbSource.trySetResult(true)) {
                    setValue(mItemList);
                }
            } else {
                dbSource.trySetException(new Exception("No data exists"));
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            dbSource.trySetException(databaseError.toException());
        }
    }

    private class VideoValueEventListener implements ValueEventListener {
        private TaskCompletionSource<List<Video>> dbSource;
        private List<Video> videoList = new ArrayList<>();

        private VideoValueEventListener(TaskCompletionSource<List<Video>> dbSource) {
            this.dbSource = dbSource;
        }

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            for (DataSnapshot snap : dataSnapshot.getChildren()) {
                Video video = snap.getValue(Video.class);
                videoList.add(video);
            }
            dbSource.trySetResult(videoList);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            dbSource.trySetException(databaseError.toException());
        }
    }

    private class SelectedVideoValueEventListener implements ValueEventListener {
        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            if (dataSnapshot.exists()) {
                Video video = dataSnapshot.getValue(Video.class);
                String videoId = dataSnapshot.getKey();
                if (video != null) {
                    int index = mItemIds.indexOf(videoId);
                    if (index > -1) {
                        mItemIds.set(index, videoId);
                        mItemList.set(index, video);
                    }
                } else {
                    int index = mItemIds.indexOf(videoId);
                    if (index > -1) {
                        // Remove data from the list
                        mItemIds.remove(index);
                        mItemList.remove(index);
                    }
                }

                Log.d(TAG, "video changed");
                setValue(mItemList);
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) { }
    }
}

