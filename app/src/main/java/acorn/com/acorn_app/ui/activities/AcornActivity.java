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
import static acorn.com.acorn_app.data.NetworkDataSource.SEARCH_REF;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class AcornActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        ArticleAdapter.OnLongClickListener {

    private static final String TAG = "AcornActivity";

    public static final long ID_OFFSET = 5000000000000000L;
    public static final int TRENDING_INDEX_OFFSET = 500000;
    public static final int TARGET_POINTS_MULTIPLIER = 3;

    private static final int RC_SIGN_IN = 1001;
    private static final int RC_PREF = 1002;
    public static final int RC_THEME_PREF = 1003;
    public static final int RC_SHARE = 1004;
    private static final String ARTICLE_CARD_TYPE = "card";

    // User status
    private DatabaseReference mUserStatusRef;
    private ValueEventListener mUserStatusListener;
    public static final String LEVEL_0 = "Budding Seed";
    public static final String LEVEL_1 = "Emerging Sprout";
    public static final String LEVEL_2 = "Thriving Sapling";
    public static final String LEVEL_3 = "Wise Oak";

    //Theme
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
    private static final FbQuery INIT_QUERY =
            new FbQuery(0, null, -1);

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
    private Map<LiveData<List<Article>>, Observer<List<Article>>> mObservedList = new HashMap<>();
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

    //Notifications
    private NotificationBadge notificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        Log.d(TAG, "onCreate: started");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String hashKey = new String(Base64.encode(md.digest(), 0));
                Log.d(TAG, "printHashKey() Hash Key: " + hashKey);
            }
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "printHashKey()", e);
        } catch (Exception e) {
            Log.e(TAG, "printHashKey()", e);
        }

        mFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        //Log.d(TAG, "uid: " + mFirebaseUser.getUid());

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

        // Set up view model
        ArticleViewModelFactory factory = InjectorUtils.provideArticleViewModelFactory(this.getApplicationContext());
        mArticleViewModel = ViewModelProviders.of(this, factory).get(ArticleViewModel.class);
        Log.d(TAG, "setQuery: onCreate");
        if (savedInstanceState != null) {
            mQuery = savedInstanceState.getParcelable("Query");
        } else {
            mQuery = INIT_QUERY;
        }
        setUpInitialViewModelObserver();
        Log.d(TAG, "AdapterCount: " + mAdapter.getItemCount());

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
        Menu nav_menu = (Menu) navigationView.getMenu();
        MenuItem signInMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_login);
        MenuItem signOutMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_logout);

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

//        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
//        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
//
//        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

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
//            case R.id.action_clear_search_history:
//                SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
//                        SearchProvider.AUTHORITY, SearchProvider.MODE);
//                suggestions.clearHistory();
//                return true;
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
                mQuery = new FbQuery(2, 1, 0);
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

                NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                Menu nav_menu = (Menu) navigationView.getMenu();
                MenuItem signInMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_login);
                MenuItem signOutMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_logout);

                if (mFirebaseUser != null) {
                    signInMenuItem.setVisible(false);
                    signOutMenuItem.setVisible(true);
                }

                Log.d(TAG, "setQuery: onSignInResult");
                mArticleViewModel.newQuery.postValue(mQuery);
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
                Log.e(TAG, "Sign-in error: ", response.getError());
            }
        } else if (requestCode == RC_PREF) {
            if (resultCode == RESULT_OK) {
                int newDayNightValue = data.getIntExtra("dayNightValue", 1);
                Log.d(TAG, "newDayNightValue: " + newDayNightValue + ", dayNightValue: " + dayNightValue);
                if (newDayNightValue != dayNightValue) recreate();

                commentNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_comment), false);
                Log.d(TAG, "comment notifications pref: " + commentNotifValue);
                mCommentNotifRef = mDatabaseReference.child(PREFERENCES_REF).child(COMMENTS_NOTIFICATION);
                if (!commentNotifValue) {
                    mCommentNotifRef.child(mUid).setValue(commentNotifValue);
                } else {
                    mCommentNotifRef.child(mUid).removeValue();
                }

                articleNotifValue = mSharedPreferences.getBoolean(getString(R.string.pref_key_notif_article), false);
                Log.d(TAG, "article notifications pref: " + articleNotifValue);
                if (articleNotifValue) {
                    if (!mSharedPreferences.getBoolean("isRecArticlesScheduled", false)) {
                        mDataSource.scheduleRecArticlesPush();
                        mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", true).apply();
                    }
                } else {
                    mDataSource.cancelRecArticlesPush();
                    mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", false).apply();
                }
            }
        } else if (requestCode == RC_THEME_PREF) {
            if (resultCode == RESULT_OK) {
                mUserThemePrefs = new ArrayList<>();
                mUserThemePrefs.addAll(data.getStringArrayListExtra("themePrefs"));
                buildThemeKeyAndFilter(mUserThemePrefs);
            }
        } else if (requestCode == RC_SHARE) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "share ok");
            } else {
                Log.d(TAG, "share cancelled");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: started");
    }


    @Override
    public void onResume() {
        super.onResume();
//        mNotificationsPref.edit().clear().commit();
        Log.d(TAG, "onResume: started");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mUserStatusRef != null) mUserStatusRef.removeEventListener(mUserStatusListener);
        Log.d(TAG, "onStop: started");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mUserStatusRef != null) mUserStatusRef.addValueEventListener(mUserStatusListener);
        Log.d(TAG, "onStart: started");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mObservedList.size() > 0) {
            for (LiveData<List<Article>> liveData : mObservedList.keySet()) {
                liveData.removeObserver(mObservedList.get(liveData));
                Log.d(TAG, "observer removed from " + liveData);
            }
            mObservedList.clear();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("Query", mQuery);
        mLlmState = mLinearLayoutManager.onSaveInstanceState();
        Log.d(TAG, "onSaveInstanceState: started");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState: started");
        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
    }

    private void handleIntent(Intent intent) {
        mSwipeRefreshLayout.setRefreshing(false);
        Log.d(TAG, "setQuery: onHandleIntent");
        mArticleViewModel.newQuery.postValue(mQuery);
    }

    private void loadMoreArticles() {
        if (isLoadingMore) return;

        int currentPosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
        final int trigger = 5;
        final List<Article> initialList = mAdapter.getList();
        mLoadedList = initialList;
        final Object index;

        if (mAdapter.getItemCount() < 10) return;

        Log.d(TAG, "currentPos: " + currentPosition + ", AdapterItemCount: " + mAdapter.getItemCount() + ", Trigger: " + String.valueOf(mAdapter.getItemCount()-trigger));
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
                    index = lastArticle.savers.get(mUid);
                    break;
                case -1:
                    index = lastArticle.getMainTheme();
                    break;
                case -2:
                    index = lastArticle.getSource();
                    break;
            }
            Log.d(TAG, "lastArticleTitle: " + lastArticle.getTitle() + ", lastArticleIndex: " + index);

            int indexType = mQuery.state < 0 ? 1 : 0;
            LiveData<List<Article>> addListLD = mArticleViewModel.getAdditionalArticles(index, indexType);
            Observer<List<Article>> addListObserver = articles -> {
                if (articles != null) {
                    Log.d(TAG, "addList changed");
                    List<Article> combinedList = new ArrayList<>(initialList);
                    combinedList.remove(combinedList.size()-1);
                    combinedList.addAll(articles);
                    mAdapter.setList(combinedList);
                }
            };
            addListLD.observeForever(addListObserver);
            mObservedList.put(addListLD, addListObserver);
            Log.d(TAG, "observer added to " + addListLD);
            new Handler().postDelayed(()->isLoadingMore = false,100);
        }
    }

    private void resetView(FbQuery mQuery) {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mSwipeRefreshLayout.setRefreshing(true);
        mRecyclerView.scrollToPosition(0);

        mAdapter.clear();
        mLoadedList.clear();
        if (mObservedList.size() > 0) {
            for (LiveData<List<Article>> liveData : mObservedList.keySet()) {
                liveData.removeObserver(mObservedList.get(liveData));
                Log.d(TAG, "observer removed from " + liveData);
            }
            mObservedList.clear();
        }

        if (mQuery.state == 2) {
            mAdapter = new ArticleAdapter(this, "list", this);
            mRecyclerView.setAdapter(mAdapter);
        } else {
            mAdapter = new ArticleAdapter(this, ARTICLE_CARD_TYPE, this);
            mRecyclerView.setAdapter(mAdapter);
        }
        Log.d(TAG, "setQuery: resetView");

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
                    String userToken = FirebaseInstanceId.getInstance().getToken();
                    if (retrievedUser == null) {
                        if (!user.isEmailVerified()) {
                            user.sendEmailVerification();
                        } else {
                            isUserAuthenticated = true;
                        }
                        String uid = user.getUid();
                        String displayName = user.getDisplayName();
                        String email = user.getEmail();
                        Long creationTimeStamp = user.getMetadata().getCreationTimestamp();
                        Long lastSignInTimeStamp = user.getMetadata().getLastSignInTimestamp();

                        User newUser = new User(uid, displayName, userToken, email,
                                creationTimeStamp, lastSignInTimeStamp);
                        userRef.setValue(newUser);

                        Log.d("setupUser", "New user created: " + displayName);

                        mUid = newUser.getUid();
                        mUsername = newUser.getDisplayName();
                        mUserToken = newUser.getToken();
                        mUserThemePrefs = new ArrayList<>();
                        String[] themeArray = getResources().getStringArray(R.array.theme_array);
                        Collections.addAll(mUserThemePrefs, themeArray);
                        mUserStatus = LEVEL_0;
                        lastRecArticlesPushTime = 0L;
                        lastRecArticlesScheduleTime = 0L;
                        buildThemeKeyAndFilter(mUserThemePrefs);

                        if (mUsernameTextView != null) {
                            mUsernameTextView.setText(mUsername);
                            mUsernameTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                        }
                        if (mUserStatusTextView != null) {
                            mUserStatusTextView.setText(mUserStatus);
                            mUserStatusTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                        }
                    } else {
                        Log.d(TAG, "user email verified: " + user.isEmailVerified());
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
                        retrievedUser.setLastSignInTimeStamp(user.getMetadata().getLastSignInTimestamp());
                        retrievedUser.setToken(userToken);

                        userRef.updateChildren(retrievedUser.toMap());
                        Log.d(TAG, "User retrieved: " + retrievedUser.getDisplayName());
                        Log.d(TAG, "User retrieved: " + retrievedUser.getUid());

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

                        if (mUsernameTextView != null) {
                            mUsernameTextView.setText(mUsername);
                            mUsernameTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                        }
                        if (mUserStatusTextView != null) {
                            mUserStatusTextView.setText(mUserStatus);
                            mUserStatusTextView.setOnClickListener(v -> startActivity(new Intent(AcornActivity.this, UserActivity.class)));
                        }
                    }

                    // Schedule recommended article push service unless explicitly disabled by user
                    if (articleNotifValue) {
                        long now = (new Date()).getTime();
                        long timeElapsedSinceLastPush = now - lastRecArticlesPushTime;
                        Log.d(TAG, "sharedPref: " + !mSharedPreferences.getBoolean("isRecArticlesScheduled", false));
                        Log.d(TAG, "lastPushTime: " + (timeElapsedSinceLastPush > 24L * 60L * 60L * 1000L));
                        Log.d(TAG, "lastScheduleTime: " + (lastRecArticlesScheduleTime < lastRecArticlesPushTime));
                        if (!mSharedPreferences.getBoolean("isRecArticlesScheduled", false) ||
                                (timeElapsedSinceLastPush > 24L * 60L * 60L * 1000L && // if last push time is longer than a day
                                lastRecArticlesScheduleTime < lastRecArticlesPushTime)) { // and last scheduled time is before last push time
                            mDataSource.scheduleRecArticlesPush();
                            mSharedPreferences.edit().putBoolean("isRecArticlesScheduled", true).apply();
                        }
                    }
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
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        Menu nav_menu = (Menu) navigationView.getMenu();
        MenuItem recentMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_recent);
        MenuItem trendingMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_trending);
        MenuItem subscriptionsMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_subscriptions);
        MenuItem savedMenuItem = (MenuItem) nav_menu.findItem(R.id.nav_saved);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //builder.setIcon(R.drawable.ic_launcher);
        builder.setTitle("Which screen would you like to return to?");

        final ArrayAdapter<String> arrayAdapter =
                new ArrayAdapter<>(this, R.layout.item_simple_list);
        arrayAdapter.add("Recent");
        arrayAdapter.add("Trending");
        arrayAdapter.add("Subscriptions");
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
                        mQuery = new FbQuery(2, 1, 0);
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
            Log.d(TAG, "theme: " + article.getMainTheme());
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
                filterStringBuilder.append("theme: \"").append(themePrefs.get(i)).append("\"");
            } else {
                searchKeyBuilder.append("_").append(themePrefs.get(i));
                filterStringBuilder.append(" OR theme: \"").append(themePrefs.get(i)).append("\"");
            }
        }
        mThemeSearchKey = searchKeyBuilder.toString();
        mSharedPreferences.edit().putString("themeSearchKey", mThemeSearchKey).apply();
        mThemeSearchFilter = filterStringBuilder.toString();
    }

    private void setUpInitialViewModelObserver() {
        mArticleViewModel.newQuery.setValue(mQuery);
        LiveData<List<Article>> articleListLD = mArticleViewModel.getArticles();
        Observer<List<Article>> articleListObserver = articles -> {
            if (articles != null) {
                Log.d(TAG, "articleList changed");
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
        Log.d(TAG, "observer added to " + articleListLD);
    }
}