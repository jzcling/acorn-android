package acorn.com.acorn_app.ui.viewModels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.jaredrummler.android.device.DeviceName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.AddressRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.data.UserLiveData;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.models.dbStation;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.GeofenceUtils;
import acorn.com.acorn_app.utils.LocationPermissionsUtils;

import static acorn.com.acorn_app.ui.AcornApplication.mFirebaseAnalytics;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class UserViewModel extends AndroidViewModel {
    private static final String TAG = "UserViewModel";

    // User activity types
    public enum UserAction { UPVOTE, DOWNVOTE, COMMENT, POST, HISTORY }

    // User status
    public static final String LEVEL_0 = "Budding Seed";
    public static final String LEVEL_1 = "Emerging Sprout";
    public static final String LEVEL_2 = "Thriving Sapling";
    public static final String LEVEL_3 = "Wise Oak";

    private FirebaseUser mUser;
    private String mUid;
    private ArrayList<String> mUserThemePrefs;
    private long lastRecArticlesPushTime;
    private long lastRecArticlesScheduleTime;
    private long lastRecDealsPushTime;
    private long lastRecDealsScheduleTime;

    private DatabaseReference mUserRef;

    private AppExecutors mExecutors;
    private NetworkDataSource mDataSource;
    private AddressRoomDatabase mAddressRoomDb;

    private GeofenceUtils mGeofenceUtils;
    private LocationPermissionsUtils mLocationPermissionsUtils;

    private SharedPreferences mSharedPreferences;
    private boolean articleNotifValue;
    private boolean dealsNotifValue;
    private boolean savedArticlesReminderNotifValue;

    public MutableLiveData<Boolean> isUserAuthenticated = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> isFirstTimeLogin = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> showEmailVerificationAlert = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> showLocationPermissionsRequest = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> sendSurveyRequest = new MutableLiveData<>(false);
    public MutableLiveData<ArrayList<String>> themePrefs = new MutableLiveData<>(new ArrayList<>());
    public MutableLiveData<String> themeSearchKey = new MutableLiveData<>();
    public MutableLiveData<String> themeSearchFilter = new MutableLiveData<>();

    public UserViewModel(Application application, SharedPreferences sharedPreferences,
                         AppExecutors executors, NetworkDataSource dataSource,
                         AddressRoomDatabase addressRoomDb, GeofenceUtils geofenceUtils,
                         LocationPermissionsUtils locationPermissionUtils) {
        super(application);

        mExecutors = executors;
        mDataSource = dataSource;
        mAddressRoomDb = addressRoomDb;

        mGeofenceUtils = geofenceUtils;
        mLocationPermissionsUtils = locationPermissionUtils;

        mSharedPreferences = sharedPreferences;
        articleNotifValue = mSharedPreferences.getBoolean(application.getString(R.string.pref_key_notif_article), true);
        dealsNotifValue = mSharedPreferences.getBoolean(application.getString(R.string.pref_key_notif_deals), true);
        savedArticlesReminderNotifValue = mSharedPreferences.getBoolean(
                application.getString(R.string.pref_key_notif_saved_articles_reminder), true);
    }

    public UserLiveData getUser(String uid) {
        return new UserLiveData(uid);
    }

    // For set up of main activity
    // ---------------------------- Acorn Activity -------------------------------------
    public void setupUser(FirebaseUser firebaseUser, @Nullable String referredBy, Runnable setupUi) {
        Log.d(TAG, "setupUser");

        mUser = firebaseUser;
        mUid = firebaseUser.getUid();
        DatabaseReference mDbRef = FirebaseDatabase.getInstance().getReference();
        mUserRef =  mDbRef.child("user/" + mUid);

        mUserRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                if (user != null) {

                    // put uid in sharedPrefs
                    mSharedPreferences.edit().putString("uid", mUid).apply();

                    // Set up Crashlytics identifier
                    Crashlytics.setUserIdentifier(mUid);
                    Crashlytics.setUserName(user.getDisplayName());
                    Crashlytics.setUserEmail(user.getEmail());

                    // Set up Firebase Analytics identifier
                    mFirebaseAnalytics.setUserId(mUid);

                    TaskCompletionSource<String> tokenSource = new TaskCompletionSource<>();
                    Task<String> tokenTask = tokenSource.getTask();

                    FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(instanceIdResult -> {
                        tokenSource.trySetResult(instanceIdResult.getToken());
                    }).addOnFailureListener(tokenSource::trySetException);

                    Tasks.whenAll(tokenTask).addOnSuccessListener(aVoid -> {
                        String userToken = tokenTask.getResult();

                        if (!mUser.isEmailVerified() && !user.isEmailVerified) {
                            showEmailVerificationAlert.setValue(true);
                        } else {
                            isUserAuthenticated.setValue(true);
                        }

                        user.setDisplayName(user.getDisplayName());
                        user.setDevice(DeviceName.getDeviceName());
                        user.setLastSignInTimeStamp(mUser.getMetadata().getLastSignInTimestamp());
                        user.setToken(userToken);
                        user.openedSinceLastReport = true;
                        if (isUserAuthenticated.getValue() != null &&
                                isUserAuthenticated.getValue()) user.isEmailVerified = true;

                        mUserRef.removeEventListener(this);
                        mUserRef.updateChildren(user.toMap());

                        mUserThemePrefs = user.getSubscriptions();
                        if (mUserThemePrefs.size() < 1) {
                            mUserThemePrefs = new ArrayList<>();
                            String[] themeArray = getApplication().getResources().getStringArray(R.array.theme_array);
                            Collections.addAll(mUserThemePrefs, themeArray);
                        }
                        themePrefs.setValue(mUserThemePrefs);
                        Log.d(TAG, "themesPrefs: " + mUserThemePrefs.toString());
                        lastRecArticlesPushTime = user.getLastRecArticlesPushTime();
                        lastRecArticlesScheduleTime = user.getLastRecArticlesScheduleTime();
                        lastRecDealsPushTime = user.getLastRecDealsPushTime();
                        lastRecDealsScheduleTime = user.getLastRecDealsScheduleTime();

                        buildThemeKeyAndFilter(mUserThemePrefs);

                        setupUi.run();

                        // Set up default settings if not set
                        if (!mSharedPreferences.contains(getApplication().getString(R.string.pref_key_notif_comment)))
                            mSharedPreferences.edit().putBoolean(getApplication().getString(R.string.pref_key_notif_comment), true).apply();
                        if (!mSharedPreferences.contains(getApplication().getString(R.string.pref_key_notif_article)))
                            mSharedPreferences.edit().putBoolean(getApplication().getString(R.string.pref_key_notif_article), true).apply();
                        if (!mSharedPreferences.contains(getApplication().getString(R.string.pref_key_notif_deals)))
                            mSharedPreferences.edit().putBoolean(getApplication().getString(R.string.pref_key_notif_deals), true).apply();
                        if (!mSharedPreferences.contains(getApplication().getString(R.string.pref_key_notif_saved_articles_reminder)))
                            mSharedPreferences.edit().putBoolean(getApplication().getString(R.string.pref_key_notif_saved_articles_reminder), true).apply();
                        if (!mSharedPreferences.contains(getApplication().getString(R.string.pref_key_night_mode)))
                            mSharedPreferences.edit().putString(getApplication().getString(R.string.pref_key_night_mode), "0").apply();
                        if (!mSharedPreferences.contains(getApplication().getString(R.string.pref_key_feed_videos)))
                            mSharedPreferences.edit().putBoolean(getApplication().getString(R.string.pref_key_feed_videos), true).apply();

                        long now = (new Date()).getTime();
                        // store stations on device, updated every week
                        long lastUpdatedStations = mSharedPreferences.getLong("lastUpdatedStations", 0);
                        if (lastUpdatedStations < now - 7L * 24L * 60L * 60L * 1000L) { // 7 days
                            mExecutors.networkIO().execute(() -> {
                                mDataSource.getMrtStations(stationMap -> {
                                    for (Map.Entry<String, Map<String, Object>> entry : stationMap.entrySet()) {
                                        String locale = entry.getKey();
                                        Double latitude = (Double) entry.getValue().get("latitude");
                                        Double longitude = (Double) entry.getValue().get("longitude");
                                        if (latitude != null && longitude != null) {
                                            dbStation station = new dbStation(latitude, longitude, locale, "MRT");
                                            mExecutors.diskWrite().execute(() -> {
                                                mAddressRoomDb.addressDAO().insert(station);
                                            });
                                        }
                                    }
                                    mSharedPreferences.edit().putLong("lastUpdatedStations", now).apply();
                                }, error -> {
                                    Log.d(TAG, "error getting MRT Stations: " + error.toString());
                                });
                            });
                        }

                        // store saved addresses on device, updated every 3 days
                        long lastUpdatedSavedAddresses = mSharedPreferences.getLong("lastUpdatedSavedAddresses", 0);
                        if (lastUpdatedSavedAddresses < now - 3L * 24L * 60L * 60L * 1000L) { // 3 days
                            mExecutors.networkIO().execute(() -> {
                                mDataSource.getSavedItemsAddresses(addresses -> {
                                    Log.d(TAG, "savedAddresses: " + addresses.size());
                                    mExecutors.diskWrite().execute(() -> {
                                        mAddressRoomDb.addressDAO().insertAddresses(addresses);
                                        mSharedPreferences.edit().putLong("lastUpdatedSavedAddresses", now).apply();
                                    });
                                });
                            });
                        }

                        // Refresh geofences every day
                        long lastUpdatedGeofences = mSharedPreferences.getLong("lastUpdatedGeofences", 0);
                        if (lastUpdatedGeofences < now - 24L * 60L * 60L * 1000L) { // 1 day
                            if (mSharedPreferences.getBoolean(getApplication().getString(R.string.pref_key_notif_location), false)) {
                                mLocationPermissionsUtils.checkLocationSettings((location) -> {
                                    Context context = getApplication().getApplicationContext();
                                    mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.REMOVE;
                                    mGeofenceUtils.performPendingGeofenceTask(context, () -> {
                                        mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.ADD;
                                        mGeofenceUtils.performPendingGeofenceTask(context, location);
                                    });
                                });
                            }
                        }

                        if (user.openedArticles.keySet().size() > 50) {
                            boolean hasSeenSurveyRequest = mSharedPreferences.getBoolean("hasSeenSurveyRequest", false);
                            if (!hasSeenSurveyRequest) {
                                sendSurveyRequest.setValue(true);
                                mSharedPreferences.edit()
                                        .putBoolean("hasSeenSurveyRequest", true)
                                        .apply();
                            }
                        }

                        // get locations and set up geofences
                        if (!mSharedPreferences.getBoolean(getApplication().getString(R.string.pref_key_loc_permissions_asked), false))
                            showLocationPermissionsRequest.setValue(true);

                        // subscribe to app topic for manual articles push
                        FirebaseMessaging.getInstance().subscribeToTopic("acorn");

                        if (savedArticlesReminderNotifValue) {
                            // subscribe to saved articles reminder push
                            FirebaseMessaging.getInstance().subscribeToTopic("savedArticlesReminderPush");
                        }

                        // Schedule recommended articles push service unless explicitly disabled by user
                        if (articleNotifValue) {
                            long timeElapsedSinceLastPush = now - lastRecArticlesPushTime;

                            if (!mSharedPreferences.getBoolean("isRecArticlesScheduled", false) ||
                                    (timeElapsedSinceLastPush > 24L * 60L * 60L * 1000L && // if last push time is longer than a day
                                            lastRecArticlesScheduleTime < lastRecArticlesPushTime)) { // and last scheduled time is before last push time
                                mDataSource.scheduleRecArticlesPush();
                                mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", true).apply();
                            }
                        }

                        // Schedule recommended deals push service unless explicitly disabled by user
                        if (dealsNotifValue) {
                            long timeElapsedSinceLastPush = now - lastRecDealsPushTime;

                            if (!mSharedPreferences.getBoolean("isRecDealsScheduled", false) ||
                                    (timeElapsedSinceLastPush > 24L * 60L * 60L * 1000L && // if last push time is longer than a day
                                            lastRecDealsScheduleTime < lastRecDealsPushTime)) { // and last scheduled time is before last push time
                                mDataSource.scheduleRecDealsPush();
                                mSharedPreferences.edit().putBoolean("isRecDealsScheduled", true).apply();
                            }
                        }
                    }).addOnFailureListener(e -> {
                        createToast(getApplication(), "Failed to get your user profile", Toast.LENGTH_SHORT);
                    });
                } else {
                    isFirstTimeLogin.setValue(true);
                    if (!mUser.isEmailVerified()) {
                        mUser.sendEmailVerification();
                    } else {
                        isUserAuthenticated.setValue(true);
                    }
                    String displayName = mUser.getDisplayName();
                    String email = mUser.getEmail();
                    String device = DeviceName.getDeviceName();
                    Long creationTimeStamp = mUser.getMetadata().getCreationTimestamp();
                    Long lastSignInTimeStamp = mUser.getMetadata().getLastSignInTimestamp();

                    User newUser = new User(mUid, displayName, null, email, device,
                            creationTimeStamp, lastSignInTimeStamp);
                    if (isUserAuthenticated.getValue() != null &&
                            isUserAuthenticated.getValue()) newUser.isEmailVerified = true;
                    newUser.openedSinceLastReport = true;
                    mUserRef.setValue(newUser);

                    // Set up default settings
                    mSharedPreferences.edit().putBoolean(getApplication().getString(R.string.pref_key_notif_comment), true)
                            .putBoolean(getApplication().getString(R.string.pref_key_notif_article), true)
                            .putBoolean(getApplication().getString(R.string.pref_key_notif_deals), true)
                            .putBoolean(getApplication().getString(R.string.pref_key_notif_saved_articles_reminder), true)
                            .putString(getApplication().getString(R.string.pref_key_night_mode), "0")
                            .putBoolean(getApplication().getString(R.string.pref_key_feed_videos), true)
                            .apply();

                    // Check if referred by someone
                    checkReferral(referredBy);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    public String setUserStatus(int userStatus) {
        if (userStatus == 0) {
            return LEVEL_0;
        } else if (userStatus == 1) {
            return LEVEL_1;
        } else if (userStatus == 2) {
            return LEVEL_2;
        } else if (userStatus >= 3) {
            return LEVEL_3;
        } else {
            return LEVEL_0;
        }
    }

    public void buildThemeKeyAndFilter(List<String> themePrefs) {
        StringBuilder filterStringBuilder = new StringBuilder();
        StringBuilder searchKeyBuilder = new StringBuilder();

        Collections.sort(themePrefs);
        for (int i = 0; i < themePrefs.size(); i++) {
            if (i == 0) {
                searchKeyBuilder.append(themePrefs.get(i));
                filterStringBuilder.append("mainTheme: \"").append(themePrefs.get(i)).append("\"");
            } else {
                searchKeyBuilder.append("_").append(themePrefs.get(i));
                filterStringBuilder.append(" OR mainTheme: \"").append(themePrefs.get(i)).append("\"");
            }
        }
        String mThemeSearchKey = searchKeyBuilder.toString();
        String mThemeSearchFilter = filterStringBuilder.toString();
        themeSearchKey.setValue(mThemeSearchKey);
        themeSearchFilter.setValue(mThemeSearchFilter);
        mSharedPreferences.edit().putString("themeSearchKey", mThemeSearchKey)
                .putString("themeSearchFilter", mThemeSearchFilter).apply();
    }

    private void checkReferral(String referredBy) {
        // check to see if user opened an invite link
        if (referredBy != null) {
            Log.d(TAG, "checkReferral: user: " + mUid + ", referrer: " + referredBy);
            mDataSource.setReferrer(mUid, referredBy);
        }
    }
    // ---------------------------- Acorn Activity -------------------------------------



    // ---------------------------- User Activity --------------------------------------
    public void getItemListFor(String uid, UserAction type, Consumer<List<Object>> onComplete) {
        mDataSource.getItemListFor(uid, type, onComplete::accept);
    }
}
