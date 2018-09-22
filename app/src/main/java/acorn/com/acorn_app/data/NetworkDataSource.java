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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.services.RecArticlesJobService;
import acorn.com.acorn_app.utils.AppExecutors;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class NetworkDataSource {
    private static final String TAG = "NetworkDataSource";
    private static final String REC_ARTICLES_TAG = "recommendedArticles";

    public static final String ARTICLE_REF = "article";
    public static final String COMMENT_REF = "comment";
    public static final String USER_REF = "user";
    public static final String SEARCH_REF = "search";
    public static final String PREFERENCES_REF = "preference";
    public static final String NOTIFICATION_TOKENS = "notificationTokens";
    public static final String COMMENTS_NOTIFICATION = "commentsNotificationValue";

    // Recommended articles
    public static List<Article> mRecArticleList;

    // Saved articles lists
    private List<String> mSavedArticlesIdList = new ArrayList<>();
    private List<Query> mSavedArticlesQueryList = new ArrayList<>();

    // Algolia
    private static final Client ALGOLIA_CLIENT =
            new Client("O96PPLSF19", "3b42d937aceab4818e2377325c76abf1"); // TODO: fetch key from server
    private static final Index ALGOLIA_ARTICLE_INDEX = ALGOLIA_CLIENT.getIndex("article");

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
        Log.d(TAG, "Getting the network data source");
        if (sInstance == null) {
            synchronized (LOCK) {
                sInstance = new NetworkDataSource(context.getApplicationContext(), executors);
                Log.d(TAG, "Made new network data source");
            }
        }
        return sInstance;
    }

    public ArticleListLiveData getArticles(FbQuery query) {
        if (query.state != 2) {
            DatabaseReference ref = mDatabaseReference.child(query.dbRef);
            Query tempQuery = ref.orderByChild(query.orderByChild);
            if (!query.strStartAt.equals("")) {
                Log.d(TAG, "state: " + query.state + ", strStartAt: " + query.strStartAt);
                tempQuery = tempQuery.startAt(query.strStartAt).limitToFirst(query.limit);
            } else if (query.numStartAt != Long.MAX_VALUE) {
                Log.d(TAG, "state: " + query.state + ", numStartAt: " + query.numStartAt);
                tempQuery = tempQuery.startAt(query.numStartAt).limitToFirst(query.limit);
            } else if (!query.strEqualTo.equals("")) {
                Log.d(TAG, "state: " + query.state + ", strEqualTo: " + query.strEqualTo);
                tempQuery = tempQuery.equalTo(query.strEqualTo).limitToFirst(50);
            } else if (query.numEqualTo != Long.MAX_VALUE) {
                Log.d(TAG, "state: " + query.state + ", numEqualTo: " + query.numEqualTo);
                tempQuery = tempQuery.equalTo(query.numEqualTo).limitToFirst(50);
            } else {
                tempQuery = tempQuery.limitToFirst(query.limit);
            }
            tempQuery.keepSynced(true);

            return new ArticleListLiveData(tempQuery);
        } else {
            Query tempQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems");
            return new ArticleListLiveData(tempQuery, query.state, query.limit, query.numStartAt.intValue());
        }
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

    public void getThemeData(Runnable bindToUi) {
        mExecutors.networkIO().execute(() -> {
            mThemeSearchKey = mSharedPrefs.getString("themeSearchKey", "");
            mThemeSearchFilter = mSharedPrefs.getString("themeSearchFilter", "");
            Log.d(TAG, "mThemeSearchKey: " + mThemeSearchKey + ", mThemeSearchFilter: " + mThemeSearchFilter);
            DatabaseReference resultRef = mDatabaseReference.child(SEARCH_REF).child(mThemeSearchKey);
            resultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() == null) {
                        Log.d(TAG, "searchRef: " + mThemeSearchKey + " does not exist");
                        searchThemeArticles(mThemeSearchKey, mThemeSearchFilter, bindToUi);
                    } else {
                        Long timeNow = (new Date().getTime());
                        Long lastQueryTimestamp = (Long) dataSnapshot.child("lastQueryTimestamp").getValue();
                        if (lastQueryTimestamp == null) {
                            searchThemeArticles(mThemeSearchKey, mThemeSearchFilter, bindToUi);
                        } else {
                            Log.d(TAG, "timeNow: " + timeNow + ", lastQueryTimestamp: "
                                    + lastQueryTimestamp + ", diff: " + (timeNow - lastQueryTimestamp));
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
                    Log.d(TAG, "getThemeData: failed to get search reference");
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

            ALGOLIA_ARTICLE_INDEX.searchAsync(query, (jsonObject, e) -> {
                Log.d(TAG, jsonObject.toString());
                String jsonString = jsonObject.toString();
    //                    .replaceAll("(\".*?)\\.(.*?\":.*?)", "$1$2");
                Map<String, Object> jsonMap = new Gson().fromJson(
                        jsonString,
                        new TypeToken<HashMap<String, Object>>() {}.getType());

                resultRef.setValue(jsonMap).addOnSuccessListener(aVoid -> {
                    resultRef.child("lastQueryTimestamp").setValue(new Date().getTime());
                    mExecutors.mainThread().execute(bindToUi);
                });
            });
        });
    }

    public void getRecommendedArticles(String hitsRef, Runnable onSuccess) {
        mExecutors.networkIO().execute(() -> {
            mRecArticleList = new ArrayList<>();
            Query query = mDatabaseReference.child(hitsRef)
                    .orderByChild("trendingIndex")
                    .limitToFirst(5);
            ChildEventListener listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    Article article = dataSnapshot.getValue(Article.class);
                    mRecArticleList.add(article);
                    if (mRecArticleList.size() == 5) {
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
                .setConstraints(Constraint.ON_UNMETERED_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(
//                        (int) TimeUnit.MINUTES.toSeconds(1), //For testing
//                        (int) TimeUnit.MINUTES.toSeconds(2))) //For testing
                        (int) TimeUnit.HOURS.toSeconds(24), //Start every 24 hours
                        (int) TimeUnit.HOURS.toSeconds(25))) //Execute within 25 hours
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setReplaceCurrent(false)
                .setTag("RecArticlesPush")
                .build();

        // Schedule the Job with the dispatcher
        dispatcher.mustSchedule(recArticlesJob);
        Log.d(TAG, REC_ARTICLES_TAG + ": Job scheduled");
        recordLastRecArticlesScheduleTime();
    }

    public void cancelRecArticlesPush() {
        Driver driver = new GooglePlayDriver(mContext);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);
        dispatcher.cancel(REC_ARTICLES_TAG);
        Log.d(TAG, REC_ARTICLES_TAG + ": Job canceled");
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
}