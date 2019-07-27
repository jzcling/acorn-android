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

public class ArticleListLiveData extends LiveData<List<Article>> {
    private static final String TAG = "ArticleListLiveData";

    private final DatabaseReference mDatabaseReference = FirebaseDatabase.getInstance().getReference();

    private Query query;
    private List<Query> queryList;
    public Map<Query, ValueEventListener> savedArticlesQueryList = new HashMap<>();
    private Map<Query, ValueEventListener> algoliaArticlesQueryList = new HashMap<>();

    // States: 0 = Recent, 1 = Trending, 2 = Saved articles, 3 = Search, 4 = Deals
    // -1 = mainTheme, -2 = source
    private int state = 3;
    private List<Integer> searchStates = new ArrayList<>();

    private final MyChildEventListener childListener = new MyChildEventListener();

    private final List<String> mArticleIds = new ArrayList<>();
    private final List<Article> mArticleList = new ArrayList<>();
    private List<String> mThemeList = new ArrayList<>();
    private int mSeed = 1;

    private boolean listenerRemovePending = false;
    private final Handler handler = new Handler();
    private final Runnable removeListener = new Runnable() {
        @Override
        public void run() {
            if (state == 2){
                for (Query query : savedArticlesQueryList.keySet()) {
                    query.removeEventListener(savedArticlesQueryList.get(query));
                    Log.d(TAG, "listener removed");
                }
            } else if (searchStates.contains(state)) {
                for (Query query : algoliaArticlesQueryList.keySet()) {
                    query.removeEventListener(algoliaArticlesQueryList.get(query));
                }
            } else {
                query.removeEventListener(childListener);
            }
            Log.d(TAG, "all listeners removed");
            listenerRemovePending = false;
        }
    };

    public ArticleListLiveData(Query query, List<String> themeList, int seed) {
        this.query = query;
        this.mThemeList = themeList;
        this.mSeed = seed;
        this.searchStates.add(-2);
        this.searchStates.add(-1);
        this.searchStates.add(3);
    }

    public ArticleListLiveData(Query query, int state) {
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
                        List<String> articleIds = new ArrayList<>(mArticleIds);
                        for (DataSnapshot snap : dataSnapshot.getChildren()) {
                            articleIds.remove(snap.getKey());
                        }
                        if (articleIds.size() > 0) {
                            for (String id : articleIds) {
                                int index = mArticleIds.indexOf(id);
                                if (index > -1) {
                                    mArticleIds.remove(index);
                                    mArticleList.remove(index);
                                    Query articleQuery = mDatabaseReference.child("article/" + id);
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
                        Tasks.whenAll(taskList).addOnCompleteListener(task -> setValue(mArticleList));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        setValue(new ArrayList<>());
                    }
                });
            } else if (searchStates.contains(state)) { // Subscriptions, Trending, Deals
                TaskCompletionSource<List<Article>> articleSource = new TaskCompletionSource<>();
                Task<List<Article>> articleTask = articleSource.getTask();

                query.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<Task<Boolean>> taskList = new ArrayList<>();
                        if (dataSnapshot.exists()) {
                            for (DataSnapshot snap : dataSnapshot.getChildren()) {
                                Article article = snap.getValue(Article.class);
                                if (article != null) {
                                    String toLoadId = article.getObjectID();

                                    // show article from source that first published the news
//                                    if (article.duplicates.size() > 0) {
//                                        Long toLoadPubDate = article.getPubDate();
//                                        for (Map.Entry<String, Long> entry : article.duplicates.entrySet()) {
//                                            if (entry.getValue() > toLoadPubDate) {
//                                                // article was published before target article
//                                                // > because pubDates are all negative
//                                                toLoadId = entry.getKey();
//                                                toLoadPubDate = entry.getValue();
//                                            }
//                                        }
//                                    }

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
                                articleSource.trySetResult(mArticleList);
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        setValue(new ArrayList<>());
                    }
                });

                boolean showVideos = mSharedPreferences.getBoolean("videosInFeed", true);
                if (showVideos) {
                    TaskCompletionSource<List<Article>> videoSource = new TaskCompletionSource<>();
                    Task<List<Article>> videoTask = videoSource.getTask();
                    Long cutoffDate = -DateUtils.getThreeDaysAgoMidnight();
                    Query videoQuery = mDatabaseReference.child("video").orderByChild("pubDate").endAt(cutoffDate);
                    VideoValueEventListener videoValueListener = new VideoValueEventListener(videoSource);
                    videoQuery.addListenerForSingleValueEvent(videoValueListener);

                    Set<String> channelsToRemove = mSharedPreferences.getStringSet(
                            "videosInFeedChannelsToRemove", new ArraySet<>());


                    Tasks.whenAll(articleTask, videoTask).addOnSuccessListener(aVoid -> {
                        List<Article> videos = videoTask.getResult();
                        List<Article> selectedVideos = new ArrayList<>();
                        if (videos != null) {
                            for (Article video : videos) {
//                                Log.d(TAG, "videoId: " + video.getObjectID() + ", theme: " +
//                                        video.getMainTheme() + ", pubDate: " + video.getPubDate());
                                if (mThemeList.contains(video.getMainTheme()) &&
                                        !channelsToRemove.contains(video.getSource())) {
                                    selectedVideos.add(video);
                                }
                            }
                            Collections.shuffle(selectedVideos, new Random(mSeed));
                            int sizeLimit = Math.min(mArticleList.size() / 5, selectedVideos.size());
                            for (int i = 0; i < sizeLimit; i++) {
                                mArticleList.add((i + 1) * 5, selectedVideos.get(i));
                                mArticleIds.add((i + 1) * 5, selectedVideos.get(i).getObjectID());
                            }
                        }
                        setValue(mArticleList);
                    });
                } else {
                    Tasks.whenAll(articleTask).addOnSuccessListener(
                            aVoid -> setValue(mArticleList));
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

            if (!mArticleIds.contains(dataSnapshot.getKey())) {
                if (article != null && !article.isReported) {
                    mArticleIds.add(dataSnapshot.getKey());
                    mArticleList.add(article);
                }
            }
            setValue(mArticleList);
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildChanged");
            Article newArticle = dataSnapshot.getValue(Article.class);
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mArticleIds.indexOf(articleKey);
            if (articleIndex > -1) {
                if (newArticle != null) {
                    if (newArticle.isReported) {
                        mArticleIds.remove(articleIndex);
                        mArticleList.remove(articleIndex);
                    } else {
                        // Replace with the new data
                        mArticleList.set(articleIndex, newArticle);
                    }
                }
            }

            setValue(mArticleList);
        }

        @Override
        public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "onChildRemoved");
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mArticleIds.indexOf(articleKey);
            if (articleIndex > -1) {
                // Remove data from the list
                mArticleIds.remove(articleIndex);
                mArticleList.remove(articleIndex);
            }

            setValue(mArticleList);
        }

        @Override
        public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildMoved");
            Article movedArticle = dataSnapshot.getValue(Article.class);
            String articleKey = dataSnapshot.getKey();

            int oldIndex = mArticleIds.indexOf(articleKey);
            if (oldIndex > -1) {
                // Remove data from old position
                mArticleIds.remove(oldIndex);
                mArticleList.remove(oldIndex);

                // Add data in new position
                int newIndex = previousChildKey == null ? 0 : mArticleIds.indexOf(previousChildKey) + 1;
                mArticleIds.add(articleKey);
                mArticleList.add(newIndex, movedArticle);
            }

            setValue(mArticleList);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {

        }

    }

    private class MainFeedValueEventListener implements ValueEventListener {
        private TaskCompletionSource<Boolean> dbSource;

        private MainFeedValueEventListener(TaskCompletionSource<Boolean> dbSource) {
            this.dbSource = dbSource;
        }

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//            Log.d(TAG, "onDataChanged");
            if (dataSnapshot.exists()) {
                Article article = dataSnapshot.getValue(Article.class);
                String articleId = dataSnapshot.getKey();
                if (article != null) {
                    boolean duplicateExists = false;
                    if (article.duplicates.size() > 0) {
                        for (String id : article.duplicates.keySet()) {
                            if (mArticleIds.contains(id)) {
                                duplicateExists = true;
                            }
                        }
                    }

                    if (!duplicateExists) {
                        int index = mArticleIds.indexOf(articleId);
                        if (index > -1) {
                            mArticleIds.set(index, articleId);
                            mArticleList.set(index, article);
                        } else {
                            mArticleIds.add(articleId);
                            mArticleList.add(article);
                        }
                    } else {
                        int index = mArticleIds.indexOf(articleId);
                        if (index > -1) {
                            // Remove data from the list
                            mArticleIds.remove(index);
                            mArticleList.remove(index);
                        }
                    }
                } else {
                    int index = mArticleIds.indexOf(articleId);
                    if (index > -1) {
                        // Remove data from the list
                        mArticleIds.remove(index);
                        mArticleList.remove(index);
                    }
                }

                if (!dbSource.trySetResult(true)) {
                   setValue(mArticleList);
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
                    int index = mArticleIds.indexOf(articleId);
                    if (index > -1) {
                        mArticleIds.set(index, articleId);
                        mArticleList.set(index, article);
                    } else {
                        mArticleIds.add(articleId);
                        mArticleList.add(article);
                    }
                } else {
                    int index = mArticleIds.indexOf(articleId);
                    if (index > -1) {
                        // Remove data from the list
                        mArticleIds.remove(index);
                        mArticleList.remove(index);
                    }
                }

                if (!dbSource.trySetResult(true)) {
                    setValue(mArticleList);
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
        private TaskCompletionSource<List<Article>> dbSource;
        private List<Article> videoList = new ArrayList<>();

        private VideoValueEventListener(TaskCompletionSource<List<Article>> dbSource) {
            this.dbSource = dbSource;
        }

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            for (DataSnapshot snap : dataSnapshot.getChildren()) {
                Video video = snap.getValue(Video.class);
                videoList.add(video.toArticle());
            }
            dbSource.trySetResult(videoList);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            dbSource.trySetException(databaseError.toException());
        }
    }
}

