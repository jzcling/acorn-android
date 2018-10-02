package acorn.com.acorn_app.ui.activities;

import android.app.AlertDialog;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.firebase.iid.FirebaseInstanceId;
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

import static acorn.com.acorn_app.data.NetworkDataSource.COMMENTS_NOTIFICATION;
import static acorn.com.acorn_app.data.NetworkDataSource.PREFERENCES_REF;
import static acorn.com.acorn_app.data.NetworkDataSource.REC_ARTICLES_NOTIFICATION;
import static acorn.com.acorn_app.data.NetworkDataSource.SEARCH_REF;
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

    //Main UI
    private DrawerLayout mDrawer;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private FloatingActionButton mScrollFab;
    private FloatingActionButton mPostFab;

    //Firebase database
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;
    public static FbQuery mQuery;

    //Firebase analytics
    private FirebaseAnalytics mFirebaseAnalytics;
    
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
    private final Map<LiveData<List<Article>>, Observer<List<Article>>> mObservedList = new HashMap<>();
    private NotificationViewModel mNotifViewModel;

    //Current loaded articles
    private static List<Article> mLoadedList = null;

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
    private TextView mUsernameTextView;
    private TextView mUserStatusTextView;

    //Shared prefs
    public static SharedPreferences mSharedPreferences;
    public static SharedPreferences mNotificationsPref;
    private static int dayNightValue;
    private static Boolean commentNotifValue;
    private static Boolean articleNotifValue;
    private DatabaseReference mCommentNotifRef;
    private ValueEventListener mCommentNotifListener;
    private DatabaseReference mRecArticlesNotifRef;
    private ValueEventListener mRecArticlesNotifListener;

    //Notifications
    private NotificationBadge notificationBadge;
    
    //Menu
    private Menu navMenu;

    private Bundle mSavedInstanceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mSavedInstanceState = savedInstanceState;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mNotificationsPref = getSharedPreferences(getString(R.string.notif_pref_id), MODE_PRIVATE);
        dayNightValue = Integer.parseInt(mSharedPreferences.getString(
                getString(R.string.pref_key_night_mode),"1"));
        commentNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_comment), true);
        articleNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_article), true);
        AppCompatDelegate.setDefaultNightMode(dayNightValue);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acorn);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String hashKey = new String(Base64.encode(md.digest(), 0));

            }
        } catch (NoSuchAlgorithmException e) {

        } catch (Exception e) {

        }

        mFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        //

        // Set up Firebase Database
        if (mDatabase == null) mDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mDatabase.getReference();
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);

        setupUser(mFirebaseUser);

        // Initiate views
        mRecyclerView = (RecyclerView) findViewById(R.id.card_recycler_view);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mScrollFab = (FloatingActionButton) findViewById(R.id.scroll_fab);
        mPostFab = (FloatingActionButton) findViewById(R.id.post_fab);

        // Set up recycler view
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        mAdapter = new ArticleAdapter(this, ARTICLE_CARD_TYPE, this);
        mRecyclerView.setAdapter(mAdapter);
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

        final MenuItem notificationItem = menu.findItem(R.id.action_notifications);
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
            case R.id.nav_recent:
                if (mQuery.state == 0) break;
                mLlmState = null;
                mQuery = new FbQuery(0, null, -1);
                resetView(mQuery);
                break;
            case R.id.nav_trending:
                if (mQuery.state == 1) break;
                mLlmState = null;
                mQuery = new FbQuery(1, null, -1);
                resetView(mQuery);
                break;
            case R.id.nav_themes:
                Intent editThemeIntent = new Intent(this, ThemeSelectionActivity.class);
                editThemeIntent.putStringArrayListExtra("themePrefs", mUserThemePrefs);
                startActivityForResult(editThemeIntent, RC_THEME_PREF);
                break;
            case R.id.nav_saved:
                if (mQuery.state == 2) break;
                mLlmState = null;
                mQuery = new FbQuery(2, 0, 0);
                resetView(mQuery);
                break;
            case R.id.nav_subscriptions:
                getThemeData();
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

                if (mArticleViewModel != null) mArticleViewModel.newQuery.postValue(mQuery);
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

                mCommentNotifRef = mDatabaseReference.child(PREFERENCES_REF).child(COMMENTS_NOTIFICATION);
                if (!commentNotifValue) {
                    mCommentNotifRef.child(mUid).setValue(commentNotifValue);
                } else {
                    mCommentNotifRef.child(mUid).removeValue();
                }

                articleNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_article), false);

                mRecArticlesNotifRef = mDatabaseReference.child(PREFERENCES_REF).child(REC_ARTICLES_NOTIFICATION);
                if (articleNotifValue) {
                    if (!mSharedPreferences.getBoolean("isRecArticlesScheduled", false)) {
                        mDataSource.scheduleRecArticlesPush();
                        mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", true).apply();
                    }
                    mRecArticlesNotifRef.child(mUid).removeValue();
                } else {
                    mDataSource.cancelRecArticlesPush();
                    mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", false).apply();
                    mRecArticlesNotifRef.child(mUid).setValue(articleNotifValue);
                }
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
//        } else if (requestCode == RC_SHARE) {
//            if (resultCode == RESULT_OK) {
//
//            } else {
//
//            }
//        }
    }

    @Override
    public void onPause() {
        super.onPause();

    }


    @Override
    public void onResume() {
        super.onResume();
//        mNotificationsPref.edit().clear().commit();

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mUserStatusRef != null) mUserStatusRef.removeEventListener(mUserStatusListener);

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mUserStatusRef != null) mUserStatusRef.addValueEventListener(mUserStatusListener);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mObservedList.size() > 0) {
            for (LiveData<List<Article>> liveData : mObservedList.keySet()) {
                liveData.removeObserver(mObservedList.get(liveData));

            }
            mObservedList.clear();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("Query", mQuery);
        mLlmState = mLinearLayoutManager.onSaveInstanceState();

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
    }

    private void handleIntent(Intent intent) {
        mSwipeRefreshLayout.setRefreshing(false);
        if (mArticleViewModel != null) {

            mArticleViewModel.newQuery.postValue(mQuery);
        }
    }

    private void loadMoreArticles() {
        if (isLoadingMore) return;

        int currentPosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
        final int trigger = 5;
        final List<Article> initialList = mAdapter.getList();
        mLoadedList = initialList;
        final Object index;

        if (mAdapter.getItemCount() < 10) return;


        if (currentPosition > mAdapter.getItemCount() - trigger) {
            isLoadingMore = true;

            Article lastArticle = mAdapter.getLastItem();

            switch (mQuery.state) {
                default:
                case 0:
                case 3:
                    index = lastArticle.getPubDate();
                    break;
                case 1:
                    index = lastArticle.getTrendingIndex();
                    break;
                case 2:
                    index = mAdapter.getItemCount();
//                    index = lastArticle.savers.get(mUid);
                    break;
                case -1:
//                    index = lastArticle.getMainTheme();
                    return;
                case -2:
//                    index = lastArticle.getSource();
                    return;
            }


            int indexType = mQuery.state < 0 ? 1 : 0;
            LiveData<List<Article>> addListLD = mArticleViewModel.getAdditionalArticles(index, indexType);
            Observer<List<Article>> addListObserver = articles -> {
                if (articles != null) {

                    List<Article> combinedList = new ArrayList<>(initialList);
                    combinedList.remove(combinedList.size()-1);
                    combinedList.addAll(articles);
                    mAdapter.setList(combinedList);
                }
            };
            addListLD.observeForever(addListObserver);
            mObservedList.put(addListLD, addListObserver);

            new Handler().postDelayed(()->isLoadingMore = false,100);
        }
    }

    private void resetView(FbQuery mQuery) {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mSwipeRefreshLayout.setRefreshing(true);
        mRecyclerView.scrollToPosition(0);

        mAdapter.clear();
        if (mLoadedList != null) mLoadedList.clear();
        if (mObservedList.size() > 0) {
            for (LiveData<List<Article>> liveData : mObservedList.keySet()) {
                liveData.removeObserver(mObservedList.get(liveData));

            }
            mObservedList.clear();
        }

        if (mQuery.state == 2) {
            mAdapter = new ArticleAdapter(this, ARTICLE_LIST_TYPE, this);
            mRecyclerView.setAdapter(mAdapter);
        } else {
            mAdapter = new ArticleAdapter(this, ARTICLE_CARD_TYPE, this);
            mRecyclerView.setAdapter(mAdapter);
        }


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
                                    userRef.setValue(newUser);



                                    mUid = newUser.getUid();
                                    mUsername = newUser.getDisplayName();
                                    mUserToken = newUser.getToken();
                                    mUserStatus = LEVEL_0;
                                    lastRecArticlesPushTime = 0L;
                                    lastRecArticlesScheduleTime = 0L;

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
                                            mQuery = new FbQuery(3, hitsRef, "pubDate");
                                        }
                                    } else {
                                        String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
                                        mQuery = new FbQuery(3, hitsRef, "pubDate");
                                    }
                                    setUpInitialViewModelObserver();
                                    mDataSource.setupAlgoliaClient();
                                } else {

                                    if (!user.isEmailVerified()) {
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
                                    mUserStatus = setUserStatus(retrievedUser.getStatus());
                                    lastRecArticlesPushTime = retrievedUser.getLastRecArticlesPushTime();
                                    lastRecArticlesScheduleTime = retrievedUser.getLastRecArticlesScheduleTime();
                                    buildThemeKeyAndFilter(mUserThemePrefs);


                                    if (mSavedInstanceState != null) {
                                        mQuery = mSavedInstanceState.getParcelable("Query");
                                        if (mQuery == null) {
                                            String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
                                            mQuery = new FbQuery(3, hitsRef, "pubDate");
                                        }
                                    } else {
                                        String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
                                        mQuery = new FbQuery(3, hitsRef, "pubDate");
                                    }
                                    setUpInitialViewModelObserver();
                                    mDataSource.setupAlgoliaClient();

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

                                // Schedule recommended article push service unless explicitly disabled by user
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
        switch (userStatus) {
            default:
            case 0:
                return LEVEL_0;
            case 1:
                return LEVEL_1;
            case 2:
                return LEVEL_2;
            case 3:
                return LEVEL_3;
        }
    }

    private void createBackPressedDialog() {
        MenuItem recentMenuItem = (MenuItem) navMenu.findItem(R.id.nav_recent);
        MenuItem trendingMenuItem = (MenuItem) navMenu.findItem(R.id.nav_trending);
        MenuItem subscriptionsMenuItem = (MenuItem) navMenu.findItem(R.id.nav_subscriptions);
        MenuItem savedMenuItem = (MenuItem) navMenu.findItem(R.id.nav_saved);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //builder.setIcon(R.drawable.ic_launcher);
        builder.setTitle("Which screen would you like to return to?");

        final ArrayAdapter<String> arrayAdapter =
                new ArrayAdapter<>(this, R.layout.item_simple_list);
        arrayAdapter.add("Subscriptions");
        arrayAdapter.add("Recent");
        arrayAdapter.add("Trending");
        arrayAdapter.add("Saved Articles");
        arrayAdapter.add("Exit App");

        builder.setNegativeButton("cancel", (dialog, which) -> dialog.dismiss());

        builder.setAdapter(arrayAdapter, (dialog, which) -> {
            String screen = arrayAdapter.getItem(which);
            if (screen != null) {
                switch  (screen) {
                    default:
                    case "Recent":
                        if (mQuery.state == 0) break;
                        mQuery = new FbQuery(0, null, -1);
                        resetView(mQuery);
                        recentMenuItem.setChecked(true);
                        break;
                    case "Trending":
                        if (mQuery.state == 1) break;
                        mQuery = new FbQuery(1, null, -1);
                        resetView(mQuery);
                        trendingMenuItem.setChecked(true);
                        break;
                    case "Saved Articles":
                        if (mQuery.state == 2) break;
                        mQuery = new FbQuery(2, 0, 0);
                        resetView(mQuery);
                        savedMenuItem.setChecked(true);
                        break;
                    case "Subscriptions":
                        if (mQuery.state == 3) break;
                        getThemeData();
                        subscriptionsMenuItem.setChecked(true);
                        break;
                    case "Exit App":
                        finish();
                        break;
                }
            }
        });
        builder.show();
    }

    @Override
    public void onLongClick(Article article, int id, String text) {
        if (id == R.id.card_theme) {

            mQuery = new FbQuery(-1, article.getMainTheme(), 1);
        } else if (id == R.id.card_contributor) {
            mQuery = new FbQuery(-2, article.getSource(), 1);
        }
        resetView(mQuery);
    }

    private void getThemeData() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mSwipeRefreshLayout.setRefreshing(true);
        String hitsRef = SEARCH_REF + "/" + mThemeSearchKey + "/hits";
        mDataSource.getThemeData(()->{
            mLlmState = null;
            mQuery = new FbQuery(3, hitsRef, "pubDate");
            resetView(mQuery);
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


        mArticleViewModel.newQuery.setValue(mQuery);
        LiveData<List<Article>> articleListLD = mArticleViewModel.getArticles();
        Observer<List<Article>> articleListObserver = articles -> {
            if (articles != null) {

                List<Article> combinedList = mLoadedList == null ?
                        new ArrayList<>() : new ArrayList<>(mLoadedList);
                for (int i = 0; i < articles.size(); i++) {
                    if (combinedList.size() < i+1) {
                        combinedList.add(i, articles.get(i));
                    } else {
                        combinedList.set(i, articles.get(i));
                    }
                }
                mAdapter.setList(combinedList);
            }
        };
        articleListLD.observeForever(articleListObserver);
        mObservedList.put(articleListLD, articleListObserver);
        if (mLlmState != null) {
            mLinearLayoutManager.onRestoreInstanceState(mLlmState);
        }

    }
}