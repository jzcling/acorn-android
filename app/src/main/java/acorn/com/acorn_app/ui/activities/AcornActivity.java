package acorn.com.acorn_app.ui.activities;

import android.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.appcompat.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.algolia.instantsearch.core.helpers.Searcher;
import com.crashlytics.android.Crashlytics;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.jaredrummler.android.device.DeviceName;
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

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.ArticleListLiveData;
import acorn.com.acorn_app.data.ArticleRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.models.User;
import acorn.com.acorn_app.ui.adapters.ArticleAdapter;
import acorn.com.acorn_app.ui.fragments.NotificationsDialogFragment;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModel;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModelFactory;
import acorn.com.acorn_app.ui.viewModels.NotificationViewModel;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.InjectorUtils;

import static acorn.com.acorn_app.data.NetworkDataSource.ALGOLIA_API_KEY;
import static acorn.com.acorn_app.data.NetworkDataSource.SEARCH_REF;
import static acorn.com.acorn_app.ui.AcornApplication.mFirebaseAnalytics;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class AcornActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        ArticleAdapter.OnLongClickListener {

    private static final String TAG = "AcornActivity";

    public static final long ID_OFFSET = 5000000000000000L;
    public static final int TARGET_POINTS_MULTIPLIER = 3;

    private static final int RC_SIGN_IN = 1001;
    private static final int RC_PREF = 1002;
    public static final int RC_THEME_PREF = 1003;
    public static final int RC_SHARE = 1004;
    private static final String ARTICLE_CARD_TYPE = "card";
    private static final String ARTICLE_LIST_TYPE = "list";

    // User status
    private DatabaseReference mUserStatusRef;
    private ValueEventListener mUserStatusListener;
    public static final String LEVEL_0 = "Budding Seed";
    public static final String LEVEL_1 = "Emerging Sprout";
    public static final String LEVEL_2 = "Thriving Sapling";
    public static final String LEVEL_3 = "Wise Oak";

    //Theme
    public static boolean isFirstTimeLogin;
    public static String mThemeSearchKey;
    public static String mThemeSearchFilter;
    public static String mAllThemesSearchKey;

    //Main UI
    private DrawerLayout mDrawer;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private FloatingActionButton mScrollFab;
    private FloatingActionButton mPostFab;

    //Firebase database
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;
    public static FbQuery mQuery;

    //Room database
    private ArticleRoomDatabase mRoomDb;
    private Context mContext;
    
    //Data source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    //RecyclerView
    private RecyclerView mRecyclerView;
    private ArticleAdapter mAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private boolean isLoadingMore = false;
    private static Parcelable mLlmState;

    //View Models
    private ArticleViewModel mArticleViewModel;
    private final Map<ArticleListLiveData, Observer<List<Article>>> mObservedList = new HashMap<>();
    private NotificationViewModel mNotifViewModel;

    //User
    private FirebaseUser mFirebaseUser;
    public static boolean isUserAuthenticated = false;
    public static String mUid;
    public static String mUsername;
    public static String mUserToken;
    private String mUserStatus;
    public static ArrayList<String> mUserThemePrefs;
    private long lastRecArticlesPushTime;
    private long lastRecArticlesScheduleTime;
    private long lastRecDealsPushTime;
    private long lastRecDealsScheduleTime;
    private TextView mUsernameTextView;
    private TextView mUserStatusTextView;

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

    //Notifications
    private NotificationBadge notificationBadge;
    
    //Menu
    private Menu navMenu;
    private MenuItem mSearchButton;

    //InstantSearch
    public static Searcher mSearcher;
    private static final String ALGOLIA_APP_ID = "O96PPLSF19";
    private static final String ALGOLIA_INDEX_NAME = "article";

    private Bundle mSavedInstanceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate");
        mSavedInstanceState = savedInstanceState;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mNotificationsPref = getSharedPreferences(getString(R.string.notif_pref_id), MODE_PRIVATE);
        dayNightValue = Integer.parseInt(mSharedPreferences.getString(
                getString(R.string.pref_key_night_mode),"1"));
        commentNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_comment), true);
        articleNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_article), true);
        dealsNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_deals), true);
        savedArticlesReminderNotifValue = mSharedPreferences.getBoolean(
                getString(R.string.pref_key_notif_saved_articles_reminder), true);
        AppCompatDelegate.setDefaultNightMode(dayNightValue);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acorn);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null);

        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String hashKey = new String(Base64.encode(md.digest(), 0));
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

        // Set up room database
        mRoomDb = ArticleRoomDatabase.getInstance(this);
        mContext = this;

        // Initiate views
        mRecyclerView = (RecyclerView) findViewById(R.id.card_recycler_view);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mScrollFab = (FloatingActionButton) findViewById(R.id.scroll_fab);
        mPostFab = (FloatingActionButton) findViewById(R.id.post_fab);

        // Set up recycler view
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new ArticleAdapter(this, this);
        mRecyclerView.setAdapter(mAdapter);
        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0) {
                    mScrollFab.show();
                } else {
                    mScrollFab.hide();
                }

                loadMoreArticles();
            }
        });

        setupUser(mFirebaseUser);

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
            Intent intent = new Intent(this, CreatePostActivity.class);
            startActivity(intent);
        });

        // Set up swipe refresh
        mSwipeRefreshLayout.setOnRefreshListener(() -> handleIntent(getIntent()));

        // Set up navigation mDrawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            createBackPressedDialog();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_bar_activity_acorn, menu);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        View navHeaderLayout = navigationView.getHeaderView(0);
        mUsernameTextView = (TextView) navHeaderLayout.findViewById(R.id.nav_user_name_text_view);
        mUserStatusTextView = (TextView) navHeaderLayout.findViewById(R.id.nav_user_status_text_view);
        if (navMenu == null) navMenu = (Menu) navigationView.getMenu();
        MenuItem signInMenuItem = (MenuItem) navMenu.findItem(R.id.nav_login);
        MenuItem signOutMenuItem = (MenuItem) navMenu.findItem(R.id.nav_logout);

        MenuItem videosItem = (MenuItem) navMenu.findItem(R.id.nav_video_feed);
        videosItem.getIcon().setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);

        final MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        mSearchButton = menu.findItem(R.id.action_search);
        ConstraintLayout notificationView = (ConstraintLayout) notificationItem.getActionView();
        notificationBadge = (NotificationBadge) notificationView.findViewById(R.id.notification_badge);
        mNotifViewModel = ViewModelProviders.of(this).get(NotificationViewModel.class);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_search:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case R.id.action_notifications:
                new NotificationsDialogFragment().show(getFragmentManager(), "NotificationsDialog");
                return true;
            case R.id.action_settings:
                startActivityForResult(new Intent(this, SettingsActivity.class), RC_PREF);
                return true;
            case R.id.action_share_app:
                Intent shareIntent = new Intent();
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Get your favourite blog articles all in one app! http://acorncommunity.sg");
                shareIntent.setAction(Intent.ACTION_SEND);
                startActivity(Intent.createChooser(shareIntent, "Share app with"));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        DrawerLayout mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        int id = item.getItemId();

        switch (id) {
            case R.id.nav_subscriptions:
                if (mQuery != null && mQuery.state == 3 && item.isChecked()) break;
                getThemeData();
                break;
            case R.id.nav_trending:
                if (mQuery != null && mQuery.state == 3 && item.isChecked()) break;
                getTrendingData();
                break;
            case R.id.nav_deals:
                if (mQuery != null && mQuery.state == 4) break;
                getDealsData();
                break;
            case R.id.nav_saved:
                Intent savedArticlesIntent = new Intent(this, SavedArticlesActivity.class);
                startActivity(savedArticlesIntent);
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
        super.onNewIntent(intent);
        handleIntent(intent);
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

                if (mArticleViewModel != null) resetView();
            } else {
                // Sign in failed
                if (response == null) {
                    // User pressed back button
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

                commentNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_comment), false);
                Log.d(TAG, "commentNotifValue: " + commentNotifValue);
                mDataSource.ToggleNotifications(COMMENTS_NOTIFICATION, commentNotifValue);

                articleNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_article), false);
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

                dealsNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_deals), false);
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
                        getString(R.string.pref_key_notif_saved_articles_reminder), false);
                Log.d(TAG, "savedArticlesReminderNotifValue: " + savedArticlesReminderNotifValue);
                mDataSource.ToggleNotifications(SAVED_ARTICLES_REMINDER_NOTIFICATION, savedArticlesReminderNotifValue);
            }
        } else if (requestCode == RC_THEME_PREF) {
            if (resultCode == RESULT_OK) {
                mUserThemePrefs = new ArrayList<>();
                mUserThemePrefs.addAll(data.getStringArrayListExtra("themePrefs"));
                buildThemeKeyAndFilter(mUserThemePrefs);

                if (navMenu == null) {
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    navMenu = (Menu) navigationView.getMenu();
                }
                navMenu.findItem(R.id.nav_subscriptions).setChecked(true);
                getThemeData();
            }
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

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mUserStatusRef != null) mUserStatusRef.removeEventListener(mUserStatusListener);

    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        if (mUserStatusRef != null) mUserStatusRef.addValueEventListener(mUserStatusListener);

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
            for (ArticleListLiveData liveData : mObservedList.keySet()) {
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
        new Handler().postDelayed(() -> {
            mLinearLayoutManager.onRestoreInstanceState(mLlmState);
        }, 100);
    }

    private void handleIntent(Intent intent) {
        mSwipeRefreshLayout.setRefreshing(false);
        if (mArticleViewModel != null) {
            resetView();
        }
    }

    private void loadMoreArticles() {
        if (isLoadingMore) return;

        // Don't trigger load more for feed filtered by theme / source
        if (mQuery.state < 0) return;

        int currentPosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
        final int trigger = 5;
        final int initialListCount = mAdapter.getItemCount();
        List<Article> currentList = mAdapter.getList();
        final Object index;

        if (initialListCount <= trigger) return;


        if (currentPosition > mAdapter.getItemCount() - trigger) {
            isLoadingMore = true;

            Article lastArticle = mAdapter.getLastItem();

            switch (mQuery.state) {
                case 0:
                    index = lastArticle.getPubDate();
                    break;
                case 1:
                    index = lastArticle.getTrendingIndex();
                    break;
                default:
                case 3:
                    index = lastArticle.getTrendingIndex();
                    break;
                case 4:
                    index = lastArticle.getTrendingIndex();
                    break;
            }

            int indexType = 0;
            ArticleListLiveData addListLD = mArticleViewModel.getAdditionalArticles(index, indexType);
            Observer<List<Article>> addListObserver = articles -> {
                if (articles != null) {
                    /*
                    initialListCount marks where the end of the list was before additional
                    articles are loaded. Live data list of additional articles will start
                    from the last article in the current list, so startIndex is initialListCount - 1
                    */
                    int startIndex = initialListCount - 1;
                    for (int i = 0; i < articles.size(); i++) {
                        if (currentList.size() < startIndex + i + 1) {
                            Log.d(TAG, "add: " + (startIndex + i));
                            currentList.add(startIndex + i, articles.get(i));
                        } else {
                            Log.d(TAG, "set: " + (startIndex + i));
                            currentList.set(startIndex + i, articles.get(i));
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

    private void resetView() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mSwipeRefreshLayout.setRefreshing(true);
        mRecyclerView.scrollToPosition(0);

        mAdapter.clear();
        removeAllObservers();

        mAdapter = new ArticleAdapter(this, this);
        mRecyclerView.setAdapter(mAdapter);

        setUpInitialViewModelObserver();

        mRecyclerView.setVisibility(View.VISIBLE);
        mSwipeRefreshLayout.setRefreshing(false);
    }

    private void launchLogin() {
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(Arrays.asList(
                                new AuthUI.IdpConfig.EmailBuilder().build(),
                                new AuthUI.IdpConfig.GoogleBuilder().build(),
                                new AuthUI.IdpConfig.FacebookBuilder().build()))
                        .setLogo(R.drawable.ic_acorn)
                        .build(),
                RC_SIGN_IN);
    }

    private void logout() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(task -> {
                    // user is now signed out
                    launchLogin();
                });
    }

    private void setupUser(FirebaseUser user) {
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        View navHeaderLayout = navigationView.getHeaderView(0);
        mUsernameTextView = (TextView) navHeaderLayout.findViewById(R.id.nav_user_name_text_view);
        mUserStatusTextView = (TextView) navHeaderLayout.findViewById(R.id.nav_user_status_text_view);

        if (mFirebaseUser == null) {
            launchLogin();
        } else {
            DatabaseReference userRef = mDatabaseReference.child("user/" + user.getUid());
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    User retrievedUser = dataSnapshot.getValue(User.class);
                    FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(
                            instanceIdResult -> {
                                String userToken = instanceIdResult.getToken();
                                if (retrievedUser == null) {
                                    isFirstTimeLogin = true;
                                    if (!user.isEmailVerified()) {
                                        user.sendEmailVerification();
                                    } else {
                                        isUserAuthenticated = true;
                                    }
                                    String uid = user.getUid();
                                    String displayName = user.getDisplayName();
                                    String email = user.getEmail();
                                    String device = DeviceName.getDeviceName();
                                    Long creationTimeStamp = user.getMetadata().getCreationTimestamp();
                                    Long lastSignInTimeStamp = user.getMetadata().getLastSignInTimestamp();

                                    User newUser = new User(uid, displayName, userToken, email, device,
                                            creationTimeStamp, lastSignInTimeStamp);
                                    if (isUserAuthenticated) newUser.isEmailVerified = true;
                                    newUser.openedSinceLastReport = true;
                                    userRef.setValue(newUser);

                                    mUid = newUser.getUid();
                                    mUsername = newUser.getDisplayName();
                                    mUserToken = newUser.getToken();
                                    mUserStatus = LEVEL_0;
                                    lastRecArticlesPushTime = 0L;
                                    lastRecArticlesScheduleTime = 0L;
                                    lastRecDealsPushTime = 0L;
                                    lastRecDealsScheduleTime = 0L;

                                    if (mUsernameTextView != null) {
                                        mUsernameTextView.setText(mUsername);
                                        mUsernameTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                                    }
                                    if (mUserStatusTextView != null) {
                                        mUserStatusTextView.setText(mUserStatus);
                                        mUserStatusTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                                    }

                                    mUserThemePrefs = new ArrayList<>();
                                    Intent editThemeIntent = new Intent(AcornActivity.this, ThemeSelectionActivity.class);
                                    editThemeIntent.putStringArrayListExtra("themePrefs", mUserThemePrefs);
                                    startActivityForResult(editThemeIntent, RC_THEME_PREF);
                                    buildThemeKeyAndFilter(mUserThemePrefs);

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
                                    setUpInitialViewModelObserver();

                                    // set up search button
                                    mDataSource.setupAlgoliaClient(() -> {
                                        mSearcher = Searcher.create(ALGOLIA_APP_ID, ALGOLIA_API_KEY, ALGOLIA_INDEX_NAME);
                                        mSearchButton.setEnabled(true);
                                    });

                                    // Set up Crashlytics identifier
                                    Crashlytics.setUserIdentifier(mUid);
                                    Crashlytics.setUserName(mUsername);
                                    Crashlytics.setUserEmail(email);

                                    // Set up Firebase Analytics identifier
                                    mFirebaseAnalytics.setUserId(mUid);

                                } else {

                                    if (!user.isEmailVerified() && !retrievedUser.isEmailVerified) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(AcornActivity.this);
                                        builder.setMessage("Please verify your email address")
                                                .setNeutralButton("Re-send verification email", (dialog, which) -> {
                                                    user.sendEmailVerification();
                                                });
                                        AlertDialog alertDialog = builder.create();
                                        alertDialog.show();
                                    } else {
                                        isUserAuthenticated = true;
                                    }

                                    retrievedUser.setDisplayName(user.getDisplayName());
                                    retrievedUser.setEmail(user.getEmail());
                                    retrievedUser.setDevice(DeviceName.getDeviceName());
                                    retrievedUser.setLastSignInTimeStamp(user.getMetadata().getLastSignInTimestamp());
                                    retrievedUser.setToken(userToken);
                                    retrievedUser.openedSinceLastReport = true;
                                    if (isUserAuthenticated) retrievedUser.isEmailVerified = true;

                                    userRef.updateChildren(retrievedUser.toMap());

                                    mUid = retrievedUser.getUid();
                                    mUsername = retrievedUser.getDisplayName();
                                    mUserToken = retrievedUser.getToken();
                                    mUserThemePrefs = retrievedUser.getSubscriptions();
                                    if (mUserThemePrefs.size() < 1) {
                                        mUserThemePrefs = new ArrayList<>();
                                        String[] themeArray = getResources().getStringArray(R.array.theme_array);
                                        Collections.addAll(mUserThemePrefs, themeArray);
                                    }
                                    Log.d(TAG, "themesPrefs: " + mUserThemePrefs.toString());
                                    mUserStatus = setUserStatus(retrievedUser.getStatus());
                                    lastRecArticlesPushTime = retrievedUser.getLastRecArticlesPushTime();
                                    lastRecArticlesScheduleTime = retrievedUser.getLastRecArticlesScheduleTime();
                                    lastRecDealsPushTime = retrievedUser.getLastRecDealsPushTime();
                                    lastRecDealsScheduleTime = retrievedUser.getLastRecDealsScheduleTime();

                                    buildThemeKeyAndFilter(mUserThemePrefs);

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
                                    setUpInitialViewModelObserver();

                                    // set up search button
                                    mDataSource.setupAlgoliaClient(() -> {
                                        mSearcher = Searcher.create(ALGOLIA_APP_ID, ALGOLIA_API_KEY, ALGOLIA_INDEX_NAME);
                                        mSearchButton.setEnabled(true);
                                    });

                                    // Set up Crashlytics identifier
                                    Crashlytics.setUserIdentifier(mUid);
                                    Crashlytics.setUserName(mUsername);
                                    Crashlytics.setUserEmail(retrievedUser.getEmail());

                                    // Set up Firebase Analytics identifier
                                    mFirebaseAnalytics.setUserId(mUid);

                                    if (mUsernameTextView != null) {
                                        mUsernameTextView.setText(mUsername);
                                        mUsernameTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                                    }
                                    if (mUserStatusTextView != null) {
                                        mUserStatusTextView.setText(mUserStatus);
                                        mUserStatusTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                                    }
                                }

                                // put uid in sharedPrefs
                                mSharedPreferences.edit().putString("uid", mUid).apply();

                                // subscribe to app topic for manual articles push
                                FirebaseMessaging.getInstance().subscribeToTopic("acorn");

                                if (savedArticlesReminderNotifValue) {
                                    // subscribe to saved articles reminder push
                                    FirebaseMessaging.getInstance().subscribeToTopic("savedArticlesReminderPush");
                                }

                                // Schedule recommended articles push service unless explicitly disabled by user
                                if (articleNotifValue) {
                                    long now = (new Date()).getTime();
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
                                    long now = (new Date()).getTime();
                                    long timeElapsedSinceLastPush = now - lastRecDealsPushTime;

                                    if (!mSharedPreferences.getBoolean("isRecDealsScheduled", false) ||
                                            (timeElapsedSinceLastPush > 24L * 60L * 60L * 1000L && // if last push time is longer than a day
                                                    lastRecDealsScheduleTime < lastRecDealsPushTime)) { // and last scheduled time is before last push time
                                        mDataSource.scheduleRecDealsPush();
                                        mSharedPreferences.edit().putBoolean("isRecDealsScheduled", true).apply();
                                    }
                                }

                                // Schedule articles download
//                                boolean isArticlesDownloadScheduled = mSharedPreferences.getBoolean("isArticlesDownloadScheduled", false);
////                                if (!isArticlesDownloadScheduled) {
////                                    mDataSource.cancelDownloadArticles();
//                                    mDataSource.scheduleArticlesDownload();
//                                    Log.d(TAG, "articlesDownloadScheduled");
//                                    mSharedPreferences.edit().putBoolean("isArticlesDownloadScheduled", true);
////                                }

                                Long lastDownloadArticlesTime = mSharedPreferences.getLong("lastDownloadArticlesTime", 0L);
                                Long now = (new Date()).getTime();
//                                lastDownloadArticlesTime = 0L;
                                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                                if (isConnected) {
                                    if (lastDownloadArticlesTime == 0L || now > lastDownloadArticlesTime + 60L * 60L * 1000L) { // 1 hour
                                        mRecyclerView.setVisibility(View.INVISIBLE);
                                        mSwipeRefreshLayout.setRefreshing(true);

                                        mSharedPreferences.edit().putLong("lastDownloadArticlesTime", now).apply();

                                        mExecutors.networkIO().execute(
                                                () -> mDataSource.getTrendingArticles((articleList) -> {
                                                    mExecutors.mainThread().execute(() -> {
                                                        mRecyclerView.setVisibility(View.VISIBLE);
                                                        mSwipeRefreshLayout.setRefreshing(false);
                                                    });
                                                    Long cutOffDate = (new Date()).getTime() - 2L * 24L * 60L * 60L * 1000L; // more than 2 days ago
                                                    mExecutors.diskWrite().execute(() -> {
                                                        mRoomDb.articleDAO().deleteOld(cutOffDate);
                                                        mRoomDb.articleDAO().insert(articleList);
                                                    });
                                                }, () -> {
                                                    mExecutors.mainThread().execute(() -> {
                                                        mRecyclerView.setVisibility(View.VISIBLE);
                                                        mSwipeRefreshLayout.setRefreshing(false);
                                                    });
                                                })
                                        );
                                    }
                                }
                            });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            });

            mUserStatusListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Integer userStatus = dataSnapshot.getValue(Integer.class);
                    if (userStatus == null) userStatus = 0;
                    mUserStatus = setUserStatus(userStatus);
                    mUserStatusTextView.setText(mUserStatus);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            };
            mUserStatusRef = userRef.child("status");
            mUserStatusRef.addValueEventListener(mUserStatusListener);
        }
    }

    private String setUserStatus(int userStatus) {
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
                        if (mQuery != null && mQuery.state == 3 && subscriptionsMenuItem.isChecked()) break;
                        getTrendingData();
                        subscriptionsMenuItem.setChecked(true);
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
            mQuery = new FbQuery(-1, article.getMainTheme(), 1);
        } else if (id == R.id.card_contributor) {
            mQuery = new FbQuery(-2, article.getSource(), 1);
        }
        resetView();
    }

    private void getThemeData() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mSwipeRefreshLayout.setRefreshing(true);
        String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
        mDataSource.getThemeData(()->{
            mLlmState = null;
            mQuery = new FbQuery(3, hitsRef, "trendingIndex");
            resetView();
        });
    }

    private void getTrendingData() {
        mRecyclerView.setVisibility(View.INVISIBLE);
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
            resetView();
        });
    }

    private void getDealsData() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mSwipeRefreshLayout.setRefreshing(true);
        String hitsRef = SEARCH_REF + "/Deals/hits";
        mDataSource.getDealsData(()->{
            mLlmState = null;
            mQuery = new FbQuery(4, hitsRef, "trendingIndex");
            resetView();
        });
    }

    public static void buildThemeKeyAndFilter(ArrayList<String> themePrefs) {
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
        mThemeSearchKey = searchKeyBuilder.toString();
        mThemeSearchFilter = filterStringBuilder.toString();
        mSharedPreferences.edit().putString("themeSearchKey", mThemeSearchKey)
                .putString("themeSearchFilter", mThemeSearchFilter).apply();
    }

    private void setUpInitialViewModelObserver() {
        // Set up view model
        ArticleViewModelFactory factory = InjectorUtils.provideArticleViewModelFactory(this.getApplicationContext());
        mArticleViewModel = ViewModelProviders.of(this, factory).get(ArticleViewModel.class);

        ArticleListLiveData articleListLD = mArticleViewModel.getArticles(mQuery);
        Observer<List<Article>> articleListObserver = articles -> {
            if (articles != null) {
                /*
                1 - While child listener adds articles incrementally to live data list,
                we add to adapter list if it had not already been added.
                2 - On any changes to live data list after adapter list is set,
                update changes in-situ.
                This way, adapter list expands up to size of all observed live data lists
                (includes all loadMoreArticles lists), with no repeat articles on changes.
                */
                List<Article> currentList = mAdapter.getList();
                for (int i = 0; i < articles.size(); i++) {
                    if (currentList.size() < i+1) {
                        //1
                        currentList.add(i, articles.get(i));
                    } else {
                        //2
                        currentList.set(i, articles.get(i));
                    }
                }
                mAdapter.setList(currentList, () -> {
                    if (mLlmState != null) {
                        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
                    }
                });
            }
        };
        articleListLD.observeForever(articleListObserver);
        mObservedList.put(articleListLD, articleListObserver);
//        if (mLlmState != null) {
//            new Handler().postDelayed(() -> {
//                mLinearLayoutManager.onRestoreInstanceState(mLlmState);
//            },100);
//        }

    }
}