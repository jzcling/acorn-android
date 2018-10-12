package acorn.com.acorn_app.ui.activities;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.algolia.instantsearch.events.ErrorEvent;
import com.algolia.instantsearch.helpers.InstantSearch;
import com.algolia.instantsearch.helpers.Searcher;
import com.algolia.instantsearch.ui.views.Hits;
import com.algolia.instantsearch.ui.views.SearchBox;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.models.Notif;
import acorn.com.acorn_app.ui.adapters.ArticleAdapter;
import acorn.com.acorn_app.ui.adapters.CommentAdapter;
import acorn.com.acorn_app.ui.adapters.CommentViewHolder;
import acorn.com.acorn_app.ui.adapters.NotificationAdapter;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModel;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModelFactory;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.InjectorUtils;
import acorn.com.acorn_app.utils.RecyclerItemTouchHelper;

import static acorn.com.acorn_app.data.NetworkDataSource.NOTIFICATION_TOKENS;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE;


public class SavedArticlesActivity extends AppCompatActivity {
    private static final String TAG = "SavedArticlesActivity";

    //Firebase database
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;
    public static FbQuery mQuery;

    //Data source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    //View Models
    private ArticleViewModel mArticleViewModel;
    private final Map<LiveData<List<Article>>, Observer<List<Article>>> mObservedList = new HashMap<>();

    //RecyclerView
    private RecyclerView mRecyclerView;
    private ArticleAdapter mAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private boolean isLoadingMore = false;

    //For restoring state
    private static Parcelable mLlmState;
    private static List<Article> mLoadedList = null;

    //For filtering
    private String mSearchText;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up recycler view
        RecyclerView mRecyclerView = (RecyclerView) findViewById(R.id.saved_articles_rv);
        LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new ArticleAdapter(this, "list", null);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                loadMoreArticles();
            }
        });

        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new RecyclerItemTouchHelper(0, ItemTouchHelper.LEFT|ItemTouchHelper.RIGHT, this);
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(mRecyclerView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_bar_saved_articles, menu);

        MenuItem searchItem = (MenuItem) menu.findItem(R.id.action_search);
        MenuItem filterItem = (MenuItem) menu.findItem(R.id.action_filter);

        // Set up search
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        EditText searchEditText = (EditText) searchView.findViewById(R.id.search_src_text);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                mAdapter.notifyDataSetChanged();
            }
            return false;
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                filterItem.setVisible(false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                filterItem.setVisible(true);
                mSearchText = null;
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void startWebViewActivity(JSONObject article) {
        try {
            Intent intent = new Intent(this, WebViewActivity.class);
            intent.putExtra("id", article.getString("objectID"));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCommentActivity(JSONObject article) {
        try {
            Intent commentIntent = new Intent(this, CommentActivity.class);
            commentIntent.putExtra("id", article.getString("objectID"));
            startActivity(commentIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private void loadMoreArticles() {
        if (isLoadingMore) return;

        int currentPosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
        final int trigger = 5;
        final List<Article> initialList = mAdapter.getList();
        mLoadedList = initialList;
        final Object index;

        if (currentPosition > mAdapter.getItemCount() - trigger) {
            isLoadingMore = true;

            index = mAdapter.getItemCount();

            int indexType = 0;
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
}
