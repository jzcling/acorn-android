package acorn.com.acorn_app.ui.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.ArticleRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.dbArticle;
import acorn.com.acorn_app.ui.adapters.ArticleOnClickListener;
import acorn.com.acorn_app.ui.views.ObservableWebView;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;
import acorn.com.acorn_app.utils.HtmlUtils;
import androidx.cardview.widget.CardView;

import static acorn.com.acorn_app.data.NetworkDataSource.ARTICLE_REF;
import static acorn.com.acorn_app.data.NetworkDataSource.NOTIFICATION_TOKENS;
import static acorn.com.acorn_app.ui.AcornApplication.mFirebaseAnalytics;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserToken;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static acorn.com.acorn_app.utils.UiUtils.increaseTouchArea;

public class WebViewActivity extends AppCompatActivity {
    private static final String TAG = "WebViewActivity";

    private ObservableWebView webView;
    private ProgressBar progressBar;
    private CardView messageOverlayCard;
    private boolean isNewIntent = false;

    private String articleId;

    private dbArticle mDbArticle;
    private Article mArticle;
    private String link;
    private String title;
    private String author;
    private String source;
    private String date;
    private String htmlContent;
    private String selector;
    private boolean isArticleLoaded = false;

    // Room Database;
    private ArticleRoomDatabase mRoomDb;

    // Firebase
    private FirebaseDatabase mDatabase;
    private DatabaseReference mArticleRef;
    private ValueEventListener mArticleListener;
    private ChildEventListener mFollowListener;

    // Action Buttons
    private CheckBox upVoteView;
    private CheckBox downVoteView;
    private CheckBox commentView;
    private CheckBox favView;
    private CheckBox shareView;

    // Data Source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    private static float lastScrollPercent = 0f;
    private static boolean hasDisplayedMessageOverlay = false;
    private boolean hasLoggedSelectContent = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up Room Database
        mRoomDb = ArticleRoomDatabase.getInstance(this);

        // Set up Firebase Database
        if (mDatabase == null) mDatabase = FirebaseDatabase.getInstance();

        // Set up data source
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);

        progressBar = (ProgressBar) findViewById(R.id.webview_progress_bar);
        messageOverlayCard = (CardView) findViewById(R.id.message_overlay_card);

        // Remove message overlay card if shown before
        if (hasDisplayedMessageOverlay) {
            messageOverlayCard.setVisibility(View.INVISIBLE);
        } else {
            messageOverlayCard.setVisibility(View.VISIBLE);
        }

        // Set up web view
        webView = (ObservableWebView) findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setSupportZoom(true);
        webView.setWebViewClient(new MyWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.INVISIBLE);

                    if (!hasDisplayedMessageOverlay) {
                        Animation fadeOut = new AlphaAnimation(1, 0);
                        fadeOut.setInterpolator(new AccelerateInterpolator());
                        fadeOut.setStartOffset(0);
                        fadeOut.setDuration(500);

                        messageOverlayCard.setAnimation(fadeOut);
                        messageOverlayCard.setVisibility(View.INVISIBLE);
                        hasDisplayedMessageOverlay = true;
                    }
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
        });
        webView.setOnScrollChangeListener(
                (ObservableWebView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    lastScrollPercent = getScrollPercent(webView);
                });

        // Set up action buttons
        upVoteView = (CheckBox) findViewById(R.id.button_upvote);
        downVoteView = (CheckBox) findViewById(R.id.button_downvote);
        commentView = (CheckBox) findViewById(R.id.button_comment);
        favView = (CheckBox) findViewById(R.id.button_favourite);
        shareView = (CheckBox) findViewById(R.id.button_share);

        increaseTouchArea(upVoteView);
        increaseTouchArea(downVoteView);
        increaseTouchArea(commentView);
        increaseTouchArea(favView);
        increaseTouchArea(shareView);

        Intent intent = getIntent();
        handleIntent(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        isNewIntent = true;
        isArticleLoaded = false;
        hasDisplayedMessageOverlay = false;
        messageOverlayCard.setVisibility(View.VISIBLE);

        webView.loadUrl("about:blank");

        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String appLinkAction = intent.getAction();
        Uri appLinkData = intent.getData();
        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData != null) {
            FirebaseDynamicLinks.getInstance().getDynamicLink(intent)
                    .addOnSuccessListener(this, pendingDynamicLinkData -> {
                        Uri deepLink;
                        if (pendingDynamicLinkData != null) {
                            deepLink = pendingDynamicLinkData.getLink();
                            Log.d(TAG, deepLink.toString());
                            articleId = deepLink.getQueryParameter("id");
                            Log.d(TAG, "articleId: " + articleId);
                            String sharerId = deepLink.getQueryParameter("sharerId");
                            loadArticle();
                        }
                    })
                    .addOnFailureListener(this, e -> {
                        Log.d(TAG, "Failed to get deep link: " + e);
                    });
        } else {
            articleId = intent.getStringExtra("id");
            loadArticle();
        }
    }

    private void loadArticle() {
        Log.d(TAG, "loadArticle");
        DatabaseReference databaseReference = mDatabase.getReference();
        mArticleRef = databaseReference.child(ARTICLE_REF).child(articleId);

        // Get article
        // Check room database if article is present, otherwise fetch from network
        mExecutors.diskRead().execute(() -> {
            mDbArticle = mRoomDb.articleDAO().getDbArticle(articleId);
            mExecutors.mainThread().execute(() -> {
                if (mDbArticle != null) {
                    Log.d(TAG, "loaded from roomDb");
                    link = mDbArticle.link;
                    title = mDbArticle.title;
                    author = mDbArticle.author;
                    source = mDbArticle.source;
                    date = DateUtils.parseDate(mDbArticle.pubDate);
                    htmlContent = mDbArticle.htmlContent;
                    String generatedHtml = HtmlUtils.generateHtmlContent(this, title, link,
                            htmlContent, author, source, date);

                    webView.loadDataWithBaseURL(null, generatedHtml, "text/html", "utf-8", null);

                    double wordCount = generatedHtml.split("\\s+").length;
                    int readTime = (int) Math.ceil(wordCount / 200D);

                    mArticleRef.runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                            Article article = mutableData.getValue(Article.class);
                            if (article == null) {
                                return Transaction.success(mutableData);
                            }

                            article.setReadTime(readTime);
                            mutableData.setValue(article);
                            return Transaction.success(mutableData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
                        }
                    });

                    mExecutors.networkIO().execute(() -> {
                        mArticleListener = mArticleRef.addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                // set up bottom toolbar
                                if (dataSnapshot.exists()) {
                                    mArticle = dataSnapshot.getValue(Article.class);

                                    if (mArticle != null) {
                                        if (mArticle.upvoters.containsKey(mUid)) {
                                            upVoteView.setChecked(true);
                                        } else {
                                            upVoteView.setChecked(false);
                                        }
                                        if (mArticle.downvoters.containsKey(mUid)) {
                                            downVoteView.setChecked(true);
                                        } else {
                                            downVoteView.setChecked(false);
                                        }
                                        if (mArticle.commenters.containsKey(mUid)) {
                                            commentView.setChecked(true);
                                        } else {
                                            commentView.setChecked(false);
                                        }
                                        if (mArticle.savers.containsKey(mUid)) {
                                            favView.setChecked(true);
                                        } else {
                                            favView.setChecked(false);
                                        }

                                        upVoteView.setOnClickListener(onClickListener(mArticle, "upvote"));
                                        downVoteView.setOnClickListener(onClickListener(mArticle, "downvote"));
                                        commentView.setOnClickListener(onClickListener(mArticle, "comment"));
                                        favView.setOnClickListener(onClickListener(mArticle, "favourite"));
                                        shareView.setOnClickListener(onClickListener(mArticle, "share"));

                                        if (!hasLoggedSelectContent) {
                                            Bundle bundle = new Bundle();
                                            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, mArticle.getObjectID());
                                            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mArticle.getTitle());
                                            bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, mArticle.getMainTheme());
                                            bundle.putString("item_source", mArticle.getSource());
                                            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mArticle.getType());
                                            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
                                            hasLoggedSelectContent = true;
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                createToast(WebViewActivity.this, databaseError.toString(), Toast.LENGTH_SHORT);
                            }
                        });
                    });
                } else {
                    mExecutors.networkIO().execute(() -> {
                        mArticleListener = mArticleRef.addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    mArticle = dataSnapshot.getValue(Article.class);

                                    // get data to set up card
                                    if (mArticle != null) {
                                        Log.d(TAG, "loaded from firebaseDb");
                                        link = mArticle.getLink();
                                        title = mArticle.getTitle();
                                        author = mArticle.getAuthor();
                                        source = mArticle.getSource();
                                        date = DateUtils.parseDate(mArticle.getPubDate());
                                        selector = mArticle.selector;
//                                        htmlContent = mArticle.htmlContent;
                                        if (!isArticleLoaded) {
                                            if (mArticle.getType() != null && mArticle.getType().equals("article")) {
                                                mExecutors.mainThread().execute(() -> genHtml());
                                            } else {
                                                mExecutors.mainThread().execute(() -> webView.loadUrl(link));
                                            }
                                            isArticleLoaded = true;
                                        }

                                        // set up bottom toolbar
                                        if (mArticle.upvoters.containsKey(mUid)) {
                                            upVoteView.setChecked(true);
                                        } else {
                                            upVoteView.setChecked(false);
                                        }
                                        if (mArticle.downvoters.containsKey(mUid)) {
                                            downVoteView.setChecked(true);
                                        } else {
                                            downVoteView.setChecked(false);
                                        }
                                        if (mArticle.commenters.containsKey(mUid)) {
                                            commentView.setChecked(true);
                                        } else {
                                            commentView.setChecked(false);
                                        }
                                        if (mArticle.savers.containsKey(mUid)) {
                                            favView.setChecked(true);
                                        } else {
                                            favView.setChecked(false);
                                        }

                                        upVoteView.setOnClickListener(onClickListener(mArticle, "upvote"));
                                        downVoteView.setOnClickListener(onClickListener(mArticle, "downvote"));
                                        commentView.setOnClickListener(onClickListener(mArticle, "comment"));
                                        favView.setOnClickListener(onClickListener(mArticle, "favourite"));
                                        shareView.setOnClickListener(onClickListener(mArticle, "share"));

                                        if (!hasLoggedSelectContent) {
                                            Bundle bundle = new Bundle();
                                            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, mArticle.getObjectID());
                                            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mArticle.getTitle());
                                            bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, mArticle.getMainTheme());
                                            bundle.putString("item_source", mArticle.getSource());
                                            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mArticle.getType());
                                            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
                                            hasLoggedSelectContent = true;
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                createToast(WebViewActivity.this, databaseError.toString(), Toast.LENGTH_SHORT);
                            }
                        });
                    });
                }
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.app_bar_webview, menu);

        // Set up search
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        EditText searchText = (EditText) searchView.findViewById(R.id.search_src_text);
        FloatingActionButton searchFab = (FloatingActionButton) findViewById(R.id.search_fab);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                webView.findNext(true);
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                searchFab.show();
                searchFab.setOnClickListener(v -> webView.findNext(true));
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                webView.findAllAsync(newText);
                searchFab.hide();
                searchFab.setOnClickListener(null);
                return true;
            }
        });

        // Set up follow/unfollow options
        MenuItem followOption = (MenuItem) menu.findItem(R.id.action_follow);
        MenuItem unfollowOption = (MenuItem) menu.findItem(R.id.action_unfollow);
        if (mArticleRef != null) {
            mFollowListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    if (dataSnapshot.getValue() != null) {
                        if (dataSnapshot.getKey().equals(mUid)) {
                            followOption.setVisible(false);
                            unfollowOption.setVisible(true);
                        }
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        if (dataSnapshot.getKey().equals(mUid)) {
                            followOption.setVisible(true);
                            unfollowOption.setVisible(false);
//
                        }
                    }
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            };
            mArticleRef.child(NOTIFICATION_TOKENS).addChildEventListener(mFollowListener);
//
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_search) {
            return true;
        } else if (id == R.id.action_follow) {
            if (mUid == null || mUserToken == null) {
                createToast(this, "An error occurred", Toast.LENGTH_SHORT);
                return false;
            }
            mArticleRef.child(NOTIFICATION_TOKENS).child(mUid).setValue(mUserToken);
        } else if (id == R.id.action_unfollow) {
            mArticleRef.child(NOTIFICATION_TOKENS).child(mUid).removeValue();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (mArticleRef != null && mArticleListener != null) {
            mArticleRef.removeEventListener(mArticleListener);
            if (mFollowListener != null) {
                mArticleRef.child(NOTIFICATION_TOKENS).removeEventListener(mFollowListener);
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

        if (!isChangingConfigurations()) {
            lastScrollPercent = 0f;
            if (mArticle != null) mExecutors.networkIO().execute(() -> {
                mDataSource.recordArticleOpenDetails(mArticle);
            });
        }
    }

    public void genHtml() {
//        Log.d(TAG, "htmlContent: " + htmlContent.substring(0, 20));
        mExecutors.networkIO().execute(() -> {
            String generatedHtml = HtmlUtils.regenArticleHtml(this, link, title, author, source, date, //htmlContent,
                    selector);

            boolean isSuccessful = generatedHtml != null && !generatedHtml.equals("");

            if (isSuccessful) {
                mExecutors.mainThread().execute(() -> {
                    webView.loadDataWithBaseURL(null, generatedHtml, "text/html", "utf-8", null);
                });

                double wordCount = generatedHtml.split("\\s+").length;
                int readTime = (int) Math.ceil(wordCount / 200D);

                mArticleRef.runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                        Article article = mutableData.getValue(Article.class);
                        if (article == null) {
//
                            return Transaction.success(mutableData);
                        }

                        article.setReadTime(readTime);
                        mutableData.setValue(article);
                        return Transaction.success(mutableData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
//
                    }
                });
            } else {
                if (link != null && !link.equals("")) {
                    mExecutors.mainThread().execute(() -> { webView.loadUrl(link); });
                    return;
                }

                finish();
                mExecutors.mainThread().execute(() -> createToast(WebViewActivity.this, "Failed to load article", Toast.LENGTH_SHORT));
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {

        super.onSaveInstanceState(outState);
//        outState.putFloat("scrollPercent", lastScrollPercent);
    }

//    @Override
//    protected void onRestoreInstanceState(Bundle savedInstanceState) {
//
//        super.onRestoreInstanceState(savedInstanceState);
//        lastScrollPercent = savedInstanceState.getFloat("scrollPercent");
//    }

    private ArticleOnClickListener onClickListener(Article article, String cardAttribute) {
        return new ArticleOnClickListener(this, article, cardAttribute,
                upVoteView, downVoteView, commentView, favView, shareView);
    }

    private float getScrollPercent(ObservableWebView content) {
        float contentHeight = content.computeVerticalScrollRange() - content.getHeight();
        float currentScrollPosition = content.getScrollY();
        float scrollPercent = contentHeight == 0f ? 0 : currentScrollPosition / contentHeight;
        Log.d(TAG, "getScrollPercent: " + scrollPercent);
        return scrollPercent;
    }

    private void restoreScrollPosition(ObservableWebView content, float scrollPercent) {
        Log.d(TAG, "restoreScrollPosition: " + scrollPercent);
        float contentHeight = content.computeVerticalScrollRange() - content.getHeight();
        float scrollPosition = contentHeight * scrollPercent;
        int positionY = (int) Math.round(scrollPosition);

        content.scrollTo(0, positionY);
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            restoreScrollPosition(webView, lastScrollPercent);

            if (isNewIntent) {
                view.clearHistory();
            }
        }
    }
}
