package acorn.com.acorn_app.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.algolia.instantsearch.core.helpers.Searcher;
import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.AdSize;
import com.facebook.ads.AdView;
import com.facebook.ads.NativeAd;
import com.facebook.ads.NativeAdListener;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.nex3z.notificationbadge.NotificationBadge;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.AddressRoomDatabase;
import acorn.com.acorn_app.data.FeedListLiveData;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.ui.adapters.FeedAdapter;
import acorn.com.acorn_app.ui.fragments.NotificationsDialogFragment;
import acorn.com.acorn_app.ui.viewModels.FeedViewModel;
import acorn.com.acorn_app.ui.viewModels.FeedViewModelFactory;
import acorn.com.acorn_app.ui.viewModels.NotificationViewModel;
import acorn.com.acorn_app.ui.viewModels.UserViewModel;
import acorn.com.acorn_app.ui.viewModels.UserViewModelFactory;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.GeofenceUtils;
import acorn.com.acorn_app.utils.InjectorUtils;
import acorn.com.acorn_app.utils.InviteUtils;
import acorn.com.acorn_app.utils.LocationPermissionsUtils;
import acorn.com.acorn_app.utils.Logger;
import acorn.com.acorn_app.utils.UiUtils;

import static acorn.com.acorn_app.data.NetworkDataSource.ALGOLIA_API_KEY;
import static acorn.com.acorn_app.data.NetworkDataSource.SEARCH_REF;
import static acorn.com.acorn_app.ui.AcornApplication.mFirebaseAnalytics;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class AcornActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        FeedAdapter.OnLongClickListener,
        AdapterView.OnItemSelectedListener {

    private static final String TAG = "AcornActivity";

    public static final long ID_OFFSET = 5000000000000000L;
    public static final int TARGET_POINTS_MULTIPLIER = 3;

    private static final int RC_SIGN_IN = 1001;
    private static final int RC_PREF = 1002;
    public static final int RC_THEME_PREF = 1003;
    public static final int RC_SHARE = 1004;
    public static final int RC_NOTIF = 1005;
    private static final String ARTICLE_CARD_TYPE = "card";
    private static final String ARTICLE_LIST_TYPE = "list";

    // User status
    private DatabaseReference mUserStatusRef;
    private ValueEventListener mUserStatusListener;
    private DatabaseReference mUserPremiumStatusRef;
    private ValueEventListener mUserPremiumStatusListener;

    //Theme
    public static boolean isFirstTimeLogin;
    public static String mThemeSearchKey;
    public static String mThemeSearchFilter;
    public static String mAllThemesSearchKey;
    private static List<String> mThemeList;
    private static Integer mSeed;

    //Main UI
    private DrawerLayout mDrawer;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private FloatingActionButton mScrollFab;
    private FloatingActionButton mPostFab;
    private CardView mNewContentPrompt;

    //Firebase database
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;
    public static FbQuery mQuery;

    //Data source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    // Room Database;
    private AddressRoomDatabase mAddressRoomDb;

    //RecyclerView
    private RecyclerView mRecyclerView;
    private FeedAdapter mAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private boolean isLoadingMore = false;
    private static Parcelable mLlmState;
    private boolean mPendingRestoreState;

    //View Models
    private UserViewModel mUserViewModel;
    private FeedViewModel mFeedViewModel;
    private final Map<FeedListLiveData, Observer<List<Object>>> mObservedList = new HashMap<>();
    private NotificationViewModel mNotifViewModel;

    //User
    private FirebaseUser mFirebaseUser;
    public static boolean isUserAuthenticated = false;
    public static String mUid;
    public static String mUsername;
    public static String mUserToken;
    private String mUserStatus;
    public Map<String, Long> mUserPremiumStatus;
    public static ArrayList<String> mUserThemePrefs;
    private TextView mUsernameTextView;
    private TextView mUserStatusTextView;
    private CardView mUserPremiumStatusCardView;
    private TextView mUserPremiumStatusTextView;
    private String mReferredBy;
    public static final String LEVEL_0 = "Budding Seed";
    public static final String LEVEL_1 = "Emerging Sprout";
    public static final String LEVEL_2 = "Thriving Sapling";
    public static final String LEVEL_3 = "Wise Oak";

    //Shared prefs
    public static SharedPreferences mSharedPreferences;
    public static SharedPreferences mNotificationsPref;
    private static int dayNightValue;
    private static Boolean commentNotifValue;
    private static Boolean articleNotifValue;
    private static Boolean dealsNotifValue;
    private static Boolean savedArticlesReminderNotifValue;
    public final String COMMENTS_NOTIFICATION = "commentsNotificationValue";
    public final String REC_ARTICLES_NOTIFICATION = "recArticlesNotificationValue";
    public final String REC_DEALS_NOTIFICATION = "recDealsNotificationValue";
    public final String SAVED_ARTICLES_REMINDER_NOTIFICATION = "savedArticlesReminderNotificationValue";
    public final String LOCATION_NOTIFICATION = "locationNotificationValue";

    //Notifications
    private NotificationBadge notificationBadge;

    //Menu
    private Menu navMenu;
    private MenuItem mSearchButton;

    //InstantSearch
    public static Searcher mSearcher;
    public static final String ALGOLIA_APP_ID = "O96PPLSF19";
    public static final String ALGOLIA_INDEX_NAME = "article";

    // Location Permissions
    private LocationPermissionsUtils mLocationPermissionsUtils;

    // Geofence
    private GeofenceUtils mGeofenceUtils;

    // Toolbar
    private Toolbar mToolbar;
    private Spinner mToolbarSpinner;
    private static String mSelectedFeed;

    private Bundle mSavedInstanceState;
    private Logger mLogger;
    private Handler mHandler = new Handler();

    //Ad
    private AdView mBannerAdView;
    private List<NativeAd> mNativeAds = new ArrayList<>();
    private final int AD_COUNT = 10;
    private boolean pendingAdLoad = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate");
        mSavedInstanceState = savedInstanceState;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mNotificationsPref = getSharedPreferences(getString(R.string.notif_pref_id), MODE_PRIVATE);
        dayNightValue = Integer.parseInt(mSharedPreferences.getString(
                getString(R.string.pref_key_night_mode),"0"));
        commentNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_comment), true);
        articleNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_article), true);
        dealsNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_deals), true);
        savedArticlesReminderNotifValue = mSharedPreferences.getBoolean(
                getString(R.string.pref_key_notif_saved_articles_reminder), true);
        AppCompatDelegate.setDefaultNightMode(dayNightValue);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acorn);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle("");
        setSupportActionBar(mToolbar);

        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null);

        // For facebook
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String hashKey = new String(Base64.encode(md.digest(), 0));
                Log.d(TAG, "hashKey: " + hashKey);
            }
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, e.getLocalizedMessage());
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage());
        }

        mFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        // Set up Firebase Database
        if (mDatabase == null) mDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mDatabase.getReference();
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);

        // Set up Address Room Database
        mAddressRoomDb = AddressRoomDatabase.getInstance(this);

        // Set up logging
        mLogger = new Logger(this);

        // Handle dynamic links
        Intent intent = getIntent();
        String appLinkAction = intent.getAction();
        Uri appLinkData = intent.getData();
        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData != null) {
            Log.d(TAG, "handle intent");
            handleDynamicLink(intent);
        } else {
            Log.d(TAG, "no dynamic link");
            boolean fromNotif = intent.getBooleanExtra("fromNotif", false);
            String notifType = intent.getStringExtra("notifType");
            if (mFirebaseUser != null) {
                mLogger.logNotificationClicked(fromNotif, notifType, mFirebaseUser.getUid(), null);
            } else {
                mLogger.logNotificationError(fromNotif, notifType, "unknown", null);
            }
        }

        // Initiate views
        mRecyclerView = (RecyclerView) findViewById(R.id.card_recycler_view);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mScrollFab = (FloatingActionButton) findViewById(R.id.scroll_fab);
        mPostFab = (FloatingActionButton) findViewById(R.id.post_fab);
        mNewContentPrompt = (CardView) findViewById(R.id.new_content_message);

        // Set up recycler view
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new FeedAdapter(this, this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setVisibility(View.INVISIBLE);
        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0) {
                    mScrollFab.show();
                } else {
                    mScrollFab.hide();
//                    loadMoreArticles();
                }
            }
        });

        // Set up swipe refresh
        mSwipeRefreshLayout.setOnRefreshListener(() -> handleIntent(getIntent()));

        if (mFirebaseUser == null) {
            launchLogin();
        } else {
            setupUser(mFirebaseUser);
        }

        // Set up mScrollFab
        mScrollFab.setOnClickListener(view -> {
            mRecyclerView.smoothScrollToPosition(0);
            mScrollFab.hide();
        });

        // Set up mPostFab
        mPostFab.setOnClickListener(v -> {
            if (!isUserAuthenticated) {
                createToast(this, "Please verify your email to post", Toast.LENGTH_SHORT);
                return;
            }
            Intent createPostIntent = new Intent(this, CreatePostActivity.class);
            startActivity(createPostIntent);
        });

        // Set up navigation mDrawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                if (!mSharedPreferences.getBoolean(getString(R.string.helper_nearby_seen), false)) {
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    MenuItem item = navigationView.getMenu().findItem(R.id.nav_nearby);
                    LinearLayout view = (LinearLayout) item.getActionView();

                    int[] location = new int[2];
                    view.getLocationOnScreen(location);

                    View target = new View(AcornActivity.this);
                    target.setY(location[1]);
                    float density = getResources().getDisplayMetrics().density;
                    int height = (int) (density * 50);
                    target.setLayoutParams(new LinearLayout.LayoutParams(location[0], height));
                    navigationView.addView(target);

                    String title = "Near Me";
                    String text = "Explore articles recommending restaurants and deals near you! " +
                            "Refer a friend to enjoy this premium feature!";
                    UiUtils.highlightView(AcornActivity.this, target, title, text);

                    mSharedPreferences.edit().putBoolean(getString(R.string.helper_nearby_seen), true).apply();
                }

                if (!mSharedPreferences.getBoolean(getString(R.string.helper_user_details_seen), false)) {
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    View headerView = navigationView.getHeaderView(0);
                    Log.d(TAG, "headerView: " + headerView.toString());

                    String title = "User Details";
                    String text = "Check out your activity history here!";
                    UiUtils.highlightView(AcornActivity.this, headerView, title, text);

                    mSharedPreferences.edit().putBoolean(getString(R.string.helper_user_details_seen), true).apply();
                }

                super.onDrawerOpened(drawerView);
            }
        };
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Geofence
        mLocationPermissionsUtils = new LocationPermissionsUtils(this);
        mGeofenceUtils = GeofenceUtils.getInstance(getApplicationContext(), mDataSource);

        mNotifViewModel = new ViewModelProvider(this).get(NotificationViewModel.class);

        // Set up ad banner
        ConstraintLayout mBannerContainer = (ConstraintLayout) findViewById(R.id.ad_banner_layout_fb);
        mBannerAdView = new AdView(this,
                getString(R.string.fb_banner_main_ad_placement_id), AdSize.BANNER_HEIGHT_50);
        mBannerContainer.addView(mBannerAdView);
    }

    @Override
    public void onBackPressed() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            if (navMenu == null) {
                NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                navMenu = navigationView.getMenu();
            }
            createBackPressedDialog();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.app_bar_activity_acorn, menu);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        View navHeaderLayout = navigationView.getHeaderView(0);
        mUsernameTextView = (TextView) navHeaderLayout.findViewById(R.id.nav_user_name_text_view);
        mUserStatusTextView = (TextView) navHeaderLayout.findViewById(R.id.nav_user_status_text_view);
        mUserPremiumStatusCardView = (CardView) navHeaderLayout.findViewById(R.id.nav_user_premium_status_card_view);
        mUserPremiumStatusTextView = (TextView) navHeaderLayout.findViewById(R.id.nav_user_premium_status_text_view);
        if (navMenu == null) navMenu = (Menu) navigationView.getMenu();
        MenuItem signInMenuItem = (MenuItem) navMenu.findItem(R.id.nav_login);
        MenuItem signOutMenuItem = (MenuItem) navMenu.findItem(R.id.nav_logout);

        MenuItem nearbyItem = (MenuItem) navMenu.findItem(R.id.nav_nearby);
        nearbyItem.getIcon().setColorFilter(Color.rgb(255,50,50), PorterDuff.Mode.SRC_ATOP);

        final MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        mSearchButton = menu.findItem(R.id.action_search);
        ConstraintLayout notificationView = (ConstraintLayout) notificationItem.getActionView();
        notificationBadge = (NotificationBadge) notificationView.findViewById(R.id.notification_badge);
        mNotifViewModel.getNotificationList().observe(this,
                notificationList -> notificationBadge.setNumber(notificationList.size()));
        NotificationViewModel.sharedPrefs.postValue(mNotificationsPref);
        notificationView.setOnClickListener(v -> onOptionsItemSelected(notificationItem));

        if (mFirebaseUser != null) {
            signInMenuItem.setVisible(false);
            signOutMenuItem.setVisible(true);
        }

        if (mUsername != null && mUserStatus != null) {
            mUsernameTextView.setText(mUsername);
            mUserStatusTextView.setText(mUserStatus);

            mUsernameTextView.setOnClickListener(
                    v -> startActivity(new Intent(this, UserActivity.class)));
            mUserStatusTextView.setOnClickListener(
                    v -> startActivity(new Intent(this, UserActivity.class)));
        } else {
            mUsernameTextView.setText("Anonymous");
            mUserStatusTextView.setText("");
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        float height = mUserPremiumStatusCardView.getHeight();
        mUserPremiumStatusCardView.setRadius(height/2);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_search:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case R.id.action_notifications:
                new NotificationsDialogFragment().show(getSupportFragmentManager(), "NotificationsDialog");
                return true;
            case R.id.action_settings:
                mGeofenceUtils.mPreChangeValue = mGeofenceUtils.getGeofencesAdded(this);
                startActivityForResult(new Intent(this, SettingsActivity.class), RC_PREF);
                return true;
            case R.id.action_share_app:
                InviteUtils.createShortDynamicLink(mUid, (link) -> {
                    Intent shareIntent = new Intent();
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT,
                            "Don't miss out on the latest news, deals, events and life hacks! " +
                                    "Download Acorn: Your Favourite Blogs in a Nutshell now! " + link);
                    shareIntent.setAction(Intent.ACTION_SEND);
                    startActivity(Intent.createChooser(shareIntent, "Share app with"));
                });
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_subscriptions:
                if (mQuery != null && mQuery.state == 3 && item.isChecked()) break;
                mSelectedFeed = "Subscriptions";
                setupToolbarTitleSpinner(true, mUserThemePrefs);
                getThemeData();
                break;
            case R.id.nav_trending:
                if (mQuery != null && mQuery.state == 3 && item.isChecked()) break;
                mSelectedFeed = "Trending";
                String[] themePrefs = getResources().getStringArray(R.array.theme_array);
                List<String> themeList = new ArrayList<>();
                Collections.addAll(themeList, themePrefs);
                setupToolbarTitleSpinner(true, themeList);
                getTrendingData();
                break;
            case R.id.nav_deals:
                if (mQuery != null && mQuery.state == 4) break;
                mSelectedFeed = "Deals";
                getDealsData();
                setupToolbarTitleSpinner(false, null);
                break;
            case R.id.nav_saved:
                Intent savedArticlesIntent = new Intent(this, SavedArticlesActivity.class);
                startActivity(savedArticlesIntent);
                break;
            case R.id.nav_history:
                Intent itemListIntent = new Intent(this, ItemListActivity.class);
                itemListIntent.putExtra("userAction", "history");
                startActivity(itemListIntent);
                break;
            case R.id.nav_nearby:
                if (mUserPremiumStatus == null || mUserPremiumStatusTextView.getText() != "Premium") {
                    createToast(this, "Refer a friend using the Share App Invite " +
                            "function on the top right of the main page!", Toast.LENGTH_LONG);
                } else {
                    Intent nearbyArticlesIntent = new Intent(this, NearbyActivity.class);
                    startActivity(nearbyArticlesIntent);
                }
                break;
            case R.id.nav_video_feed:
                Intent videoFeedIntent = new Intent(this, VideoFeedActivity.class);
                startActivity(videoFeedIntent);
                break;
            case R.id.nav_themes:
                Intent editThemeIntent = new Intent(this, ThemeSelectionActivity.class);
                editThemeIntent.putStringArrayListExtra("themePrefs", mUserThemePrefs);
                startActivityForResult(editThemeIntent, RC_THEME_PREF);
                break;
            case R.id.nav_settings:
                startActivityForResult(new Intent(this, SettingsActivity.class), RC_PREF);
                break;
            case R.id.nav_invite:
                InviteUtils.createShortDynamicLink(mUid, (link) -> {
                    Intent shareIntent = new Intent();
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT,
                            "Don't miss out on the latest news, deals, events and life hacks! " +
                                    "Download Acorn: Your Favourite Blogs in a Nutshell now! " + link);
                    shareIntent.setAction(Intent.ACTION_SEND);
                    startActivity(Intent.createChooser(shareIntent, "Share app with"));
                });
                break;
            case R.id.nav_login:
                launchLogin();
                break;
            case R.id.nav_logout:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Would you like to log out?")
                        .setCancelable(true)
                        .setPositiveButton("Yes", (dialog, which) -> {
                            logout();
                            mDrawer.closeDrawer(GravityCompat.START);
                        })
                        .setNegativeButton("No", (dialog, which) -> dialog.cancel());
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                return true;
        }
        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent");
        super.onNewIntent(intent);
        String appLinkAction = intent.getAction();
        Uri appLinkData = intent.getData();
        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData != null) {
            handleDynamicLink(intent);
        } else {
            boolean fromNotif = intent.getBooleanExtra("fromNotif", false);
            String notifType = intent.getStringExtra("notifType");
            if (mFirebaseUser != null) {
                mLogger.logNotificationClicked(fromNotif, notifType, mFirebaseUser.getUid(), null);
            } else {
                mLogger.logNotificationError(fromNotif, notifType, "unknown", null);
            }
            handleIntent(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            // Successfully signed in
            if (resultCode == RESULT_OK) {
                mFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                setupUser(mFirebaseUser);

                if (navMenu == null) {
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    navMenu = (Menu) navigationView.getMenu();
                }
                MenuItem signInMenuItem = (MenuItem) navMenu.findItem(R.id.nav_login);
                MenuItem signOutMenuItem = (MenuItem) navMenu.findItem(R.id.nav_logout);

                if (mFirebaseUser != null) {
                    signInMenuItem.setVisible(false);
                    signOutMenuItem.setVisible(true);
                }

                if (mFeedViewModel != null) resetView();
            } else {
                // Sign in failed
                if (response == null) {
                    // User pressed back button
                    AlertDialog.Builder builder = new AlertDialog.Builder(AcornActivity.this);
                    builder.setMessage("In order for us to cater the best experience for you, " +
                            "we require that you sign in to use Acorn.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                launchLogin();
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                finish();
                            });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                    createToast(this, getString(R.string.sign_in_cancelled), Toast.LENGTH_SHORT);
                    return;
                }

                if (response.getError().getErrorCode() == ErrorCodes.NO_NETWORK) {
                    createToast(this, getString(R.string.sign_in_no_connection), Toast.LENGTH_SHORT);
                    return;
                }

                createToast(this, getString(R.string.sign_in_error), Toast.LENGTH_SHORT);

            }
        } else if (requestCode == RC_PREF) {
            if (resultCode == RESULT_OK) {
                int newDayNightValue = data.getIntExtra("dayNightValue", 1);

                if (newDayNightValue != dayNightValue) recreate();

                commentNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_comment), true);
                Log.d(TAG, "commentNotifValue: " + commentNotifValue);
                mDataSource.ToggleNotifications(COMMENTS_NOTIFICATION, commentNotifValue);

                articleNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_article), true);
                Log.d(TAG, "articleNotifValue: " + articleNotifValue);
                if (articleNotifValue) {
                    if (!mSharedPreferences.getBoolean("isRecArticlesScheduled", false)) {
                        mDataSource.scheduleRecArticlesPush();
                        mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", true).apply();
                    }
                } else {
                    mDataSource.cancelRecArticlesPush();
                    mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", false).apply();
                }
                mDataSource.ToggleNotifications(REC_ARTICLES_NOTIFICATION, articleNotifValue);

                dealsNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_deals), true);
                Log.d(TAG, "dealsNotifValue: " + dealsNotifValue);
                if (dealsNotifValue) {
                    if (!mSharedPreferences.getBoolean("isRecDealsScheduled", false)) {
                        mDataSource.scheduleRecDealsPush();
                        mSharedPreferences.edit().putBoolean("isRecDealsScheduled", true).apply();
                    }
                } else {
                    mDataSource.cancelRecDealsPush();
                    mSharedPreferences.edit().putBoolean("isRecDealsScheduled", false).apply();
                }
                mDataSource.ToggleNotifications(REC_DEALS_NOTIFICATION, dealsNotifValue);

                savedArticlesReminderNotifValue = mSharedPreferences.getBoolean(
                        getString(R.string.pref_key_notif_saved_articles_reminder), true);
                Log.d(TAG, "savedArticlesReminderNotifValue: " + savedArticlesReminderNotifValue);
                mDataSource.ToggleNotifications(SAVED_ARTICLES_REMINDER_NOTIFICATION, savedArticlesReminderNotifValue);

                boolean locationNotifValue = mGeofenceUtils.getGeofencesAdded(this);
                Log.d(TAG, "locationNotifValue: " + locationNotifValue);
                if (mGeofenceUtils.mPreChangeValue != locationNotifValue) {
                    if (locationNotifValue) {
                        if (!mLocationPermissionsUtils.CheckPlayServices()) {
                            createToast(this, "Please update Google Play Services to enable this feature", Toast.LENGTH_SHORT);
                            mSharedPreferences.edit()
                                    .putBoolean(getString(R.string.pref_key_notif_location), mGeofenceUtils.mPreChangeValue)
                                    .apply();
                            return;
                        }
//                        mLocationPermissionsUtils.requestLocationPermissions(() -> {
                            mLocationPermissionsUtils.checkLocationSettings((location) -> {
                                mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.ADD;
                                mGeofenceUtils.performPendingGeofenceTask(this, location);
                                mDataSource.ToggleNotifications(LOCATION_NOTIFICATION, locationNotifValue);
                            });
//                        });
                    } else {
                        mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.REMOVE;
                        mGeofenceUtils.performPendingGeofenceTask(this, null);
                        mDataSource.ToggleNotifications(LOCATION_NOTIFICATION, locationNotifValue);
                    }
                }

                boolean videosInFeed = mSharedPreferences.getBoolean(
                        getString(R.string.pref_key_feed_videos), true);
                Log.d(TAG, "videosInFeed: " + videosInFeed);
                mDataSource.setVideosInFeedPreference(videosInFeed);

                String[] channelsToAdd = data.getStringArrayExtra("channelsToAdd");
                for (String channel : channelsToAdd) {
                    Log.d(TAG, "channelToAdd: " + channel);
                    mDataSource.setVideosInFeedPreference(channel, true);
                }
            }
        } else if (requestCode == RC_THEME_PREF) {
            if (resultCode == RESULT_OK) {
                mUserThemePrefs = new ArrayList<>();
                mUserThemePrefs.addAll(data.getStringArrayListExtra("themePrefs"));
                mUserViewModel.buildThemeKeyAndFilter(mUserThemePrefs);

                if (navMenu == null) {
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    navMenu = (Menu) navigationView.getMenu();
                }
                navMenu.findItem(R.id.nav_subscriptions).setChecked(true);
                getThemeData();
            }
        } else if (requestCode == RC_NOTIF) {
            mSwipeRefreshLayout.setRefreshing(false);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

    }


    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mBannerAdView.loadAd();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mUserStatusRef != null) mUserStatusRef.removeEventListener(mUserStatusListener);
        if (mUserPremiumStatusRef != null) mUserPremiumStatusRef.removeEventListener(mUserPremiumStatusListener);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        if (mUserStatusRef != null) mUserStatusRef.addValueEventListener(mUserStatusListener);
        if (mUserPremiumStatusRef != null) mUserPremiumStatusRef.addValueEventListener(mUserPremiumStatusListener);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (!isChangingConfigurations()) {
            removeAllObservers();
        }
    }

    private void removeAllObservers() {
        if (mObservedList.size() > 0) {
            for (FeedListLiveData liveData : mObservedList.keySet()) {
                liveData.removeObserver(mObservedList.get(liveData));
            }
            mObservedList.clear();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG,"onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putParcelable("Query", mQuery);
        mLlmState = mLinearLayoutManager.onSaveInstanceState();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.d(TAG,"onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
//        mHandler.postDelayed(() -> {
//            mLinearLayoutManager.onRestoreInstanceState(mLlmState);
//        }, 2000);
    }

    private void handleDynamicLink(Intent intent) {
        FirebaseDynamicLinks.getInstance().getDynamicLink(intent)
                .addOnSuccessListener(this, pendingDynamicLinkData -> {
                    Uri deepLink;
                    if (pendingDynamicLinkData != null) {
                        deepLink = pendingDynamicLinkData.getLink();
                        Log.d(TAG, deepLink.toString() + ", " + deepLink.getLastPathSegment());
                        String lastSegment = deepLink.getLastPathSegment();
                        if (lastSegment != null && lastSegment.equals("article")) {
                            String articleId = deepLink.getQueryParameter("id");
                            mReferredBy = deepLink.getQueryParameter("sharerId");
                            Intent webviewActivity = new Intent(this, WebViewActivity.class);
                            webviewActivity.putExtra("id", articleId);
                            startActivityForResult(webviewActivity, RC_NOTIF);
                        } else if (lastSegment != null && lastSegment.equals("video")) {
                            String id = deepLink.getQueryParameter("id");
                            if (id != null) {
                                String videoId = id.substring(3);
                                mReferredBy = deepLink.getQueryParameter("sharerId");
                                Intent youtubeActivity = new Intent(this, YouTubeActivity.class);
                                youtubeActivity.putExtra("videoId", videoId);
                                startActivityForResult(youtubeActivity, RC_NOTIF);
                            }
                        } else {
                            mReferredBy = deepLink.getQueryParameter("referrer");
                            Log.d(TAG, "handleDynamicLink: " + deepLink.toString() + ", referrer: " + mReferredBy);
                        }
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.d(TAG, "Failed to get deep link: " + e);
                });
    }

    private void handleIntent(Intent intent) {
        Log.d(TAG, "handle intent");

        if (mFeedViewModel != null && mSelectedFeed != null) {
            if (mSelectedFeed.equals("Subscriptions") || mSelectedFeed.equals("Trending")) {
                String theme = mToolbarSpinner.getSelectedItem().toString();
                Log.d(TAG, "theme: " + theme + ", selectedFeed: " + mSelectedFeed);
                if (theme.equals("All")) {
                    if (mSelectedFeed.equals("Subscriptions")) {
                        getThemeData();
                    } else if (mSelectedFeed.equals("Trending")) {
                        getTrendingData();
                    }
                } else {
                    String themeRef = SEARCH_REF + "/" + theme + "/hits";
                    String searchKey = theme;
                    String searchFilter = "mainTheme: \"" + theme + "\"";
                    mDataSource.getSearchData(searchKey, searchFilter, () -> {
                        mLlmState = null;
                        mQuery = new FbQuery(-1, themeRef, "trendingIndex");
                        mThemeList.clear();
                        mThemeList.add(theme);
                        mSeed = (new Random()).nextInt(100);
                        Log.d(TAG, "seed: " + mSeed + ", themeList: " + mThemeList);
                        resetView();
                    });
                }
            } else if (mSelectedFeed.equals("Deals")) {
                getDealsData();
            }
        } else {
            getThemeData();
        }
    }

//    private void loadMoreArticles() {
//        if (isLoadingMore) return;
//
//        int currentPosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
//        final int trigger = 5;
//        final int initialListCount = mAdapter.getItemCount();
//        List<Object> currentList = mAdapter.getList();
//        final Object index;
//
//        if (initialListCount <= trigger) return;
//
//        if (currentPosition > mAdapter.getItemCount() - trigger) {
//            isLoadingMore = true;
//
//            Article lastArticle = mAdapter.getLastItem();
//
//            switch (mQuery.state) {
//                case 0:
//                    index = lastArticle.getPubDate();
//                    break;
//                case 1:
//                    index = lastArticle.getTrendingIndex();
//                    break;
//                default:
//                case 3:
//                    index = lastArticle.getTrendingIndex();
//                    break;
//                case 4:
//                    index = lastArticle.getTrendingIndex();
//                    break;
//                case -1:
//                    index = lastArticle.getTrendingIndex();
//                    break;
//                case -2:
//                    index = lastArticle.getTrendingIndex();
//                    break;
//            }
//
//            int indexType = 0;
//            ArticleListLiveData addListLD = mArticleViewModel.getAdditionalArticles(index, indexType, mThemeList, mSeed);
//            Observer<List<Object>> addListObserver = articles -> {
//                if (articles != null) {
//                    /*
//                    initialListCount marks where the end of the list was before additional
//                    articles are loaded. Live data list of additional articles will start
//                    from the last article in the current list, so startIndex is initialListCount - 1
//                    */
//                    int startIndex = initialListCount - 1;
//                    for (int i = 0; i < articles.size(); i++) {
//                        if (currentList.size() < startIndex + i + 1) {
//                            Log.d(TAG, "add: " + (startIndex + i));
//                            currentList.add(startIndex + i, articles.get(i));
//                        } else {
//                            Log.d(TAG, "set: " + (startIndex + i));
//                            currentList.set(startIndex + i, articles.get(i));
//                        }
//                    }
//                    mAdapter.setList(currentList);
//                }
//            };
//            addListLD.observeForever(addListObserver);
//            mObservedList.put(addListLD, addListObserver);
//
//            mHandler.postDelayed(()->isLoadingMore = false,1000);
//        }
//    }

    private void clearView() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mSwipeRefreshLayout.setRefreshing(true);
        mRecyclerView.scrollToPosition(0);

        mAdapter.clear();
        removeAllObservers();
    }

    private void resetView() {
        Log.d(TAG, "resetView");
        clearView();

        mAdapter = new FeedAdapter(this, this);
        mRecyclerView.setAdapter(mAdapter);

        setUpInitialViewModelObserver();
    }

    private void launchLogin() {
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(Arrays.asList(
                                new AuthUI.IdpConfig.GoogleBuilder().build(),
                                new AuthUI.IdpConfig.FacebookBuilder().build(),
                                new AuthUI.IdpConfig.EmailBuilder().build()))
                        .setLogo(R.drawable.ic_acorn)
                        .build(),
                RC_SIGN_IN);
    }

    private void logout() {
        mReferredBy = null;
        clearView();
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(task -> {
                    // user is now signed out
                    mAdapter.clear();
                    if (mUserStatusRef != null) mUserStatusRef.removeEventListener(mUserStatusListener);
                    if (mUserPremiumStatusRef != null) mUserPremiumStatusRef.removeEventListener(mUserPremiumStatusListener);
                    launchLogin();
                });
    }

    private void setupUser(FirebaseUser user) {
        Log.d(TAG, "setupUser");

        // Set up view model
        UserViewModelFactory factory = InjectorUtils.provideUserViewModelFactory(this, getApplicationContext());
        mUserViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);
        mUserViewModel.setupUser(user, mReferredBy, () -> {
            if (mSavedInstanceState != null) {
                mQuery = mSavedInstanceState.getParcelable("Query");
                if (mQuery == null) {
                    String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
                    mQuery = new FbQuery(3, hitsRef, "trendingIndex");
                }
            } else {
                String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
                mQuery = new FbQuery(3, hitsRef, "trendingIndex");
            }

            // set up feed
            if (mThemeList == null) mThemeList = new ArrayList<>(mUserThemePrefs);
            if (mSelectedFeed == null) mSelectedFeed = "Subscriptions";
            if (mSeed == null) mSeed = new Random().nextInt(100);
            if (mLlmState == null) {
                getThemeData();
            } else {
                setUpInitialViewModelObserver();
            }

            // set up toolbar
            if (mSelectedFeed.equals("Deals")) {
                setupToolbarTitleSpinner(false, null);
            } else {
                setupToolbarTitleSpinner(true, mThemeList);
            }

            // set up search button
            mDataSource.setupAlgoliaClient(() -> {
                mSearcher = Searcher.create(ALGOLIA_APP_ID, ALGOLIA_API_KEY, ALGOLIA_INDEX_NAME);
                if (mSearchButton != null) mSearchButton.setEnabled(true);
            });
        });
        mUserViewModel.themeSearchKey.observe(this, value -> {
            mThemeSearchKey = value;
        });
        mUserViewModel.themeSearchFilter.observe(this, value -> {
            mThemeSearchFilter = value;
        });
        mUserViewModel.isFirstTimeLogin.observe(this, bool -> {
            isFirstTimeLogin = bool;
        });
        mUserViewModel.isUserAuthenticated.observe(this, bool -> {
            isUserAuthenticated = bool;
        });
        mUserViewModel.sendSurveyRequest.observe(this, bool -> {
            if (bool) {
                sendSurveyRequest();
            }
        });
        mUserViewModel.showEmailVerificationAlert.observe(this, bool -> {
            if (bool) {
                showEmailVerificationAlert();
            }
        });
        mUserViewModel.showLocationPermissionsRequest.observe(this, bool -> {
            if (bool) {
                showLocationPermissionsRequest();
            }
        });
        mUserViewModel.themePrefs.observe(this, prefs -> {
            mUserThemePrefs = prefs;
        });
        mUserViewModel.getUser(mFirebaseUser.getUid()).observe(this, retrievedUser -> {
            if (retrievedUser != null) {
                mUid = retrievedUser.getUid();
                mUsername = retrievedUser.getDisplayName();
                mUserToken = retrievedUser.getToken();
                mUserPremiumStatus = retrievedUser.premiumStatus;
                Log.d(TAG, "premiumStatus: " + mUserPremiumStatus.toString());
                mUserStatus = mUserViewModel.setUserStatus(retrievedUser.getStatus());

                if (mUsernameTextView != null) {
                    mUsernameTextView.setText(mUsername);
                    mUsernameTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                }
                if (mUserStatusTextView != null) {
                    mUserStatusTextView.setText(mUserStatus);
                    mUserStatusTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                }

                if (mUserPremiumStatusCardView != null && mUserPremiumStatusTextView != null) {
                    Long end = mUserPremiumStatus.get("end");
                    if (end != null && end > (new Date()).getTime()) {
                        mUserPremiumStatusTextView.setText("Premium");
                        mUserPremiumStatusCardView.setCardBackgroundColor(getColor(R.color.colorPrimary));
                    }
                }
            }
        });
    }

//    private void setupUser(FirebaseUser user) {
//        Log.d(TAG, "setupUser");
//        if (user == null) {
//            launchLogin();
//        } else {
//            TaskCompletionSource<User> userSource = new TaskCompletionSource<>();
//            Task<User> userTask = userSource.getTask();
//
//            DatabaseReference userRef = mDatabaseReference.child("user/" + user.getUid());
//            setupUserStatusListeners(userRef);
////            userRef.keepSynced(true);
//            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                    User retrievedUser = dataSnapshot.getValue(User.class);
//                    userSource.trySetResult(retrievedUser);
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError databaseError) {
//                    userSource.trySetException(databaseError.toException());
//                }
//            });
//
//            TaskCompletionSource<String> tokenSource = new TaskCompletionSource<>();
//            Task<String> tokenTask = tokenSource.getTask();
//
//            FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(instanceIdResult -> {
//                tokenSource.trySetResult(instanceIdResult.getToken());
//            }).addOnFailureListener(tokenSource::trySetException);
//
//            Tasks.whenAll(userTask, tokenTask).addOnSuccessListener(aVoid -> {
//                User retrievedUser = userTask.getResult();
//                String userToken = tokenTask.getResult();
//
//                if (retrievedUser == null) {
//                    isFirstTimeLogin = true;
//                    if (!user.isEmailVerified()) {
//                        user.sendEmailVerification();
//                    } else {
//                        isUserAuthenticated = true;
//                    }
//                    String uid = user.getUid();
//                    String displayName = user.getDisplayName();
//                    String email = user.getEmail();
//                    String device = DeviceName.getDeviceName();
//                    Long creationTimeStamp = user.getMetadata().getCreationTimestamp();
//                    Long lastSignInTimeStamp = user.getMetadata().getLastSignInTimestamp();
//
//                    User newUser = new User(uid, displayName, userToken, email, device,
//                            creationTimeStamp, lastSignInTimeStamp);
//                    if (isUserAuthenticated) newUser.isEmailVerified = true;
//                    newUser.openedSinceLastReport = true;
//                    userRef.setValue(newUser);
//
//                    mUid = newUser.getUid();
//                    mUsername = newUser.getDisplayName();
//                    mUserToken = newUser.getToken();
//                    mUserPremiumStatus = new HashMap<>();
//                    mUserStatus = LEVEL_0;
//                    lastRecArticlesPushTime = 0L;
//                    lastRecArticlesScheduleTime = 0L;
//                    lastRecDealsPushTime = 0L;
//                    lastRecDealsScheduleTime = 0L;
//
//                    if (mUsernameTextView != null) {
//                        mUsernameTextView.setText(mUsername);
//                        mUsernameTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
//                    }
//                    if (mUserStatusTextView != null) {
//                        mUserStatusTextView.setText(mUserStatus);
//                        mUserStatusTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
//                    }
//
//                    mUserThemePrefs = new ArrayList<>();
//                    Intent editThemeIntent = new Intent(AcornActivity.this, ThemeSelectionActivity.class);
//                    editThemeIntent.putStringArrayListExtra("themePrefs", mUserThemePrefs);
//                    startActivityForResult(editThemeIntent, RC_THEME_PREF);
//
//                    // Set up Crashlytics identifier
//                    Crashlytics.setUserIdentifier(mUid);
//                    Crashlytics.setUserName(mUsername);
//                    Crashlytics.setUserEmail(email);
//
//                    // Set up Firebase Analytics identifier
//                    mFirebaseAnalytics.setUserId(mUid);
//
//                    // Set up default settings
//                    mSharedPreferences.edit().putBoolean(getString(R.string.pref_key_notif_comment), true)
//                            .putBoolean(getString(R.string.pref_key_notif_article), true)
//                            .putBoolean(getString(R.string.pref_key_notif_deals), true)
//                            .putBoolean(getString(R.string.pref_key_notif_saved_articles_reminder), true)
//                            .putString(getString(R.string.pref_key_night_mode), "0")
//                            .putBoolean(getString(R.string.pref_key_feed_videos), true)
//                            .apply();
//
//                    // Check if referred by someone
//                    checkReferral();
//                } else {
//
//                    if (!user.isEmailVerified() && !retrievedUser.isEmailVerified) {
//                        AlertDialog.Builder builder = new AlertDialog.Builder(AcornActivity.this);
//                        builder.setMessage("Please verify your email address")
//                                .setNeutralButton("Re-send verification email", (dialog, which) -> {
//                                    user.sendEmailVerification();
//                                });
//                        AlertDialog alertDialog = builder.create();
//                        alertDialog.show();
//                    } else {
//                        isUserAuthenticated = true;
//                    }
//
//                    retrievedUser.setDisplayName(user.getDisplayName());
//                    retrievedUser.setEmail(user.getEmail());
//                    retrievedUser.setDevice(DeviceName.getDeviceName());
//                    retrievedUser.setLastSignInTimeStamp(user.getMetadata().getLastSignInTimestamp());
//                    retrievedUser.setToken(userToken);
//                    retrievedUser.openedSinceLastReport = true;
//                    if (isUserAuthenticated) retrievedUser.isEmailVerified = true;
//
//                    userRef.updateChildren(retrievedUser.toMap());
//
//                    mUid = retrievedUser.getUid();
//                    mUsername = retrievedUser.getDisplayName();
//                    mUserToken = retrievedUser.getToken();
//                    mUserPremiumStatus = retrievedUser.premiumStatus;
//                    Log.d(TAG, "premiumStatus: " + mUserPremiumStatus.toString());
//                    mUserStatus = setUserStatus(retrievedUser.getStatus());
//                    mUserThemePrefs = retrievedUser.getSubscriptions();
//                    if (mUserThemePrefs.size() < 1) {
//                        mUserThemePrefs = new ArrayList<>();
//                        String[] themeArray = getResources().getStringArray(R.array.theme_array);
//                        Collections.addAll(mUserThemePrefs, themeArray);
//                    }
//                    Log.d(TAG, "themesPrefs: " + mUserThemePrefs.toString());
//                    lastRecArticlesPushTime = retrievedUser.getLastRecArticlesPushTime();
//                    lastRecArticlesScheduleTime = retrievedUser.getLastRecArticlesScheduleTime();
//                    lastRecDealsPushTime = retrievedUser.getLastRecDealsPushTime();
//                    lastRecDealsScheduleTime = retrievedUser.getLastRecDealsScheduleTime();
//
//                    buildThemeKeyAndFilter(mUserThemePrefs);
//
//                    if (mSavedInstanceState != null) {
//                        mQuery = mSavedInstanceState.getParcelable("Query");
//                        if (mQuery == null) {
//                            String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
//                            mQuery = new FbQuery(3, hitsRef, "trendingIndex");
//                        }
//                    } else {
//                        String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
//                        mQuery = new FbQuery(3, hitsRef, "trendingIndex");
//                    }
//
//                    // set up feed
//                    if (mThemeList == null) mThemeList = new ArrayList<>(mUserThemePrefs);
//                    if (mSelectedFeed == null) mSelectedFeed = "Subscriptions";
//                    if (mSeed == null) mSeed = new Random().nextInt(100);
//                    if (mLlmState == null) {
//                        getThemeData();
//                    } else {
//                        setUpInitialViewModelObserver();
//                    }
//
//                    // set up toolbar
//                    if (mSelectedFeed.equals("Deals")) {
//                        setupToolbarTitleSpinner(false, null);
//                    } else {
//                        setupToolbarTitleSpinner(true, mThemeList);
//                    }
//
//                    // set up search button
//                    mDataSource.setupAlgoliaClient(() -> {
//                        mSearcher = Searcher.create(ALGOLIA_APP_ID, ALGOLIA_API_KEY, ALGOLIA_INDEX_NAME);
//                        if (mSearchButton != null) mSearchButton.setEnabled(true);
//                    });
//
//                    // Set up Crashlytics identifier
//                    Crashlytics.setUserIdentifier(mUid);
//                    Crashlytics.setUserName(mUsername);
//                    Crashlytics.setUserEmail(retrievedUser.getEmail());
//
//                    // Set up Firebase Analytics identifier
//                    mFirebaseAnalytics.setUserId(mUid);
//
//                    if (mUsernameTextView != null) {
//                        mUsernameTextView.setText(mUsername);
//                        mUsernameTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
//                    }
//                    if (mUserStatusTextView != null) {
//                        mUserStatusTextView.setText(mUserStatus);
//                        mUserStatusTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
//                    }
//
//                    if (mUserPremiumStatusCardView != null && mUserPremiumStatusTextView != null) {
//                        Long end = mUserPremiumStatus.get("end");
//                        if (end != null && end > (new Date()).getTime()) {
//                            mUserPremiumStatusTextView.setText("Premium");
//                            mUserPremiumStatusCardView.setCardBackgroundColor(getColor(R.color.colorPrimary));
//                        }
//                    }
//
//                    // Set up default settings if not set
//                    if (!mSharedPreferences.contains(getString(R.string.pref_key_notif_comment)))
//                        mSharedPreferences.edit().putBoolean(getString(R.string.pref_key_notif_comment), true).apply();
//                    if (!mSharedPreferences.contains(getString(R.string.pref_key_notif_article)))
//                        mSharedPreferences.edit().putBoolean(getString(R.string.pref_key_notif_article), true).apply();
//                    if (!mSharedPreferences.contains(getString(R.string.pref_key_notif_deals)))
//                        mSharedPreferences.edit().putBoolean(getString(R.string.pref_key_notif_deals), true).apply();
//                    if (!mSharedPreferences.contains(getString(R.string.pref_key_notif_saved_articles_reminder)))
//                        mSharedPreferences.edit().putBoolean(getString(R.string.pref_key_notif_saved_articles_reminder), true).apply();
//                    if (!mSharedPreferences.contains(getString(R.string.pref_key_night_mode)))
//                        mSharedPreferences.edit().putString(getString(R.string.pref_key_night_mode), "0").apply();
//                    if (!mSharedPreferences.contains(getString(R.string.pref_key_feed_videos)))
//                        mSharedPreferences.edit().putBoolean(getString(R.string.pref_key_feed_videos), true).apply();
//
//                    long now = (new Date()).getTime();
//                    // store stations on device, updated every week
//                    long lastUpdatedStations = mSharedPreferences.getLong("lastUpdatedStations", 0);
////                    if (lastUpdatedStations < now - 7L * 24L * 60L * 60L * 1000L) {
//                        mExecutors.networkIO().execute(() -> {
//                            mDataSource.getMrtStations(stationMap -> {
//                                for (Map.Entry<String, Map<String, Object>> entry : stationMap.entrySet()) {
//                                    String locale = entry.getKey();
//                                    Double latitude = (Double) entry.getValue().get("latitude");
//                                    Double longitude = (Double) entry.getValue().get("longitude");
//                                    if (latitude != null && longitude != null) {
//                                        dbStation station = new dbStation(latitude, longitude, locale, "MRT");
//                                        mExecutors.diskWrite().execute(() -> {
//                                            mAddressRoomDb.addressDAO().insert(station);
//                                        });
//                                    }
//                                }
//                                mSharedPreferences.edit().putLong("lastUpdatedStations", now).apply();
//                            }, error -> {
//                                Log.d(TAG, "error getting MRT Stations: " + error.toString());
//                            });
//                        });
////                    }
//
//                    // store saved addresses on device, updated every 3 days
//                    long lastUpdatedSavedAddresses = mSharedPreferences.getLong("lastUpdatedSavedAddresses", 0);
////                    if (lastUpdatedSavedAddresses < now - 3L * 24L * 60L * 60L * 1000L) {
//                        mExecutors.networkIO().execute(() -> {
//                            mDataSource.getSavedItemsAddresses(addresses -> {
//                                Log.d(TAG, "savedAddresses: " + addresses.size());
//                                mExecutors.diskWrite().execute(() -> {
//                                    mAddressRoomDb.addressDAO().insertAddresses(addresses);
//                                    mSharedPreferences.edit().putLong("lastUpdatedSavedAddresses", now).apply();
//
//                                    mExecutors.networkIO().execute(() -> {
//                                        mLocationPermissionsUtils.checkLocationSettings((location) -> {
//                                            mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.ADD;
//                                            mGeofenceUtils.performPendingGeofenceTask(this, location);
//                                        });
//                                    });
//                                });
//                            });
//                        });
////                    }
//
//                    //TEMP: add geofences
//                    mSharedPreferences.edit().remove("hasAddedGeofences").apply();
//                    if (!mSharedPreferences.getBoolean("addedGeofences", false)) {
//                        if (mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_location), false)) {
//                            mLocationPermissionsUtils.checkLocationSettings((location) -> {
//                                mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.ADD;
//                                mGeofenceUtils.performPendingGeofenceTask(this, location);
//                            });
//                        }
//                        mSharedPreferences.edit().putBoolean("addedGeofences", true).apply();
//                    }
//
//
//                    if (retrievedUser.openedArticles.keySet().size() > 50) {
//                        boolean hasSeenSurveyRequest = mSharedPreferences.getBoolean("hasSeenSurveyRequest", false);
//                        if (!hasSeenSurveyRequest) {
//                            sendSurveyRequest();
//                            mSharedPreferences.edit()
//                                    .putBoolean("hasSeenSurveyRequest", true)
//                                    .apply();
//                        }
//                    }
//                }
//
//                // put uid in sharedPrefs
//                mSharedPreferences.edit().putString("uid", mUid).apply();
//
//                // get locations and set up geofences
//                if (!mSharedPreferences.getBoolean(getString(R.string.pref_key_loc_permissions_asked), false))
//                    showLocationPermissionsRequest();
//                // remove old request in sharedPrefs
//                mSharedPreferences.edit().remove("locationPermissionsAskedPref").apply();
//
//                // subscribe to app topic for manual articles push
//                FirebaseMessaging.getInstance().subscribeToTopic("acorn");
//
//                if (savedArticlesReminderNotifValue) {
//                    // subscribe to saved articles reminder push
//                    FirebaseMessaging.getInstance().subscribeToTopic("savedArticlesReminderPush");
//                }
//
//                // Schedule recommended articles push service unless explicitly disabled by user
//                if (articleNotifValue) {
//                    long now = (new Date()).getTime();
//                    long timeElapsedSinceLastPush = now - lastRecArticlesPushTime;
//
//                    if (!mSharedPreferences.getBoolean("isRecArticlesScheduled", false) ||
//                            (timeElapsedSinceLastPush > 24L * 60L * 60L * 1000L && // if last push time is longer than a day
//                                    lastRecArticlesScheduleTime < lastRecArticlesPushTime)) { // and last scheduled time is before last push time
//                        mDataSource.scheduleRecArticlesPush();
//                        mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", true).apply();
//                    }
//                }
//
//                // Schedule recommended deals push service unless explicitly disabled by user
//                if (dealsNotifValue) {
//                    long now = (new Date()).getTime();
//                    long timeElapsedSinceLastPush = now - lastRecDealsPushTime;
//
//                    if (!mSharedPreferences.getBoolean("isRecDealsScheduled", false) ||
//                            (timeElapsedSinceLastPush > 24L * 60L * 60L * 1000L && // if last push time is longer than a day
//                                    lastRecDealsScheduleTime < lastRecDealsPushTime)) { // and last scheduled time is before last push time
//                        mDataSource.scheduleRecDealsPush();
//                        mSharedPreferences.edit().putBoolean("isRecDealsScheduled", true).apply();
//                    }
//                }
//            }).addOnFailureListener(e -> {
//                createToast(this, "Failed to get your user profile", Toast.LENGTH_SHORT);
//            });
//        }
//    }

    public void showEmailVerificationAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Please verify your email address")
                .setNeutralButton("Re-send verification email", (dialog, which) -> {
                    mFirebaseUser.sendEmailVerification();
                });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void sendSurveyRequest() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Help Shape the Future of Acorn!");
        builder.setIcon(R.drawable.ic_launcher);
        String message = "Thanks for supporting Acorn, " + mUsername.split(" ")[0] +
                "! Having reached 50 articles read, we would love to hear your opinion on the app. " +
                "Would you like to help improve Acorn?";
        builder.setMessage(message);
        builder.setNegativeButton("No", (dialog, which) -> {
            mDataSource.logSurveyResponse(false);
            dialog.dismiss();
        });
        builder.setPositiveButton("Yes", (dialog, which) -> {
            mDataSource.logSurveyResponse(true);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLSdrEHdVUB5M7ouMTihf5Y02yGhGytPK0-xjY427TcedfBMCBQ/viewform?usp=sf_link"));
            startActivity(browserIntent);
        });
        builder.show();
    }

    private void createBackPressedDialog() {
        MenuItem subscriptionsMenuItem = (MenuItem) navMenu.findItem(R.id.nav_subscriptions);
        MenuItem trendingMenuItem = (MenuItem) navMenu.findItem(R.id.nav_trending);
        MenuItem dealsMenuItem = (MenuItem) navMenu.findItem(R.id.nav_deals);

        String subscriptions = getResources().getString(R.string.nav_subscriptions);
        String trending = getResources().getString(R.string.nav_trending);
        String deals = getResources().getString(R.string.nav_deals);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //builder.setIcon(R.drawable.ic_launcher);
        builder.setTitle("Which screen would you like to return to?");

        final ArrayAdapter<String> arrayAdapter =
                new ArrayAdapter<>(this, R.layout.item_simple_list);
        arrayAdapter.add(subscriptions);
        arrayAdapter.add(trending);
        arrayAdapter.add(deals);
        arrayAdapter.add("Exit App");

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.setAdapter(arrayAdapter, (dialog, which) -> {
            String screen = arrayAdapter.getItem(which);
            if (screen != null) {
                switch  (screen) {
                    default:
                    case "Subscriptions":
                        if (mQuery != null && mQuery.state == 3 && subscriptionsMenuItem.isChecked()) break;
                        getThemeData();
                        subscriptionsMenuItem.setChecked(true);
                        break;
                    case "Trending":
                        if (mQuery != null && mQuery.state == 3 && trendingMenuItem.isChecked()) break;
                        getTrendingData();
                        trendingMenuItem.setChecked(true);
                        break;
                    case "Deals":
                        if (mQuery != null && mQuery.state == 4) break;
                        getDealsData();
                        dealsMenuItem.setChecked(true);
                        break;
                    case "Exit App":
                        finish();
                        break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onLongClick(Article article, int id, String text) {
        if (id == R.id.card_theme) {
            String themeRef = SEARCH_REF + "/" + article.getMainTheme() + "/hits";
            String searchKey = article.getMainTheme();
            String searchFilter = "mainTheme: \"" + article.getMainTheme() + "\"";
            mDataSource.getSearchData(searchKey, searchFilter, ()->{
                mLlmState = null;
                mQuery = new FbQuery(-1, themeRef, "trendingIndex");
                mThemeList.clear();
                mThemeList.add(article.getMainTheme());
                mSeed = (new Random()).nextInt(100);
                Log.d(TAG, "seed: " + mSeed + ", themeList: " + mThemeList);
                resetView();
            });
        } else if (id == R.id.card_contributor) {
            String sourceRef = SEARCH_REF + "/" + article.getSource().replaceAll("[.#$\\[\\]]", "") + "/hits";
            String searchKey = article.getSource();
            String searchFilter = "source: \"" + article.getSource() + "\"";
            mDataSource.getSearchData(searchKey, searchFilter, ()->{
                mLlmState = null;
                mQuery = new FbQuery(-2, sourceRef, "trendingIndex");
                resetView();
            });
        }
    }

    private void getThemeData() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mNewContentPrompt.setVisibility(View.GONE);
        mSwipeRefreshLayout.setRefreshing(true);
        String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
        mDataSource.getThemeData(()->{
            mLlmState = null;
            mQuery = new FbQuery(3, hitsRef, "trendingIndex");
            mThemeList = new ArrayList<>(mUserThemePrefs);
            mSeed = (new Random()).nextInt(100);
            Log.d(TAG, "seed: " + mSeed + ", themeList: " + mThemeList);
            resetView();
        });
    }

    private void getTrendingData() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mNewContentPrompt.setVisibility(View.GONE);
        mSwipeRefreshLayout.setRefreshing(true);

        StringBuilder searchKeyBuilder = new StringBuilder();

        String[] themePrefs = getResources().getStringArray(R.array.theme_array);
        Arrays.sort(themePrefs);
        for (int i = 0; i < themePrefs.length; i++) {
            if (i == 0) {
                searchKeyBuilder.append(themePrefs[i]);
            } else {
                searchKeyBuilder.append("_").append(themePrefs[i]);
            }
        }
        mAllThemesSearchKey = searchKeyBuilder.toString();

        String hitsRef = SEARCH_REF + "/" + mAllThemesSearchKey + "/hits";
        mDataSource.getTrendingData(()->{
            mLlmState = null;
            mQuery = new FbQuery(3, hitsRef, "trendingIndex");
            List<String> themeList = new ArrayList<>();
            Collections.addAll(themeList, themePrefs);
            mThemeList = new ArrayList<>(themeList);
            mSeed = (new Random()).nextInt(100);
            Log.d(TAG, "seed: " + mSeed + ", themeList: " + mThemeList);
            resetView();
        });
    }

    private void getDealsData() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mNewContentPrompt.setVisibility(View.GONE);
        mSwipeRefreshLayout.setRefreshing(true);
        String hitsRef = SEARCH_REF + "/Deals/hits";
        mDataSource.getDealsData(()->{
            mLlmState = null;
            mQuery = new FbQuery(4, hitsRef, "trendingIndex");
            mThemeList.clear();
            mThemeList.add("Deals");
            mSeed = (new Random()).nextInt(100);
            Log.d(TAG, "seed: " + mSeed + ", themeList: " + mThemeList);
            resetView();
        });
    }

    private void setUpInitialViewModelObserver() {
        Log.d(TAG, "setUpInitialViewModelObserver: theme:" + mThemeList + ", seed: " + mSeed + ", query: " + mQuery.state);

        // Set up view model
        FeedViewModelFactory factory = InjectorUtils.provideArticleViewModelFactory(this.getApplicationContext());
        mFeedViewModel = new ViewModelProvider(this, factory).get(FeedViewModel.class);

        FeedListLiveData itemListLD = mFeedViewModel.getArticles(mQuery, mThemeList, mSeed);
        Observer<List<Object>> itemListObserver = items -> {
            if (items != null) {
                long lastFeedRefreshTime = mSharedPreferences.getLong("lastFeedRefreshTime", 0L);
                long now = (new Date()).getTime();
                int cutoff = 10;
                if (mAdapter.getItemCount() < cutoff || lastFeedRefreshTime < (now - 60 * 60 * 1000)) {
                    Log.d(TAG, "refresh feed");
                    mAdapter.setList(items, () -> {
                        // add ads to feed
                        if (mNativeAds.size() < 1 || mNativeAds.get(0).isAdInvalidated()) {
                            loadNativeAds(this::addAdsToFeed);
                        } else {
                            addAdsToFeed();
                        }

                        if (mPendingRestoreState) mHandler.removeCallbacks(restoreLlmState);
                        mHandler.postDelayed(restoreLlmState, 200);
                        mPendingRestoreState = true;
                    });
                    mSharedPreferences.edit().putLong("lastFeedRefreshTime", now).apply();
                } else {
                    int diffCount = 0;
                    List<String> newIds = new ArrayList<>();
                    for (int i = 0; i < cutoff; i++) {
                        if (items.get(i) instanceof Article) {
                            newIds.add(((Article) items.get(i)).getObjectID());
                        } else if (items.get(i) instanceof Video) {
                            newIds.add(((Video) items.get(i)).getObjectID());
                        }
                    }
                    int limit = Math.min(cutoff, mAdapter.getItemCount());
                    for (String id : mAdapter.getFirstIdList(limit)) {
                        if (!newIds.contains(id)) {
                            diffCount += 1;
                        }
                    }

                    // new articles are available
                    if (diffCount > 5) {
                        Log.d(TAG, "new articles available: " + diffCount);
                        mNewContentPrompt.setOnClickListener(v -> {
                            mAdapter.setList(items, () -> {
                                // add ads to feed
                                if (mNativeAds.size() < 1 || mNativeAds.get(0).isAdInvalidated()) {
                                    loadNativeAds(this::addAdsToFeed);
                                } else {
                                    addAdsToFeed();
                                }
                            });
                            mRecyclerView.scrollToPosition(0);
                            fadeOut(mNewContentPrompt);
                        });

                        fadeIn(mNewContentPrompt);
                    } else {
                        Log.d(TAG, "article change");
                        mAdapter.setList(items, () -> {
                            // add ads to feed
                            if (mNativeAds.size() < 1 || mNativeAds.get(0).isAdInvalidated()) {
                                loadNativeAds(this::addAdsToFeed);
                            } else {
                                addAdsToFeed();
                            }
                        });
                    }
                }
            }
        };
        itemListLD.observe(this, itemListObserver);
        mObservedList.put(itemListLD, itemListObserver);
    }

    private void loadNativeAds(Runnable onComplete) {
        if (!pendingAdLoad) {
            pendingAdLoad = true;

            Log.d(TAG, "loadNativeAds");
            List<Integer> doneList = new ArrayList<>();
            for (int i = 0; i < AD_COUNT; i++) {
                final int index = i;
                NativeAd nativeAd = new NativeAd(this,
                        getString(R.string.fb_native_ad_placement_id));

                nativeAd.setAdListener(new NativeAdListener() {
                    @Override
                    public void onMediaDownloaded(Ad ad) {
                    }

                    @Override
                    public void onError(Ad ad, AdError adError) {
                        doneList.add(index);
                        Log.d(TAG, "error loading ad: " + adError.getErrorMessage());

                        if (doneList.size() == AD_COUNT) {
                            onComplete.run();
                            pendingAdLoad = false;
                        }
                    }

                    @Override
                    public void onAdLoaded(Ad ad) {
                        Log.d(TAG, "ad loaded: " + ad.getPlacementId());
                        mNativeAds.add(nativeAd);
                        doneList.add(index);

                        if (doneList.size() == AD_COUNT) {
                            onComplete.run();
                        }
                    }

                    @Override
                    public void onAdClicked(Ad ad) {
                    }

                    @Override
                    public void onLoggingImpression(Ad ad) {
                    }
                });

                // Request an ad
                nativeAd.loadAd();
            }
        }
    }

    private void addAdsToFeed() {
        int index = 3;
        int interval = 10;
        for (NativeAd ad : mNativeAds) {
            if (index < mAdapter.getItemCount()) {
                mAdapter.mItemList.add(index, ad);
                mAdapter.notifyItemInserted(index);
                index += interval;
            }
        }
    }

    private void fadeIn(View view) {
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new AccelerateInterpolator());
        fadeIn.setStartOffset(0);
        fadeIn.setDuration(500);
        view.setAnimation(fadeIn);
        view.setVisibility(View.VISIBLE);
    }

    private void fadeOut(View view) {
        Animation fadeOut = new AlphaAnimation(1, 0);
        fadeOut.setInterpolator(new AccelerateInterpolator());
        fadeOut.setStartOffset(0);
        fadeOut.setDuration(500);
        view.setAnimation(fadeOut);
        view.setVisibility(View.GONE);
    }

    private Runnable restoreLlmState = () -> {
        if (mLlmState != null) {
            mLinearLayoutManager.onRestoreInstanceState(mLlmState);
            mLlmState = null;
        }

        if (mRecyclerView.getVisibility() != View.VISIBLE) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mSwipeRefreshLayout.setRefreshing(false);
        }
        mPendingRestoreState = false;
        Log.d(TAG, "restore instance state");
    };

    private int getDeviceWidth() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        Log.d(TAG, "width: " + size.x);
        return size.x;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch(requestCode) {
            case LocationPermissionsUtils.LOCATION_PERMISSIONS_RC:
                for (String perm : mLocationPermissionsUtils.permissionsToRequest) {
                    if (!mLocationPermissionsUtils.hasPermission(perm)) {
                        mLocationPermissionsUtils.permissionsRejected.add(perm);
                    }
                }

                if (mLocationPermissionsUtils.permissionsRejected.size() > 0) {
                    if (shouldShowRequestPermissionRationale(mLocationPermissionsUtils.permissionsRejected.get(0))) {
                        new AlertDialog.Builder(this)
                                .setMessage("Please enable locations permissions to receive recommendations near you.")
                                .setPositiveButton("OK", (dialogInterface, i) ->
                                        requestPermissions(mLocationPermissionsUtils.permissionsRejected
                                                .toArray(new String[mLocationPermissionsUtils.permissionsRejected.size()]),
                                                LocationPermissionsUtils.LOCATION_PERMISSIONS_RC))
                                .setNegativeButton("Cancel", (dialog, which) -> {
                                    mSharedPreferences.edit()
                                            .putBoolean(getString(R.string.pref_key_notif_location), false)
                                            .apply();
                                }).create().show();
                        return;
                    }
                }
                mSharedPreferences.edit()
                        .putBoolean(getString(R.string.pref_key_notif_location), !mGeofenceUtils.mPreChangeValue)
                        .apply();

                mLocationPermissionsUtils.checkLocationSettings((location) -> {
                    mGeofenceUtils.mPreChangeValue = false;
                    mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.ADD;
                    mGeofenceUtils.performPendingGeofenceTask(this, location);
                });

                break;
        }
    }

    private void showLocationPermissionsRequest() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_location_request, null);

        builder.setView(view);
        builder.setNegativeButton("No", (dialog, which) -> {
            Log.d(TAG, "geofence no selected");
            mSharedPreferences.edit()
                    .putBoolean(getString(R.string.pref_key_notif_location), false)
                    .apply();
            mDataSource.ToggleNotifications(LOCATION_NOTIFICATION, false);
            dialog.dismiss();
        });
        builder.setPositiveButton("Yes", ((dialog, which) -> {
            Log.d(TAG, "geofence yes selected");
            if (!mLocationPermissionsUtils.CheckPlayServices()) {
                createToast(this, "Please update Google Play Services to enable this feature", Toast.LENGTH_SHORT);
                return;
            }
            mLocationPermissionsUtils.requestLocationPermissions(() -> {
                mSharedPreferences.edit()
                        .putBoolean(getString(R.string.pref_key_notif_location), true)
                        .apply();
                mLocationPermissionsUtils.checkLocationSettings((location) -> {
                    mGeofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.ADD;
                    mGeofenceUtils.performPendingGeofenceTask(this, location);
                });
            });
        }));

        builder.setOnDismissListener(dialog -> mSharedPreferences.edit()
                .putBoolean(getString(R.string.pref_key_loc_permissions_asked), true)
                .apply());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialog1 -> {
            ImageView imageView = view.findViewById(R.id.dialog_location_request_iv);
            LinearLayout rootView = view.findViewById(R.id.dialog_location_request_root);
            imageView.getLayoutParams().width = rootView.getWidth();
            imageView.getLayoutParams().height = Math.round(1.243f * rootView.getWidth());
        });
        dialog.show();

    }

    private void setupToolbarTitleSpinner(boolean show, @Nullable List<String> themes) {
        TextView mToolbarTitle = mToolbar.findViewById(R.id.toolbar_title);
        mToolbarSpinner = mToolbar.findViewById(R.id.toolbar_title_spinner);
        if (show) {
            ArrayAdapter<CharSequence> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
            spinnerAdapter.add("All");
            spinnerAdapter.addAll(themes);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mToolbarSpinner.setOnItemSelectedListener(this);
            mToolbarSpinner.setAdapter(spinnerAdapter);
            mToolbarSpinner.setVisibility(View.VISIBLE);
            mToolbarTitle.setVisibility(View.GONE);
        } else {
            mToolbarTitle.setVisibility(View.VISIBLE);
            mToolbarSpinner.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        TextView titleTv = (TextView) parent.getChildAt(0);
        if (titleTv != null) {
            titleTv.setTextColor(Color.WHITE);
            titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.title_font_size));
        }

        String theme = parent.getItemAtPosition(position).toString();
        if (theme.equals("All")) {
            if (titleTv != null) titleTv.setText("Acorn");
            if (mQuery != null && mQuery.state == 3) return;
            if (mSelectedFeed.equals("Subscriptions")) {
                getThemeData();
            } else if (mSelectedFeed.equals("Trending")) {
                getTrendingData();
            }
        } else {
            String themeRef = SEARCH_REF + "/" + theme + "/hits";
            String searchKey = theme;
            String searchFilter = "mainTheme: \"" + theme + "\"";
            mDataSource.getSearchData(searchKey, searchFilter, () -> {
                mLlmState = null;
                mQuery = new FbQuery(-1, themeRef, "trendingIndex");
                mThemeList.clear();
                mThemeList.add(theme);
                mSeed = (new Random()).nextInt(100);
                Log.d(TAG, "seed: " + mSeed + ", themeList: " + mThemeList);
                resetView();
            });
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        parent.setSelection(0);
    }
}