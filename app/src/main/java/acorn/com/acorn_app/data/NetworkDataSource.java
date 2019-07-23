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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.location.Location;
import android.preference.PreferenceManager;

import acorn.com.acorn_app.models.Address;
import acorn.com.acorn_app.models.MrtStation;
import acorn.com.acorn_app.models.PremiumStatus;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.utils.DateUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.Display;

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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.common.geometry.S1Angle;
import com.google.common.geometry.S2Cap;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2CellUnion;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2Point;
import com.google.common.geometry.S2RegionCoverer;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    public static final String ADDRESS_REF = "address";
    public static final String PREFERENCES_REF = "preference";
    public static final String NOTIFICATION_TOKENS = "notificationTokens";
    public static final String API_KEY_REF = "api";
    public static final String ALGOLIA_API_KEY_REF = "algoliaKey";
    public static final String YOUTUBE_API_KEY_REF = "youtubeKey";
    public static final String MAPS_API_KEY_REF = "mapsKey";
    public static final String REPORT_REF = "reported";
    public static final String VIDEOS_IN_FEED_PREF_REF = "videosInFeed";

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

    public ArticleListLiveData getArticles(FbQuery query, List<String> themeList) {
        Query articleQuery;
        switch (query.state) {
            case -2: // source
            case -1: // mainTheme
            case 3: // search
                articleQuery = mDatabaseReference.child(query.dbRef)
                        .orderByChild(query.orderByChild)
                        .limitToFirst(query.limit);
//                articleQuery.keepSynced(true);
                return new ArticleListLiveData(articleQuery, themeList);
            case 2: // saved
                articleQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems")
                        .orderByKey()
                        .limitToFirst(query.limit);
//                articleQuery.keepSynced(true);
                return new ArticleListLiveData(articleQuery, query.state);
            default:
                articleQuery = mDatabaseReference.child(query.dbRef)
                        .orderByChild(query.orderByChild)
                        .limitToFirst(query.limit);
//                articleQuery.keepSynced(true);
                return new ArticleListLiveData(articleQuery, themeList);
        }
    }

    private void filterNearbyArticles(Map<String, Long> articleIds, Consumer<List<Article>> onComplete) {
        filterNearbyArticles(articleIds, false, 50, onComplete);
    }

    private void filterNearbyArticles(Map<String, Long> articleIds, boolean weekOnly, int limit,
                                      Consumer<List<Article>> onComplete) {
        if (articleIds.size() == 0) {
            onComplete.accept(new ArrayList<>());
            return;
        }

        List<Article> articles = new ArrayList<>();

        Map<String, Long> datedArticleIds = new HashMap<>();
        List<String> undatedArticleIds = new ArrayList<>();
        for (Map.Entry<String, Long> entry : articleIds.entrySet()) {
            if (entry.getValue() > 1) {
                long now = (new Date()).getTime();
                long absDiff = Math.abs(now - entry.getValue());
                if (weekOnly) {
                    try {
                        long weekAgoMidnight = DateUtils.getWeekAgoMidnight();
                        long weekLaterMidnight = DateUtils.getWeekLaterMidnight();
                        if (entry.getValue() >= weekAgoMidnight && entry.getValue() < weekLaterMidnight) {
                            datedArticleIds.put(entry.getKey(), absDiff);
                        }
                    } catch (Exception e) {
                        Log.d(TAG, e.getLocalizedMessage());
                    }
                } else {
                    datedArticleIds.put(entry.getKey(), absDiff);
                }
            } else {
                undatedArticleIds.add(entry.getKey());
            }
        }

        // sort datedArticles by proximity to today
        Map<String, Long> sortedDatedArticles = datedArticleIds.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
        Log.d(TAG, sortedDatedArticles.toString());
        List<String> sortedDatedArticleIds = new ArrayList<>(sortedDatedArticles.keySet());

        // dated articles will go on top, undated articles will be randomised
        Collections.shuffle(undatedArticleIds);
        List<String> orderedArticleIds = new ArrayList<>(sortedDatedArticleIds);
        orderedArticleIds.addAll(undatedArticleIds);

        int articleLimit = Math.min(limit, articleIds.size());
        List<String> doneList = new ArrayList<>();
        for (int i = 0; i < articleLimit; i++) {
            String articleId = orderedArticleIds.get(i);
            Query query = mDatabaseReference.child(ARTICLE_REF).child(articleId);
            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Article article = dataSnapshot.getValue(Article.class);
                    if (article != null) {
                        //Log.d(TAG, "title: " + article.getTitle());
                        articles.add(article);
                        doneList.add(article.getObjectID());
                    } else {
                        doneList.add("failed to load article " + (doneList.size() + 1));
                    }

                    if (doneList.size() >= articleLimit) {
                        onComplete.accept(articles);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            });
        }
    }

    public ArticleListLiveData getSavedArticles(FbQuery query) {
        Query savedItemsQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems");
        return new ArticleListLiveData(savedItemsQuery, query.state);
    }

    public void getNearbyArticles(double lat, double lng, double radius,
                                  Consumer<List<Article>> onComplete) {
        getNearbyArticles(lat, lng, radius, false, 50, onComplete);
    }

    public void getNearbyArticles(double lat, double lng, double radius, boolean weekOnly,
                                  int limit, Consumer<List<Article>> onComplete) {
        // get sphere cap centred at location
        S2Point point = S2LatLng.fromDegrees(lat, lng).toPoint();
        S1Angle angle = S1Angle.radians(radius / S2LatLng.EARTH_RADIUS_METERS);
        S2Cap cap = S2Cap.fromAxisAngle(point, angle);

        // get covering cell ids
        S2RegionCoverer coverer = new S2RegionCoverer();
        coverer.setMaxCells(5);
        S2CellUnion covering = coverer.getInteriorCovering(cap);

        // get all addresses and associated articles in covering cells
        Map<String, Long> articleIds = new HashMap<>();
        List<Long> doneList = new ArrayList<>();
        for (S2CellId id : covering) {
            String minRange = String.valueOf(id.rangeMin().id());
            String maxRange = String.valueOf(id.rangeMax().id());
            Log.d(TAG, "minRange: " + minRange + ", maxRange: " + maxRange);
            Query addressQuery = mDatabaseReference.child(ADDRESS_REF)
                    .orderByKey().startAt(minRange).endAt(maxRange);

            addressQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        //Log.d(TAG, "address count: " + dataSnapshot.getChildrenCount());
                        for (DataSnapshot snap : dataSnapshot.getChildren()) {
                            Address address = snap.getValue(Address.class);
                            if (address != null) {
                                for (String id : address.article.keySet()) {
                                    Long reminderDate = address.article.get(id);
                                    Long cutoff = DateUtils.getFourteenDaysAgoMidnight();
                                    if (reminderDate != null) {
                                        if ((cutoff != null && reminderDate > cutoff) || reminderDate == 1L) {
                                            /*
                                            these are all the articles with no reminderDate or
                                            reminderDate greater than thirty dates ago,
                                            i.e. first date appearing in title is greater than 30 days
                                            ago. this is so we avoid removing articles with
                                            dates from x to y where reminder date is x - 1 but
                                            event is still valid as y has not reached. the implication
                                            is that there will be deals/events that expired up to
                                            30 days ago
                                            */
                                            articleIds.put(id, reminderDate);
                                        } else {
                                            // remove all events that expired more than 30 days ago
                                            Log.d(TAG, "addressId: " + address.objectID + ", reminderDate: " + reminderDate);
                                            mDatabaseReference.child(ADDRESS_REF).child(address.objectID)
                                                    .child("article").child(id).removeValue();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    doneList.add(id.id());

                    if (doneList.size() >= covering.size()) {
                        Log.d(TAG, "articleIds size: " + articleIds.size());
                        filterNearbyArticles(articleIds, weekOnly, limit, onComplete);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            });
        }
    }

    public ArticleListLiveData getAdditionalArticles(FbQuery query, Object index, int indexType, List<String> themeList) {
        Query articleQuery;
        switch (query.state) {
            case -2: // source
            case -1: // mainTheme
            case 3: // search
                articleQuery = mDatabaseReference.child(query.dbRef)
                        .orderByChild(query.orderByChild)
                        .limitToFirst(query.limit + 1);
                articleQuery = articleQuery.startAt((String) index);
                return new ArticleListLiveData(articleQuery, themeList);
            case 2: // saved
                articleQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems")
                        .orderByKey()
                        .limitToFirst(query.limit + 1);
                articleQuery = articleQuery.startAt((String) index);
                return new ArticleListLiveData(articleQuery, query.state);
            default:
                articleQuery = mDatabaseReference.child(query.dbRef)
                        .orderByChild(query.orderByChild)
                        .limitToFirst(query.limit + 1);
                if (indexType == 0) {
                    if (index instanceof String) articleQuery = articleQuery.startAt((String) index);
                    if (index instanceof Number)
                        articleQuery = articleQuery.startAt(((Number) index).longValue());
                } else if (indexType == 1) {
                    if (index instanceof String) articleQuery = articleQuery.equalTo((String) index);
                    if (index instanceof Number)
                        articleQuery = articleQuery.equalTo(((Number) index).longValue());
                }
                return new ArticleListLiveData(articleQuery, themeList);
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

    public void getSearchData(String searchKey, String searchFilter, Runnable bindToUi) {
        mExecutors.networkIO().execute(() -> {
            String cleanedSearchKey = searchKey.replaceAll("[.#$\\[\\]]", "");
            DatabaseReference resultRef = mDatabaseReference.child(SEARCH_REF).child(cleanedSearchKey);
            resultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() == null) {
                        searchThemeArticles(cleanedSearchKey, searchFilter, bindToUi);
                    } else {
                        Long timeNow = (new Date().getTime());
                        Long lastQueryTimestamp = (Long) dataSnapshot.child("lastQueryTimestamp").getValue();
                        if (lastQueryTimestamp == null) {
                            searchThemeArticles(cleanedSearchKey, searchFilter, bindToUi);
                        } else {
                            if (timeNow - lastQueryTimestamp < 3L * 60L * 60L * 1000L) { // 3 hours
                                mExecutors.mainThread().execute(bindToUi);
                            } else {
                                searchThemeArticles(cleanedSearchKey, searchFilter, bindToUi);
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

    // get saved articles reminders
    public void getSavedArticlesReminderData(Consumer<List<Article>> bindToUi) {
        mExecutors.networkIO().execute(() -> {
            Log.d(TAG, "getSavedArticlesReminderData");
            List<Article> reminderList = new ArrayList<>();
            if (mUid == null) mUid = mSharedPrefs.getString("uid", "");
            DatabaseReference userRef = mDatabaseReference.child(USER_REF).child(mUid).child("savedItems");
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Map<String, Long> savedItems = (Map<String, Long>) dataSnapshot.getValue();
                    if (savedItems == null || savedItems.size() == 0) {
                        return;
                    }

                    List<String> savedIdList = new ArrayList<>(savedItems.keySet());
                    List<String> doneList = new ArrayList<>();
                    for (String id : savedIdList) {
                        DatabaseReference articleRef = mDatabaseReference.child(ARTICLE_REF).child(id);
                        articleRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                doneList.add(id);
                                Article article = dataSnapshot.getValue(Article.class);
                                if (article != null && article.reminderDate != null) {
                                    Log.d(TAG, "reminderDate: " + article.reminderDate);
                                    if (DateUtils.getThisMidnight() != null) {
                                        if (article.reminderDate > DateUtils.getThisMidnight() &&
                                                article.reminderDate < DateUtils.getNextMidnight()) {
                                            reminderList.add(article);
                                        }
                                    }
                                }

                                if (doneList.size() >= savedIdList.size()) {
                                    bindToUi.accept(reminderList);
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
        });
    }

    // Download subscribed articles
    public void downloadSubscribedArticles(int imageWidth, Consumer<List<dbArticle>> onComplete, Runnable onError) {
        Log.d(TAG, "downloadSubscribedArticles started");

        List<dbArticle> articleList = new ArrayList<>();
        mExecutors.networkIO().execute(() -> {
            getTrendingData(() -> {
                mExecutors.networkIO().execute(() -> {
                    String themeSearchKey = mSharedPrefs.getString("themeSearchKey", "");
                    Log.d(TAG, themeSearchKey);
                    if (themeSearchKey.equals("")) { return; }
                    Query query = mDatabaseReference.child("search")
                            .child(themeSearchKey).child("hits").orderByChild("trendingIndex");
                    query.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot articlesSnap) {
                            Log.d(TAG, "articles fetched");
                            List<String> doneList = new ArrayList<>();
                            for (DataSnapshot snap : articlesSnap.getChildren()) {
                                Article algArticle = snap.getValue(Article.class);
                                if (algArticle == null) continue;

                                String articleId = algArticle.getObjectID();
                                mDatabaseReference.child(ARTICLE_REF).child(articleId)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                        doneList.add(snap.getKey());
                                        Article article = dataSnapshot.getValue(Article.class);
                                        if (article != null) {
                                            if (article.htmlContent != null && !article.htmlContent.equals("")) {
                                                article.htmlContent = HtmlUtils.cleanHtmlContent(article.htmlContent,
                                                        article.getLink(), article.selector, article.getObjectID(), imageWidth);
                                                dbArticle localArticle = new dbArticle(mContext, article);
                                                articleList.add(localArticle);
                                            }
                                        }

                                        if (doneList.size() >= articlesSnap.getChildrenCount()) {
                                            Log.d(TAG, "articlesDownloaded: " + articleList.size());
                                            onComplete.accept(articleList);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError databaseError) { }
                                });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            onError.run();
                        }
                    });
                });
            });
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
    public void getRecommendedArticles(String hitsRef, Consumer<List<Article>> onSuccess) {
        int limit = 3;
        mExecutors.networkIO().execute(() -> {
            List<Article> articleList = new ArrayList<>();
            Query query = mDatabaseReference.child(hitsRef)
                    .orderByChild("trendingIndex")
                    .limitToFirst(limit);
            ChildEventListener listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    Article article = dataSnapshot.getValue(Article.class);
                    articleList.add(article);
                    if (articleList.size() == limit) {
                        onSuccess.accept(articleList);
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
        mDatabaseReference.child(API_KEY_REF).child(ALGOLIA_API_KEY_REF).addListenerForSingleValueEvent(new ValueEventListener() {
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
        mDatabaseReference.child(API_KEY_REF).child(YOUTUBE_API_KEY_REF).addListenerForSingleValueEvent(new ValueEventListener() {
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

    // Maps Key
    public void getMapsApiKey(Consumer<String> onComplete) {
        mDatabaseReference.child(API_KEY_REF).child(MAPS_API_KEY_REF).addListenerForSingleValueEvent(new ValueEventListener() {
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


    public void getMrtStationByName(String locale, Consumer<Map<String, Map<String, Object>>> onComplete) {
        String name = locale + " MRT Station";
        Map<String, Map<String, Object>> mrtStationMap = new HashMap<>();
        Query mrtRef = mDatabaseReference.child("mrtStation").orderByChild("stationName").equalTo(name);
        mrtRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot snap : dataSnapshot.getChildren()) {
                        MrtStation station = snap.getValue(MrtStation.class);
                        if (station != null && mrtStationMap.get(station.stationLocale) == null) {
                            Map<String, Object> location = new HashMap<>();
                            location.put("latitude", station.latitude);
                            location.put("longitude", station.longitude);
                            location.put("geofence", station.geofence);
                            mrtStationMap.put(station.stationLocale, location);
                        }
                    }
                    onComplete.accept(mrtStationMap);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }

    // get mrt stations list
    public void getMrtStations(Consumer<Map<String, Map<String, Object>>> onComplete,
                               @Nullable Consumer<DatabaseError> onError) {
        Map<String, Map<String, Object>> mrtStationMap = new HashMap<>();
        Query mrtRef = mDatabaseReference.child("mrtStation").orderByChild("type").equalTo("MRT");
        mrtRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot snap : dataSnapshot.getChildren()) {
                        MrtStation station = snap.getValue(MrtStation.class);
                        if (station != null && mrtStationMap.get(station.stationLocale) == null) {
                            Map<String, Object> location = new HashMap<>();
                            location.put("latitude", station.latitude);
                            location.put("longitude", station.longitude);
                            location.put("geofence", station.geofence);
                            mrtStationMap.put(station.stationLocale, location);
                        }
                    }
                    onComplete.accept(mrtStationMap);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                if (onError != null) {
                    onError.accept(databaseError);
                }
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
    public void removeSavedArticle(String articleId) {
        DatabaseReference articleRef = mDatabaseReference.child("article/"+articleId);

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



    // Record open video
    public void recordVideoOpenDetails(Video video) {
        Long timeNow = new Date().getTime();
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("video/"+video.getObjectID());

        videoRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Video video = mutableData.getValue(Video.class);
                if (video == null) {
                    return Transaction.success(mutableData);
                }

                int currentViewCount = video.getViewCount() == null ? 0 : video.getViewCount();

                video.viewedBy.put(mUid, timeNow);
                video.setViewCount(currentViewCount + 1);

                video.changedSinceLastJob = true;
                mutableData.setValue(video);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
            }
        });

        // Update user with open video data
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
                user.viewedVideos.put(video.getObjectID(), timeNow);

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
    public void ToggleNotifications(String type, boolean toReceive) {
        if (!toReceive) {
            mDatabaseReference.child(PREFERENCES_REF).child(type).child(mUid).setValue(toReceive);
        } else {
            mDatabaseReference.child(PREFERENCES_REF).child(type).child(mUid).removeValue();
        }
    }

    public void setVideosInFeedPreference(boolean showVideos) {
        if (showVideos) {
            mDatabaseReference.child(PREFERENCES_REF).child(VIDEOS_IN_FEED_PREF_REF).child(mUid)
                    .child("showVideos").removeValue();
        } else {
            mDatabaseReference.child(PREFERENCES_REF).child(VIDEOS_IN_FEED_PREF_REF).child(mUid)
                    .child("showVideos").setValue(showVideos);
        }
    }

    public void setVideosInFeedPreference(String channel, boolean showFromChannel) {
        if (showFromChannel) {
            mDatabaseReference.child(PREFERENCES_REF).child(VIDEOS_IN_FEED_PREF_REF).child(mUid)
                    .child("channelsToRemove").child(channel).removeValue();
        } else {
            mDatabaseReference.child(PREFERENCES_REF).child(VIDEOS_IN_FEED_PREF_REF).child(mUid)
                    .child("channelsToRemove").child(channel).setValue((new Date()).getTime());
        }
    }


    // Set user referrer
    public void setReferrer(String uid, String referrer) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("/" + uid + "/referredBy", referrer);

        Log.d(TAG, "updates: " + updates);
        mDatabaseReference.child(USER_REF).updateChildren(updates);
    }


    // Get duplicate articles
    public void getDuplicateArticles(List<String> articleIds, Consumer<List<Article>> onComplete) {
        List<Task<Article>> taskList = new ArrayList<>();

        for (String id : articleIds) {
            TaskCompletionSource<Article> duplicateSource = new TaskCompletionSource<>();
            Task<Article> duplicateTask = duplicateSource.getTask();
            taskList.add(duplicateTask);

            Query query = mDatabaseReference.child(ARTICLE_REF).child(id);
            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Article article = dataSnapshot.getValue(Article.class);
                    if (article != null) {
                        duplicateSource.trySetResult(article);
                    } else {
                        duplicateSource.trySetException(new Exception("Article does not exist: " + id));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    duplicateSource.trySetException(databaseError.toException());
                }
            });
        }

        Tasks.whenAll(taskList).addOnSuccessListener(aVoid -> {
            List<Article> duplicatesList = new ArrayList<>();
            for (Task<Article> task : taskList) {
                duplicatesList.add(task.getResult());
            }
            onComplete.accept(duplicatesList);
        });
    }
}