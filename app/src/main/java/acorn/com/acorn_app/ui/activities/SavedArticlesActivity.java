package acorn.com.acorn_app.ui.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.AddressRoomDatabase;
import acorn.com.acorn_app.data.FeedListLiveData;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.ui.adapters.SavedArticleAdapter;
import acorn.com.acorn_app.ui.adapters.SavedArticleViewHolder;
import acorn.com.acorn_app.ui.viewModels.FeedViewModel;
import acorn.com.acorn_app.ui.viewModels.FeedViewModelFactory;
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
    private AddressRoomDatabase mAddressRoomDb;

    //View Models
    private FeedViewModel mFeedViewModel;
    private final Map<FeedListLiveData, Observer<List<Object>>> mObservedList = new HashMap<>();

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

    private ProgressBar mProgressBar;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_articles);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up data source
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);
        mAddressRoomDb = AddressRoomDatabase.getInstance(this);

        // Set up recycler view
        mRecyclerView = (RecyclerView) findViewById(R.id.saved_articles_rv);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new SavedArticleAdapter(this);
        mRecyclerView.setAdapter(mAdapter);

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

        mProgressBar = findViewById(R.id.saved_articles_pb);

        setUpInitialViewModelObserver();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_bar_saved_articles, menu);

        MenuItem searchItem = (MenuItem) menu.findItem(R.id.action_search);
        MenuItem filterItem = (MenuItem) menu.findItem(R.id.action_filter);

        // Set up search
        SearchView searchView = (SearchView) searchItem.getActionView();
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
            for (FeedListLiveData liveData : mObservedList.keySet()) {
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
        FeedViewModelFactory factory = InjectorUtils.provideArticleViewModelFactory(this.getApplicationContext());
        mFeedViewModel = new ViewModelProvider(this, factory).get(FeedViewModel.class);
        mQuery = new FbQuery(2, 0, 0);

        FeedListLiveData articleListLD = mFeedViewModel.getArticles(mQuery);
        Observer<List<Object>> articleListObserver = items -> {
            if (items != null) {
                List<Article> articleList = new ArrayList<>();
                for (Object item : items) {
                    articleList.add((Article) item);
                }
                mAdapter.setList(articleList, mThemeFilterList, mSearchText, () -> {
                    if (mLlmState != null) {
                        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
                        mLlmState = null;
                    }
                });
                mProgressBar.setVisibility(View.GONE);
            }
        };
        articleListLD.observe(this, articleListObserver);
        mObservedList.put(articleListLD, articleListObserver);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
        if (viewHolder instanceof SavedArticleViewHolder) {
            Article article = mAdapter.getList().get(position);
            mDataSource.removeSavedArticle(article.getObjectID());
            mAdapter.removeItem(position);
        }
    }
}
