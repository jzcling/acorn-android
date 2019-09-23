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
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.ui.adapters.SavedArticleAdapter;
import acorn.com.acorn_app.ui.adapters.SavedArticleViewHolder;
import acorn.com.acorn_app.ui.viewModels.FeedViewModel;
import acorn.com.acorn_app.ui.viewModels.FeedViewModelFactory;
import acorn.com.acorn_app.ui.viewModels.UserViewModel;
import acorn.com.acorn_app.ui.viewModels.UserViewModelFactory;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.InjectorUtils;
import acorn.com.acorn_app.utils.SavedArticleTouchHelper;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;


public class ItemListActivity extends AppCompatActivity {
    private static final String TAG = "ItemListActivity";

    //View Models
    private UserViewModel mUserViewModel;

    // Type
    private UserViewModel.UserAction mAction;

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

        // Get Action Type
        String type = getIntent().getStringExtra("userAction");
        if (type != null) {
            switch (type) {
                case "upvote":
                    mAction = UserViewModel.UserAction.UPVOTE;
                    break;
                case "downvote":
                    mAction = UserViewModel.UserAction.DOWNVOTE;
                    break;
                case "comment":
                    mAction = UserViewModel.UserAction.COMMENT;
                    break;
                case "post":
                    mAction = UserViewModel.UserAction.POST;
                    break;
                case "history":
                    mAction = UserViewModel.UserAction.HISTORY;
                    break;
            }
        } else {
            finish();
        }

        // Set up recycler view
        mRecyclerView = (RecyclerView) findViewById(R.id.saved_articles_rv);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new SavedArticleAdapter(this);
        mRecyclerView.setAdapter(mAdapter);

        // Set up theme filters
        themeList = getResources().getStringArray(R.array.theme_array);
        checkedStatus = new boolean[themeList.length];
        for (int i = 0; i < themeList.length; i++) {
            checkedStatus[i] = false;
        }

        mProgressBar = findViewById(R.id.saved_articles_pb);

        // get data
        getAndDisplayData();
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

    private void getAndDisplayData() {
        UserViewModelFactory factory = InjectorUtils.provideUserViewModelFactory(this, getApplicationContext());
        mUserViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);
        mUserViewModel.getItemListFor(mUid, mAction, items -> {
            if (items != null) {
                List<Article> articleList = new ArrayList<>();
                for (Object item : items) {
                    if (item instanceof  Article) {
                        articleList.add((Article) item);
                    } else if (item instanceof Video) {
                        articleList.add(((Video) item).toArticle());
                    }
                }
                mAdapter.setList(articleList, mThemeFilterList, mSearchText, () -> {
                    if (mLlmState != null) {
                        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
                        mLlmState = null;
                    }
                });
                mProgressBar.setVisibility(View.GONE);
            }
        });
    }
}
