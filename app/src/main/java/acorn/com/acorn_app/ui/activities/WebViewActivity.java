package acorn.com.acorn_app.ui.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.ui.adapters.ArticleOnClickListener;
import acorn.com.acorn_app.ui.views.ObservableWebView;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;
import acorn.com.acorn_app.utils.HtmlUtils;

import static acorn.com.acorn_app.data.NetworkDataSource.ARTICLE_REF;
import static acorn.com.acorn_app.data.NetworkDataSource.NOTIFICATION_TOKENS;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserToken;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static acorn.com.acorn_app.utils.UiUtils.increaseTouchArea;

public class WebViewActivity extends AppCompatActivity {
    private static final String TAG = "WebViewActivity";

    private ObservableWebView webView;
    private ProgressBar progressBar;
    private Intent intent;

    private Article mArticle;
    private String articleId;
    private String link;
    private String title;
    private String author;
    private String source;
    private String date;
    private boolean isArticleLoaded = false;

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

    private final AppExecutors mExecutors = AppExecutors.getInstance();

    private static float lastScrollPercent = 0f;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        intent = getIntent();
        articleId = intent.getStringExtra("id");
//        Log.d(TAG, "articleId: " + articleId);

        // Set up Firebase Database
        if (mDatabase == null) mDatabase = FirebaseDatabase.getInstance();
        DatabaseReference databaseReference = mDatabase.getReference();
        mArticleRef = databaseReference.child(ARTICLE_REF).child(articleId);

        progressBar = (ProgressBar) findViewById(R.id.webview_progress_bar);

        // Set up web view
        webView = (ObservableWebView) findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.setWebViewClient(new MyWebViewClient());
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

        // Get article
        mArticleListener = mArticleRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mArticle = dataSnapshot.getValue(Article.class);
//                Log.d(TAG, mArticle.getTitle());

                // get data to set up card
                link = mArticle.getLink();
                title = mArticle.getTitle();
                author = mArticle.getAuthor();
                source = mArticle.getSource();
                date = DateUtils.parseDate(mArticle.getPubDate());
                if (!isArticleLoaded) {
                    if (mArticle.getType() != null && mArticle.getType().equals("article")) {
                        genHtml();
                    } else {
                        webView.loadUrl(link);
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                createToast(WebViewActivity.this, databaseError.toString(), Toast.LENGTH_SHORT);
            }
        });
//        Log.d(TAG, "valueEventListener added to " + mArticleRef);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
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

                searchFab.setVisibility(View.VISIBLE);
                searchFab.setOnClickListener(v -> webView.findNext(true));
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                webView.findAllAsync(newText);
                searchFab.setVisibility(View.INVISIBLE);
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
//                            Log.d(TAG, dataSnapshot.getKey() + ": " + dataSnapshot.getValue(String.class));
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
//                            Log.d(TAG, dataSnapshot.getKey() + ": " + dataSnapshot.getValue(String.class));
                        }
                    }
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            };
            mArticleRef.child(NOTIFICATION_TOKENS).addChildEventListener(mFollowListener);
//            Log.d(TAG, "listener added to " + mArticleRef.child(NOTIFICATION_TOKENS));
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
            mArticleRef.child(NOTIFICATION_TOKENS).child(mUid).setValue(mUserToken);
        } else if (id == R.id.action_unfollow) {
            mArticleRef.child(NOTIFICATION_TOKENS).child(mUid).removeValue();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
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
        if (mArticleRef != null) {
            mArticleRef.removeEventListener(mArticleListener);
//            Log.d(TAG, "valueEventListener removed from " + mArticleRef);
            if (mFollowListener != null) {
                mArticleRef.child(NOTIFICATION_TOKENS).removeEventListener(mFollowListener);
//                Log.d(TAG, "childEventListener removed from " + mArticleRef.child(NOTIFICATION_TOKENS));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        lastScrollPercent = 0f;
    }

    public void genHtml() {
        webView.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);

//        Log.d(TAG, link + title + author + source + date);
        mExecutors.networkIO().execute(() -> {
            String generatedHtml = HtmlUtils.regenArticleHtml(webView.getContext(),
                        link, title, author, source, date);

            boolean isSuccessful = generatedHtml != null && !generatedHtml.equals("");

            if (isSuccessful) {
                mExecutors.mainThread().execute(() -> webView.loadData(generatedHtml, null, null));

                double wordCount = generatedHtml.split("\\s+").length;
                int readTime = (int) Math.ceil(wordCount / 200D);

                mArticleRef.runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                        Article article = mutableData.getValue(Article.class);
                        if (article == null) {
//                            Log.d(TAG, "postReadTime: could not find article");
                            return Transaction.success(mutableData);
                        }

                        article.setReadTime(readTime);
                        mutableData.setValue(article);
                        return Transaction.success(mutableData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {
//                        Log.d(TAG, "postReadTime: " + databaseError);
                    }
                });
            } else {
                finish();
                mExecutors.mainThread().execute(() -> createToast(WebViewActivity.this, "Failed to load article", Toast.LENGTH_SHORT));
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
//        outState.putFloat("scrollPercent", lastScrollPercent);
    }

//    @Override
//    protected void onRestoreInstanceState(Bundle savedInstanceState) {
//        Log.d(TAG, "onRestoreInstanceState");
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
        Log.d(TAG, "height: " + contentHeight + ", position: " + currentScrollPosition + ", percent: " + scrollPercent);
        return scrollPercent;
    }

    private void restoreScrollPosition(ObservableWebView content, float scrollPercent) {
        float contentHeight = content.computeVerticalScrollRange() - content.getHeight();
        float scrollPosition = contentHeight * scrollPercent;
        int positionY = (int) Math.round(scrollPosition);
        Log.d(TAG, "height: " + contentHeight + ", position: " + scrollPosition);
        content.scrollTo(0, positionY);
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                restoreScrollPosition(webView, lastScrollPercent);
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                super.onPageFinished(view, url);
            }, 300);

        }
    }
}