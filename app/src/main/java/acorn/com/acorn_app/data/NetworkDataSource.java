/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package acorn.com.acorn_app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.algolia.search.saas.Client;
import com.algolia.search.saas.Index;
import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.Driver;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.Comment;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.models.dbArticle;
import acorn.com.acorn_app.services.DownloadArticlesJobService;
import acorn.com.acorn_app.services.RecArticlesJobService;
import acorn.com.acorn_app.services.RecDealsJobService;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.HtmlUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class NetworkDataSource {
    private static final String TAG = "NetworkDataSource";
    private static final String REC_ARTICLES_TAG = "recommendedArticles";
    private static final String REC_DEALS_TAG = "recommendedDeals";
    private static final String DOWNLOAD_ARTICLES_TAG = "downloadArticles";

    public static final String ARTICLE_REF = "article";
    public static final String VIDEO_REF = "video";
    public static final String COMMENT_REF = "comment";
    public static final String USER_REF = "user";
    public static final String SEARCH_REF = "search";
    public static final String PREFERENCES_REF = "preference";
    public static final String NOTIFICATION_TOKENS = "notificationTokens";
    public static final String COMMENTS_NOTIFICATION = "commentsNotificationValue";
    public static final String REC_ARTICLES_NOTIFICATION = "recArticlesNotificationValue";
    public static final String REC_DEALS_NOTIFICATION = "recDealsNotificationValue";
    public static final String ALGOLIA_API_KEY_REF = "algoliaApiKey";
    public static final String YOUTUBE_API_KEY_REF = "youtubeApiKey";
    public static final String REPORT_REF = "reported";

    // Recommended articles
    public static List<Article> mRecArticleList;

    // Saved articles lists
    private List<String> mSavedArticlesIdList = new ArrayList<>();
    private List<Query> mSavedArticlesQueryList = new ArrayList<>();

    // Algolia
    public static String ALGOLIA_API_KEY;
    private static Client mAlgoliaClient;
    private static Index mAlgoliaIndex;

    // For Singleton instantiation
    private static final Object LOCK = new Object();
    private static NetworkDataSource sInstance;
    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private final DatabaseReference mDatabaseReference;
    private final AppExecutors mExecutors;

    // Theme
    private String mThemeSearchKey;
    private String mThemeSearchFilter;

    private NetworkDataSource(Context context, AppExecutors executors) {
        mContext = context;
        mExecutors = executors;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mDatabaseReference = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Get the singleton for this class
     */
    public static NetworkDataSource getInstance(Context context, AppExecutors executors) {
        if (sInstance == null) {
            synchronized (LOCK) {
                sInstance = new NetworkDataSource(context.getApplicationContext(), executors);
            }
        }
        return sInstance;
    }

    public ArticleListLiveData getArticles(FbQuery query) {
        DatabaseReference ref = mDatabaseReference.child(query.dbRef);
        Query tempQuery = ref.orderByChild(query.orderByChild);
        if (!query.strStartAt.equals("")) {
            tempQuery = tempQuery.startAt(query.strStartAt).limitToFirst(query.limit);
        } else if (query.numStartAt != Long.MAX_VALUE) {
            tempQuery = tempQuery.startAt(query.numStartAt).limitToFirst(query.limit);
        } else if (!query.strEqualTo.equals("")) {
            tempQuery = tempQuery.equalTo(query.strEqualTo).limitToFirst(50);
        } else if (query.numEqualTo != Long.MAX_VALUE) {
            tempQuery = tempQuery.equalTo(query.numEqualTo).limitToFirst(50);
        } else {
            tempQuery = tempQuery.limitToFirst(query.limit);
        }
        tempQuery.keepSynced(true);

        return new ArticleListLiveData(tempQuery);
    }

    public ArticleListLiveData getSavedArticles(FbQuery query) {
        Query tempQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems");
        return new ArticleListLiveData(tempQuery, query.state, query.limit, query.numStartAt.intValue());
    }

    public ArticleListLiveData getAdditionalArticles(FbQuery query, Object index, int indexType) {
        if (query.state != 2) {
            DatabaseReference ref = mDatabaseReference.child(query.dbRef);
            Query tempQuery = ref.orderByChild(query.orderByChild)
                    .limitToFirst(query.limit + 1);
            if (indexType == 0) {
                if (index instanceof String) tempQuery = tempQuery.startAt((String) index);
                if (index instanceof Number)
                    tempQuery = tempQuery.startAt(((Number) index).longValue());
            } else if (indexType == 1) {
                if (index instanceof String) tempQuery = tempQuery.equalTo((String) index);
                if (index instanceof Number)
                    tempQuery = tempQuery.equalTo(((Number) index).longValue());
            }
            return new ArticleListLiveData(tempQuery);
        } else {
            Query tempQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems");
            query.numStartAt = ((Number) index).longValue();
            return new ArticleListLiveData(tempQuery, query.state, query.limit, query.numStartAt.intValue());
        }
    }

    public void getTrendingData(Runnable bindToUi) {
        mExecutors.networkIO().execute(() -> {
            StringBuilder searchKeyBuilder = new StringBuilder();
            StringBuilder filterStringBuilder = new StringBuilder();

            String[] themePrefs = mContext.getResources().getStringArray(R.array.theme_array);
            Arrays.sort(themePrefs);
            for (int i = 0; i < themePrefs.length; i++) {
                if (i == 0) {
                    searchKeyBuilder.append(themePrefs[i]);
                    filterStringBuilder.append("mainTheme: \"").append(themePrefs[i]).append("\"");
                } else {
                    searchKeyBuilder.append("_").append(themePrefs[i]);
                    filterStringBuilder.append(" OR mainTheme: \"").append(themePrefs[i]).append("\"");
                }
            }
            String themeSearchKey = searchKeyBuilder.toString();
            String themeSearchFilter = filterStringBuilder.toString();

            DatabaseReference resultRef = mDatabaseReference.child(SEARCH_REF).child(themeSearchKey);
            resultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() == null) {
                        searchThemeArticles(themeSearchKey, themeSearchFilter, bindToUi);
                    } else {
                        Long timeNow = (new Date().getTime());
                        Long lastQueryTimestamp = (Long) dataSnapshot.child("lastQueryTimestamp").getValue();
                        if (lastQueryTimestamp == null) {
                            searchThemeArticles(themeSearchKey, themeSearchFilter, bindToUi);
                        } else {
                            if (timeNow - lastQueryTimestamp < 60L * 60L * 1000L) { // 1 hour
                                mExecutors.mainThread().execute(bindToUi);
                            } else {
                                searchThemeArticles(themeSearchKey, themeSearchFilter, bindToUi);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
        });
    }

    public void getThemeData(Runnable bindToUi) {
        mExecutors.networkIO().execute(() -> {
            mThemeSearchKey = mSharedPrefs.getString("themeSearchKey", "");
            mThemeSearchFilter = mSharedPrefs.getString("themeSearchFilter", "");
            DatabaseReference resultRef = mDatabaseReference.child(SEARCH_REF).child(mThemeSearchKey);
            resultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() == null) {
                        searchThemeArticles(mThemeSearchKey, mThemeSearchFilter, bindToUi);
                    } else {
                        Long timeNow = (new Date().getTime());
                        Long lastQueryTimestamp = (Long) dataSnapshot.child("lastQueryTimestamp").getValue();
                        if (lastQueryTimestamp == null) {
                            searchThemeArticles(mThemeSearchKey, mThemeSearchFilter, bindToUi);
                        } else {
                            if (timeNow - lastQueryTimestamp < 3L * 60L * 60L * 1000L) { // 3 hours
                                mExecutors.mainThread().execute(bindToUi);
                            } else {
                                searchThemeArticles(mThemeSearchKey, mThemeSearchFilter, bindToUi);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
        });
    }

    private void searchThemeArticles(String themeSearchKey, String themeSearchFilter, Runnable bindToUi) {
        mExecutors.networkIO().execute(() -> {
            DatabaseReference resultRef = mDatabaseReference.child(SEARCH_REF).child(themeSearchKey);
            com.algolia.search.saas.Query query = new com.algolia.search.saas.Query();
            //        query.setFacets("*");
            query.setFilters(themeSearchFilter);

            setupAlgoliaClient(() -> {
                if (mAlgoliaIndex == null) return;
                mAlgoliaIndex.searchAsync(query, (jsonObject, e) -> {
                    if (jsonObject == null) return;

                    String jsonString = jsonObject.toString();
                    Map<String, Object> jsonMap = new Gson().fromJson(
                            jsonString,
                            new TypeToken<HashMap<String, Object>>() {
                            }.getType());

                    resultRef.setValue(jsonMap).addOnSuccessListener(aVoid -> {
                        resultRef.child("lastQueryTimestamp").setValue(new Date().getTime());
                        mExecutors.mainThread().execute(bindToUi);
                    });
                });
            });
        });
    }

    public void getDealsData(Runnable bindToUi) {
        mExecutors.networkIO().execute(() -> {
            String dealsSearchKey = "Deals";
            String dealsSearchFilter = "mainTheme: Deals";
            DatabaseReference resultRef = mDatabaseReference.child(SEARCH_REF).child("Deals");
            resultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() == null) {
                        searchThemeArticles(dealsSearchKey, dealsSearchFilter, bindToUi);
                    } else {
                        Long timeNow = (new Date().getTime());
                        Long lastQueryTimestamp = (Long) dataSnapshot.child("lastQueryTimestamp").getValue();
                        if (lastQueryTimestamp == null) {
                            searchThemeArticles(dealsSearchKey, dealsSearchFilter, bindToUi);
                        } else {
                            if (timeNow - lastQueryTimestamp < 3L * 60L * 60L * 1000L) { // 3 hours
                                mExecutors.mainThread().execute(bindToUi);
                            } else {
                                searchThemeArticles(dealsSearchKey, dealsSearchFilter, bindToUi);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
        });
    }

    // Trending Articles
    public void getTrendingArticles(Runnable onComplete, Runnable onError) {
        int limit = 50;
        ArticleRoomDatabase roomDb = ArticleRoomDatabase.getInstance(mContext);
        mExecutors.networkIO().execute(() -> {
            Query query = mDatabaseReference.child(ARTICLE_REF).orderByKey()
                    .limitToFirst(limit);
            query.keepSynced(true);
            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot snap : dataSnapshot.getChildren()) {
                        Article article = snap.getValue(Article.class);
                        if (article == null) return;

                        mExecutors.diskWrite().execute(() -> {
                            Log.d(TAG, "article: " + article.getTitle());
                            article.htmlContent = HtmlUtils.getCleanedHtml(mContext, article.getLink());
                            dbArticle localArticle = new dbArticle(mContext, article);
                            roomDb.articleDAO().insert(localArticle);
                        });
                    }
                    onComplete.run();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    onError.run();
                }
            };
            query.addValueEventListener(listener);
        });
    }

    public void scheduleArticlesDownload() {
        Driver driver = new GooglePlayDriver(mContext);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);

        Job downloadArticlesJob = dispatcher.newJobBuilder()
                .setService(DownloadArticlesJobService.class)
                .setTag(DOWNLOAD_ARTICLES_TAG)
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(
                        (int) TimeUnit.MINUTES.toSeconds(5), //Testing
                        (int) TimeUnit.MINUTES.toSeconds(6))) //Testing
//                        (int) TimeUnit.HOURS.toSeconds(3), //Start every 3 hours
//                        (int) TimeUnit.HOURS.toSeconds(4))) //Execute within 4 hours
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setReplaceCurrent(true)
                .build();

        // Schedule the Job with the dispatcher
        dispatcher.mustSchedule(downloadArticlesJob);
    }

    public void cancelDownloadArticles() {
        Driver driver = new GooglePlayDriver(mContext);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);
        dispatcher.cancel(DOWNLOAD_ARTICLES_TAG);
    }

    // Recommended Deals
    public void getRecommendedDeals(Consumer<List<Article>> onSuccess) {
        int limit = 3;
        mExecutors.networkIO().execute(() -> {
            List<Article> recDealList = new ArrayList<>();
            Query query = mDatabaseReference.child(SEARCH_REF)
                    .child("Deals")
                    .child("hits")
                    .orderByChild("trendingIndex")
                    .limitToFirst(limit);
            ChildEventListener listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    Article article = dataSnapshot.getValue(Article.class);
                    recDealList.add(article);
                    if (recDealList.size() == limit) {
                        onSuccess.accept(recDealList);
                        query.removeEventListener(this);
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) { }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            };
            query.addChildEventListener(listener);
        });
    }
    
    public void scheduleRecDealsPush() {
        Driver driver = new GooglePlayDriver(mContext);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);

        Job recDealsJob = dispatcher.newJobBuilder()
                .setService(RecDealsJobService.class)
                .setTag(REC_DEALS_TAG)
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(
//                        (int) TimeUnit.MINUTES.toSeconds(1), //Testing
//                        (int) TimeUnit.MINUTES.toSeconds(2))) //Testing
                        (int) TimeUnit.HOURS.toSeconds(8), //Start every 8 hours
                        (int) TimeUnit.HOURS.toSeconds(9))) //Execute within 9 hours
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setReplaceCurrent(true)
                .build();

        // Schedule the Job with the dispatcher
        dispatcher.mustSchedule(recDealsJob);
        recordLastRecDealsScheduleTime();
    }

    public void cancelRecDealsPush() {
        Driver driver = new GooglePlayDriver(mContext);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);
        dispatcher.cancel(REC_DEALS_TAG);
        mDatabaseReference.child(USER_REF).child(mUid)
                .child("lastRecDealsScheduleTime").setValue(0);
    }

    public void recordLastRecDealsPushTime() {
        Long lastRecArticlesPushTime = (new Date()).getTime();
        if (mUid == null) mUid = mSharedPrefs.getString("uid", "");
        mDatabaseReference.child(USER_REF).child(mUid)
                .child("lastRecDealsPushTime").setValue(lastRecArticlesPushTime);
        mSharedPrefs.edit().putLong("lastRecDealsPushTime", lastRecArticlesPushTime).apply();
    }

    public void recordLastRecDealsScheduleTime() {
        if (mUid == null) mUid = mSharedPrefs.getString("uid", "");
        mDatabaseReference.child(USER_REF).child(mUid)
                .child("lastRecDealsScheduleTime").setValue((new Date()).getTime());
    }


    // Recommended Articles
    public void getRecommendedArticles(String hitsRef, Runnable onSuccess) {
        int limit = 3;
        mExecutors.networkIO().execute(() -> {
            mRecArticleList = new ArrayList<>();
            Query query = mDatabaseReference.child(hitsRef)
                    .orderByChild("trendingIndex")
                    .limitToFirst(limit);
            ChildEventListener listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    Article article = dataSnapshot.getValue(Article.class);
                    mRecArticleList.add(article);
                    if (mRecArticleList.size() == limit) {
                        onSuccess.run();
                        query.removeEventListener(this);
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) { }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            };
            query.addChildEventListener(listener);
        });
    }

    public void scheduleRecArticlesPush() {
        Driver driver = new GooglePlayDriver(mContext);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);

        Job recArticlesJob = dispatcher.newJobBuilder()
                .setService(RecArticlesJobService.class)
                .setTag(REC_ARTICLES_TAG)
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(
//                        (int) TimeUnit.MINUTES.toSeconds(1), //Testing
//                        (int) TimeUnit.MINUTES.toSeconds(2))) //Testing
                        (int) TimeUnit.HOURS.toSeconds(6), //Start every 6 hours
                        (int) TimeUnit.HOURS.toSeconds(7))) //Execute within 7 hours
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setReplaceCurrent(true)
                .build();

        // Schedule the Job with the dispatcher
        dispatcher.mustSchedule(recArticlesJob);
        recordLastRecArticlesScheduleTime();
    }

    public void cancelRecArticlesPush() {
        Driver driver = new GooglePlayDriver(mContext);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);
        dispatcher.cancel(REC_ARTICLES_TAG);
        mDatabaseReference.child(USER_REF).child(mUid)
                .child("lastRecArticlesScheduleTime").setValue(0);
    }

    public void recordLastRecArticlesPushTime() {
        Long lastRecArticlesPushTime = (new Date()).getTime();
        if (mUid == null) mUid = mSharedPrefs.getString("uid", "");
        mDatabaseReference.child(USER_REF).child(mUid)
                .child("lastRecArticlesPushTime").setValue(lastRecArticlesPushTime);
        mSharedPrefs.edit().putLong("lastRecArticlesPushTime", lastRecArticlesPushTime).apply();
    }

    public void recordLastRecArticlesScheduleTime() {
        if (mUid == null) mUid = mSharedPrefs.getString("uid", "");
        mDatabaseReference.child(USER_REF).child(mUid)
                .child("lastRecArticlesScheduleTime").setValue((new Date()).getTime());
    }


    // Algolia
    public void setupAlgoliaClient(@Nullable Runnable onComplete) {
        mDatabaseReference.child(ALGOLIA_API_KEY_REF).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    ALGOLIA_API_KEY = dataSnapshot.getValue(String.class);
                    mAlgoliaClient = new Client("O96PPLSF19", ALGOLIA_API_KEY);
                    mAlgoliaIndex = mAlgoliaClient.getIndex("article");

                    if (onComplete != null) onComplete.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
    }


    // Youtube Key
    public void getYoutubeApiKey(Consumer<String> onComplete) {
        mDatabaseReference.child(YOUTUBE_API_KEY_REF).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String apiKey = dataSnapshot.getValue(String.class);
                    onComplete.accept(apiKey);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    // Videos
    public VideoListLiveData getVideos(String orderByChild, @Nullable String startAt, int limit) {
        DatabaseReference ref = mDatabaseReference.child(VIDEO_REF);
        Query query = ref.orderByChild(orderByChild);
        if (startAt != null) {
            query = query.startAt(startAt).limitToFirst(limit + 1);
        } else {
            query = query.limitToFirst(limit);
        }
        query.keepSynced(true);

        return new VideoListLiveData(query);
    }




    // Reporting
    public void reportPost(Article post) {
        mDatabaseReference.child(ARTICLE_REF).child(post.getObjectID()).child("isReported")
                .setValue(true);

        DatabaseReference reportRef = mDatabaseReference.child(REPORT_REF);
        reportRef.child("article").child(post.getObjectID()).setValue(post.getPostAuthorUid());

        reportRef.child("user").child(post.getPostAuthorUid()).addListenerForSingleValueEvent(
                new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Integer count = dataSnapshot.getValue(Integer.class);

                if (count != null) {
                    reportRef.child("user").child(post.getPostAuthorUid()).setValue(count + 1);
                } else {
                    reportRef.child("user").child(post.getPostAuthorUid()).setValue(1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
    }

    public void reportComment(String articleId, Comment comment) {
        mDatabaseReference.child(COMMENT_REF).child(articleId).child(comment.getCommentId())
                .child("isReported").setValue(true);

        DatabaseReference reportRef = mDatabaseReference.child(REPORT_REF);
        reportRef.child("comment").child(articleId).child(comment.getCommentId())
                .setValue(comment.getUid());

        reportRef.child("user").child(comment.getUid()).addListenerForSingleValueEvent(
                new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Integer count = dataSnapshot.getValue(Integer.class);

                if (count != null) {
                    reportRef.child("user").child(comment.getUid()).setValue(count + 1);
                } else {
                    reportRef.child("user").child(comment.getUid()).setValue(1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
    }


    // Remove saved article
    public void removeSavedArticle(String articleId, ArticleListLiveData articleListLD) {
        DatabaseReference articleRef = FirebaseDatabase.getInstance()
                .getReference("article/"+articleId);
        articleRef.removeEventListener(
                articleListLD.savedArticlesQueryList.get(articleRef));
        articleListLD.savedArticlesQueryList.remove(articleRef);

        articleRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Article article = mutableData.getValue(Article.class);
                if (article == null) {

                    return Transaction.success(mutableData);
                }

                int currentSaveCount = article.getSaveCount() == null ? 0 : article.getSaveCount();

                article.savers.remove(mUid);
                article.setSaveCount(currentSaveCount - 1);

                article.changedSinceLastJob = true;
                mutableData.setValue(article);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });

        // Update user with save data
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("user/"+mUid);

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {
                    return Transaction.success(mutableData);
                }

                int currentSaveCount = user.getSavedItemsCount() == null ? 0 : user.getSavedItemsCount();

                user.savedItems.remove(articleId);
                user.setSavedItemsCount(currentSaveCount - 1);
                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });
    }


    // Record open article
    public void recordArticleOpenDetails(Article article) {
        Long timeNow = new Date().getTime();
        DatabaseReference articleRef = FirebaseDatabase.getInstance()
                .getReference("article/"+article.getObjectID());

        articleRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Article article = mutableData.getValue(Article.class);
                if (article == null) {
                    return Transaction.success(mutableData);
                }

                int currentOpenCount = article.getOpenCount() == null ? 0 : article.getOpenCount();

                article.openedBy.put(mUid, timeNow);
                article.setOpenCount(currentOpenCount + 1);

                article.changedSinceLastJob = true;
                mutableData.setValue(article);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });

        // Update user with open article data
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("user/"+mUid);

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {
                    return Transaction.success(mutableData);
                }
                int themeOpenedCount = user.openedThemes.get(article.getMainTheme()) == null ?
                        0 : user.openedThemes.get(article.getMainTheme());

                user.openedArticles.put(article.getObjectID(), timeNow);
                user.openedThemes.put(article.getMainTheme(), themeOpenedCount + 1);

                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });
    }

    public void recordArticleOpenDetails(String articleId, String mainTheme) {
        Long timeNow = new Date().getTime();
        DatabaseReference articleRef = FirebaseDatabase.getInstance()
                .getReference("article/"+articleId);

        articleRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Article article = mutableData.getValue(Article.class);
                if (article == null) {
                    return Transaction.success(mutableData);
                }

                int currentOpenCount = article.getOpenCount() == null ? 0 : article.getOpenCount();

                article.openedBy.put(mUid, timeNow);
                article.setOpenCount(currentOpenCount + 1);

                article.changedSinceLastJob = true;
                mutableData.setValue(article);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });

        // Update user with save data
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("user/"+mUid);

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {
                    return Transaction.success(mutableData);
                }

                int themeOpenedCount = user.openedThemes.get(mainTheme);

                user.openedArticles.put(articleId, timeNow);
                user.openedThemes.put(mainTheme, themeOpenedCount + 1);

                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });
    }


    // Set user email verified
    public void setUserEmailVerified() {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("user/"+mUid);

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                User user = mutableData.getValue(User.class);
                if (user == null) {
                    return Transaction.success(mutableData);
                }

                user.isEmailVerified = true;

                mutableData.setValue(user);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });
    }


    // Update notifications preference values
    public void ToggleCommentsNotifications(boolean toReceive) {
        if (!toReceive) {
            mDatabaseReference.child(PREFERENCES_REF).child(COMMENTS_NOTIFICATION).child(mUid).setValue(toReceive);
        } else {
            mDatabaseReference.child(PREFERENCES_REF).child(COMMENTS_NOTIFICATION).child(mUid).removeValue();
        }
    }

    public void ToggleRecArticlesNotifications(boolean toReceive) {
        if (!toReceive) {
            mDatabaseReference.child(PREFERENCES_REF).child(REC_ARTICLES_NOTIFICATION).child(mUid).setValue(toReceive);
        } else {
            mDatabaseReference.child(PREFERENCES_REF).child(REC_ARTICLES_NOTIFICATION).child(mUid).removeValue();
        }
    }

    public void ToggleDealsNotifications(boolean toReceive) {
        if (!toReceive) {
            mDatabaseReference.child(PREFERENCES_REF).child(REC_DEALS_NOTIFICATION).child(mUid).setValue(toReceive);
        } else {
            mDatabaseReference.child(PREFERENCES_REF).child(REC_DEALS_NOTIFICATION).child(mUid).removeValue();
        }
    }
}