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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Address;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.Comment;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.models.TimeLog;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.models.dbAddress;
import acorn.com.acorn_app.models.dbArticle;
import acorn.com.acorn_app.models.dbStation;
import acorn.com.acorn_app.services.DownloadArticlesJobService;
import acorn.com.acorn_app.services.RecArticlesJobService;
import acorn.com.acorn_app.services.RecDealsJobService;
import acorn.com.acorn_app.ui.viewModels.UserViewModel;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;
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
    private static final String NOTIFICATION_REF = "notification";

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

    // Local DB
    private AddressRoomDatabase mAddressRoomDb;

//    private Handler mHandler = new Handler();

    private NetworkDataSource(Context context, AppExecutors executors) {
        mContext = context;
        mExecutors = executors;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mAddressRoomDb = AddressRoomDatabase.getInstance(context);
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

    public void getSingleArticle(String articleId, Consumer<Article> onComplete) {
        DatabaseReference ref = mDatabaseReference.child(ARTICLE_REF).child(articleId);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Article article = dataSnapshot.getValue(Article.class);
                    onComplete.accept(article);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
    }

    public FeedListLiveData getArticles(FbQuery query, List<String> themeList, @Nullable Integer seed) {
        Query articleQuery;
        switch (query.state) {
            case -2: // source
            case -1: // mainTheme
            case 3: // search
                articleQuery = mDatabaseReference.child(query.dbRef)
                        .orderByChild(query.orderByChild)
                        .limitToFirst(query.limit);
                return new FeedListLiveData(articleQuery, themeList, seed);
            case 2: // saved
                articleQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems")
                        .orderByKey()
                        .limitToFirst(query.limit);
                return new FeedListLiveData(articleQuery, query.state);
            default:
                articleQuery = mDatabaseReference.child(query.dbRef)
                        .orderByChild(query.orderByChild)
                        .limitToFirst(query.limit);
                return new FeedListLiveData(articleQuery, themeList, seed);
        }
    }

    private void filterNearbyArticles(Map<String, Long> articleIds, Map<String, List<String>> postcodeMap,
                                      @Nullable String keyword, boolean weekOnly, int limit,
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
                        long weekAgoMidnight = DateUtils.getMidnightOf(-7);
                        long weekLaterMidnight = DateUtils.getMidnightOf(7);
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
        List<String> rejectList = new ArrayList<>();
        if (keyword != null) {
            for (String articleId : orderedArticleIds) {
                if (doneList.size() >= articleLimit) {
                    break;
                }

                Query query = mDatabaseReference.child(ARTICLE_REF).child(articleId);
                query.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Article article = dataSnapshot.getValue(Article.class);
                        if (article != null) {
                            if ((article.getTitle() != null && article.getTitle().toLowerCase().contains(keyword)) ||
                                    (article.getSource() != null && article.getSource().toLowerCase().contains(keyword)) ||
                                    (article.getMainTheme() != null && article.getMainTheme().toLowerCase().contains(keyword)) ||
                                    (article.getPostText() != null && article.getPostText().toLowerCase().contains(keyword)) ||
                                    (article.getPostAuthor() != null && article.getPostAuthor().toLowerCase().contains(keyword))) {
                                article.postcode = postcodeMap.get(articleId);
                                articles.add(article);
                                doneList.add(article.getObjectID());
                            } else {
                                rejectList.add(article.getObjectID());
                            }
                        } else {
                            rejectList.add("failed to load article " + (doneList.size() + 1));
                        }

                        if (doneList.size() >= articleLimit ||
                                doneList.size() + rejectList.size() >= orderedArticleIds.size()) {
                            onComplete.accept(articles);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }
                });
            }
        } else {
            for (int i = 0; i < articleLimit; i++) {
                articles.add(new Article());
            }
            for (int i = 0; i < articleLimit; i++) {
                final int index = i;
                String articleId = orderedArticleIds.get(i);
                Query query = mDatabaseReference.child(ARTICLE_REF).child(articleId);
                query.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Article article = dataSnapshot.getValue(Article.class);
                        if (article != null) {
                            //Log.d(TAG, "title: " + article.getTitle());
                            article.postcode = postcodeMap.get(articleId);
                            articles.remove(index);
                            articles.add(index, article);
                            doneList.add(article.getObjectID());
                        } else {
                            doneList.add("failed to load article " + (doneList.size() + 1));
                        }

                        if (doneList.size() >= articleLimit) {
                            onComplete.accept(articles);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }
                });
            }
        }
    }

    public FeedListLiveData getSavedArticles(FbQuery query) {
        Query savedItemsQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems");
        return new FeedListLiveData(savedItemsQuery, query.state);
    }

    public void getNearbyArticles(double lat, double lng, double radius, @Nullable String keyword,
                                  Consumer<List<Article>> onComplete) {
        getNearbyArticles(lat, lng, radius, keyword, false, 50, onComplete);
    }

    public void getNearbyArticles(double lat, double lng, double radius, @Nullable String keyword,
                                  boolean weekOnly, int limit, Consumer<List<Article>> onComplete) {
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
        Map<String, List<String>> postcodeMap = new HashMap<>();
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
                                Pattern postcodePattern = Pattern.compile(".*(Singapore [0-9]{6}|S[0-9]{6})", Pattern.CASE_INSENSITIVE);
                                String postcode = postcodePattern.matcher(address.address).replaceAll("$1");
                                for (String id : address.article.keySet()) {
                                    Long reminderDate = address.article.get(id);
                                    Long cutoff = DateUtils.getMidnightOf(-14);
                                    if (reminderDate != null) {
                                        if ((cutoff != null && reminderDate > cutoff) || reminderDate == 1L) {
                                            /*
                                            these are all the articles with no reminderDate or
                                            reminderDate greater than cutoff,
                                            i.e. first date appearing in title is greater than 14 days
                                            ago. this is so we avoid removing articles with
                                            dates from x to y where reminder date is x - 1 but
                                            event is still valid as y has not reached. the implication
                                            is that there will be deals/events that expired up to
                                            14 days ago
                                            */
                                            List<String> postcodeList = new ArrayList<>();
                                            List<String> postcodes = postcodeMap.get(id);
                                            if (postcodes != null) {
                                                postcodeList.addAll(postcodes);
                                            }
                                            if (!postcodeList.contains(postcode)) postcodeList.add(postcode);
                                            postcodeMap.put(id, postcodeList);
                                            articleIds.put(id, reminderDate);
                                        } else {
                                            // remove all events that expired more than 14 days ago
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
                        Handler handler = new Handler();
                        handler.postDelayed(() -> {
                            Log.d(TAG, "articleIds size: " + articleIds.size());
                            filterNearbyArticles(articleIds, postcodeMap, keyword, weekOnly, limit, onComplete);
                        }, 200);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            });
        }
    }

    public FeedListLiveData getAdditionalArticles(FbQuery query, Object index, int indexType,
                                                  List<String> themeList, int seed) {
        Query articleQuery;
        switch (query.state) {
            case -2: // source
            case -1: // mainTheme
            case 3: // search
                articleQuery = mDatabaseReference.child(query.dbRef)
                        .orderByChild(query.orderByChild)
                        .limitToFirst(query.limit + 1);
                articleQuery = articleQuery.startAt((String) index);
                return new FeedListLiveData(articleQuery, themeList, seed);
            case 2: // saved
                articleQuery = mDatabaseReference.child(USER_REF + "/" + mUid + "/savedItems")
                        .orderByKey()
                        .limitToFirst(query.limit + 1);
                articleQuery = articleQuery.startAt((String) index);
                return new FeedListLiveData(articleQuery, query.state);
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
                return new FeedListLiveData(articleQuery, themeList, seed);
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
                    if (!dataSnapshot.exists()) {
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
                            if (timeNow - lastQueryTimestamp < 1L * 60L * 60L * 1000L) { // 1 hour
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
                            if (timeNow - lastQueryTimestamp < 1L * 60L * 60L * 1000L) { // 1 hour
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
                            if (timeNow - lastQueryTimestamp < 1L * 60L * 60L * 1000L) { // 1 hour
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
                                    if (DateUtils.getMidnightOf(0) != null) {
                                        if (article.reminderDate > DateUtils.getMidnightOf(0) &&
                                                article.reminderDate < DateUtils.getMidnightOf(1)) {
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
                    query.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot articlesSnap) {
                            Log.d(TAG, "articles fetched");
                            query.removeEventListener(this);

                            List<Task<Boolean>> taskList = new ArrayList<>();
                            for (DataSnapshot snap : articlesSnap.getChildren()) {
                                Article algArticle = snap.getValue(Article.class);
                                if (algArticle == null) continue;

                                TaskCompletionSource<Boolean> articleTaskSource = new TaskCompletionSource<>();
                                Task<Boolean> articleTask = articleTaskSource.getTask();
                                taskList.add(articleTask);

                                String articleId = algArticle.getObjectID();
                                mDatabaseReference.child(ARTICLE_REF).child(articleId)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                        Article article = dataSnapshot.getValue(Article.class);
                                        if (article != null) {
                                            if (article.htmlContent != null && !article.htmlContent.equals("")) {
                                                article.htmlContent = HtmlUtils.cleanHtmlContent(article.htmlContent,
                                                        article.getLink(), article.selector, article.getObjectID(), imageWidth);
                                                dbArticle localArticle = new dbArticle(mContext, article);
                                                articleList.add(localArticle);
                                            }
                                            articleTaskSource.trySetResult(true);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError databaseError) { }
                                });

                                Tasks.whenAll(taskList).addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "articlesDownloaded: " + articleList.size());
                                    onComplete.accept(articleList);
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

    public void createAlgoliaPost(JSONObject post) {
        if (mAlgoliaIndex == null) {
            setupAlgoliaClient(() -> {
                mAlgoliaIndex.addObjectAsync(post, null);
            });
        } else {
            mAlgoliaIndex.addObjectAsync(post, null);
        }
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
            public void onCancelled(@NonNull DatabaseError databaseError) { }
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
                        dbStation station = snap.getValue(dbStation.class);
                        if (station != null && mrtStationMap.get(station.stationLocale) == null) {
                            Map<String, Object> location = new HashMap<>();
                            location.put("latitude", station.latitude);
                            location.put("longitude", station.longitude);
//                            location.put("geofence", station.geofence);
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

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot snap : dataSnapshot.getChildren()) {
                        dbStation station = snap.getValue(dbStation.class);
                        if (station != null && mrtStationMap.get(station.stationLocale) == null) {
                            Map<String, Object> location = new HashMap<>();
                            location.put("latitude", station.latitude);
                            location.put("longitude", station.longitude);
//                            location.put("geofence", station.geofence);
                            mrtStationMap.put(station.stationLocale, location);
                        }
                    }

                    if (mrtStationMap.size() > 0) {
                        onComplete.accept(mrtStationMap);
                        mrtRef.removeEventListener(this);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                if (onError != null) {
                    onError.accept(databaseError);
                }
            }
        };

        mrtRef.addValueEventListener(listener);
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

    public void getSingleVideo(String videoId, Consumer<Video> onComplete) {
        DatabaseReference ref = mDatabaseReference.child(VIDEO_REF).child(videoId);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Video video = dataSnapshot.getValue(Video.class);
                    onComplete.accept(video);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
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
                if (databaseError == null) {
                    mExecutors.diskWrite().execute(() -> {
                        mAddressRoomDb.addressDAO().deleteForArticle(articleId);
                    });
                }
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

    public void getSavedItemsAddresses(Consumer<List<dbAddress>> onComplete) {
        List<dbAddress> addressList = new ArrayList<>();
        List<String> savedList = new ArrayList<>();

        if (mUid == null || mUid.equals("")) {
            mUid = mSharedPrefs.getString("uid", "");
            if (mUid.equals("")) {
                onComplete.accept(addressList);
                return;
            }
        }

        DatabaseReference savedRef = mDatabaseReference.child(USER_REF).child(mUid).child("savedItems");
        savedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snap : dataSnapshot.getChildren()) {
                    savedList.add(snap.getKey());
                }
                if (savedList.size() == 0) {
                    onComplete.accept(addressList);
                    return;
                }

                List<Task<Boolean>> taskList = new ArrayList<>();
                for (String articleId : savedList) {
                    TaskCompletionSource<Boolean> addressTaskSource = new TaskCompletionSource<>();
                    Task<Boolean> addressTask = addressTaskSource.getTask();
                    taskList.add(addressTask);
                    getSavedAddressFor(articleId, addresses -> {
                        addressList.addAll(addresses);
                        addressTaskSource.trySetResult(true);
                    });
                }

                Tasks.whenAll(taskList).addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "addressList: " + addressList.size());
                    onComplete.accept(addressList);
//                    mHandler.postDelayed(() -> {
//                        mExecutors.networkIO().execute(() -> {
//                            savedRef.removeEventListener(this);
//                        });
//                    }, 3000);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    public void getSavedAddressFor(String articleId, Consumer<List<dbAddress>> onComplete) {
        List<dbAddress> addresses = new ArrayList<>();
        DatabaseReference locationRef = mDatabaseReference.child("location").child(articleId);
        locationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snap : dataSnapshot.getChildren()) {
                    Address address = snap.getValue(Address.class);
                    if (address != null) {
                        if (address.location == null) Log.d(TAG, "no location: " + snap.getKey());
                        dbAddress localAddress = new dbAddress(address, articleId);
                        addresses.add(localAddress);
                    }
                }

                onComplete.accept(addresses);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
    }

    public void getItemListFor(String uid, UserViewModel.UserAction type,
                               Consumer<List<Object>> onComplete) {
        Log.d(TAG, "getItemListFor: " + uid + ", " + type.name());
        DatabaseReference userRef = mDatabaseReference.child("user/" + uid);
        List<Object> itemList = new ArrayList<>();

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Long> items = (HashMap<String, Long>) dataSnapshot.getValue();
                    if (items == null || items.size() == 0) {
                        userRef.removeEventListener(this);
                        return;
                    }

                    LinkedHashMap<String, Long> sortedItems = items.entrySet().stream()
                            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                    (e1, e2) -> e2, LinkedHashMap::new));
                    List<String> itemIds = new ArrayList<>(sortedItems.keySet());

                    List<Task<Boolean>> taskList = new ArrayList<>();
                    int limit = Math.min(50, itemIds.size());
                    for (int i = 0; i < limit; i++) {
                        String id = itemIds.get(i);

                        TaskCompletionSource<Boolean> itemTaskSource = new TaskCompletionSource<>();
                        Task<Boolean> itemTask = itemTaskSource.getTask();
                        taskList.add(itemTask);
                        if (id.startsWith("yt:")) {
                            DatabaseReference videoRef = mDatabaseReference.child("video/" + id);
                            videoRef.addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.exists()) {
                                        Video video = dataSnapshot.getValue(Video.class);
                                        Log.d(TAG, "videoId: " + video.getObjectID());
                                        itemList.add(video);
                                        itemTaskSource.trySetResult(true);
                                    }
                                    videoRef.removeEventListener(this);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                    videoRef.removeEventListener(this);
                                }
                            });
                        } else {
                            DatabaseReference articleRef = mDatabaseReference.child("article/" + id);
                            articleRef.addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.exists()) {
                                        Article article = dataSnapshot.getValue(Article.class);
                                        Log.d(TAG, "articleId: " + article.getObjectID());
                                        itemList.add(article);
                                    }
                                    articleRef.removeEventListener(this);
                                    itemTaskSource.trySetResult(true);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                    articleRef.removeEventListener(this);
                                }
                            });
                        }
                    }

                    Tasks.whenAll(taskList).addOnSuccessListener(aVoid -> {
                        onComplete.accept(itemList);
                        userRef.removeEventListener(this);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                userRef.removeEventListener(this);
            }
        };

        Query query = null;
        switch (type) {
            case UPVOTE:
                query = userRef.child("upvotedItems");
                break;
            case DOWNVOTE:
                query = userRef.child("downvotedItems");
                break;
            case COMMENT:
                query = userRef.child("commentedItems");
                break;
            case POST:
                query = userRef.child("createdPosts");
                break;
            case HISTORY:
                query = userRef.child("openedArticles");
                break;
        }
        if (query != null) {
            query.addValueEventListener(listener);
        }
    }

    // Track notifications
    public void logNotificationClicked(String userId, @Nullable String itemId, String type) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        if (itemId != null) data.put("itemId", itemId);
        data.put("type", type);
        data.put("date", (new Date()).getTime());

        String key = mDatabaseReference.child(NOTIFICATION_REF).child(userId).push().getKey();
        if (key != null)
            mDatabaseReference.child(NOTIFICATION_REF).child(userId).child(key).updateChildren(data);
    }

    public void logNotificationError(String userId, @Nullable String itemId, String type) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        if (itemId != null) data.put("itemId", itemId);
        data.put("type", type);
        data.put("date", (new Date()).getTime());

        String key = mDatabaseReference.child("error").push().getKey();
        if (key != null) mDatabaseReference.child("error").child(key).updateChildren(data);
    }

    public void logSeenItemEvent(String uid, String itemId, String type) {
        Long now = (new Date()).getTime();
        if (type.equals("article") || type.equals("post")) {
            mDatabaseReference.child(ARTICLE_REF).child(itemId).child("seenBy").child(uid).setValue(now);
        } else if (type.equals("video")) {
            mDatabaseReference.child(VIDEO_REF).child(itemId).child("seenBy").child(uid).setValue(now);
        }
    }

    public void logItemTimeLog(TimeLog timeLog) {
        String key = mDatabaseReference.child("timeLog").push().getKey();
        mDatabaseReference.child("timeLog/" + key).setValue(timeLog);
    }

    public void logSurveyResponse(boolean response) {
        mDatabaseReference.child("survey").child(mUid).setValue(response);
    }
}