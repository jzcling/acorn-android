package acorn.com.acorn_app.ui.activities;

import android.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.ArticleListLiveData;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.ui.adapters.SavedArticleAdapter;
import acorn.com.acorn_app.ui.adapters.SavedArticleViewHolder;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModel;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModelFactory;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.InjectorUtils;
import acorn.com.acorn_app.utils.SavedArticleTouchHelper;


public class SavedArticlesActivity extends AppCompatActivity
    implements SavedArticleTouchHelper.RecyclerItemTouchHelperListener {
    private static final String TAG = "SavedArticlesActivity";

    //Data
    private NetworkDataSource mDataSource;
    public static FbQuery mQuery;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    //View Models
    private ArticleViewModel mArticleViewModel;
    private final Map<ArticleListLiveData, Observer<List<Article>>> mObservedList = new HashMap<>();

    //RecyclerView
    private RecyclerView mRecyclerView;
    private SavedArticleAdapter mAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private boolean isLoadingMore = false;

    //For restoring state
    private static Parcelable mLlmState;

    //For filtering
    private String[] themeList;
    private boolean[] checkedStatus;
    private String mSearchText;
    private List<String> mThemeFilterList = new ArrayList<>();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_articles);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up data source
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);

        // Set up recycler view
        mRecyclerView = (RecyclerView) findViewById(R.id.saved_articles_rv);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new SavedArticleAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    loadMoreArticles();
                }
            }
        });

        // Set up swipe mechanism
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new SavedArticleTouchHelper(0, ItemTouchHelper.LEFT|ItemTouchHelper.RIGHT, this);
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(mRecyclerView);

        // Set up theme filters
        themeList = getResources().getStringArray(R.array.theme_array);
        checkedStatus = new boolean[themeList.length];
        for (int i = 0; i < themeList.length; i++) {
            checkedStatus[i] = false;
        }

        setUpInitialViewModelObserver();
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
            mSearchText = searchEditText.getText().toString().trim().toLowerCase();
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                mAdapter.clearSearch();
                mAdapter.filterBySearchText(mSearchText);
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
                mAdapter.clearSearch();
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Filter by themes")
                    .setMultiChoiceItems(themeList, checkedStatus, ((dialog, which, isChecked) -> {
                checkedStatus[which] = isChecked;
            }));

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            builder.setPositiveButton("Done", ((dialog, which) -> {
                mThemeFilterList.clear();
                for (int i = 0; i < themeList.length; i++) {
                    if (checkedStatus[i]) {
                        mThemeFilterList.add(themeList[i]);
                    }
                }
                Log.d(TAG, "themeFilterList: " + mThemeFilterList);
                mAdapter.filterByThemes(mThemeFilterList);
            }));

            AlertDialog dialog = builder.create();
            dialog.show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mObservedList.size() > 0) {
            for (ArticleListLiveData liveData : mObservedList.keySet()) {
                liveData.removeObserver(mObservedList.get(liveData));
            }
            mObservedList.clear();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mLlmState = mLinearLayoutManager.onSaveInstanceState();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setUpInitialViewModelObserver() {
        // Set up view model
        ArticleViewModelFactory factory = InjectorUtils.provideArticleViewModelFactory(this.getApplicationContext());
        mArticleViewModel = ViewModelProviders.of(this, factory).get(ArticleViewModel.class);
        mQuery = new FbQuery(2, 0, 0);

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
                        Log.d(TAG, "added: " + currentList.size());
                    } else {
                        //2
                        currentList.set(i, articles.get(i));
                        Log.d(TAG, "set: " + currentList.size());
                    }
                }
                mAdapter.setList(currentList, mThemeFilterList, mSearchText);
            }
        };
        articleListLD.observeForever(articleListObserver);
        mObservedList.put(articleListLD, articleListObserver);
        if (mLlmState != null) {
            mLinearLayoutManager.onRestoreInstanceState(mLlmState);
        }

    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
        if (viewHolder instanceof SavedArticleViewHolder) {
            Article article = mAdapter.getList().get(position);
            mDataSource.removeSavedArticle(article.getObjectID());
            mAdapter.removeItem(position);
        }
    }

    private void loadMoreArticles() {
        if (isLoadingMore) return;

        int currentPosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
        final int trigger = 5;
        final int initialListCount = mAdapter.getItemCount();
        List<Article> currentList = mAdapter.getList();
        final Object index;

        if (currentPosition > mAdapter.getItemCount() - trigger) {
            isLoadingMore = true;

            Article lastArticle = mAdapter.getLastItem();
            index = lastArticle.getObjectID();

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
                    mAdapter.setList(currentList, mThemeFilterList, mSearchText);
                }
            };
            addListLD.observeForever(addListObserver);
            mObservedList.put(addListLD, addListObserver);

            new Handler().postDelayed(()->isLoadingMore = false,1000);
        }
    }
}
