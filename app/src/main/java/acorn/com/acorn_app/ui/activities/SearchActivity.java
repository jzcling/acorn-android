package acorn.com.acorn_app.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.algolia.instantsearch.events.ErrorEvent;
import com.algolia.instantsearch.helpers.InstantSearch;
import com.algolia.instantsearch.helpers.Searcher;
import com.algolia.instantsearch.ui.views.Hits;
import com.algolia.instantsearch.ui.views.SearchBox;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONObject;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.utils.AppExecutors;

import static acorn.com.acorn_app.data.NetworkDataSource.mAlgoliaApiKey;


public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";
    private static final String ALGOLIA_APP_ID = "O96PPLSF19";
    private static final String ALGOLIA_INDEX_NAME = "article";

    private ActionMenuView mAmvView;

    private Searcher mSearcher;
    private SearchView mSearchBox;
    private Hits mHits;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        EventBus.getDefault().register(this);

        mAmvView = toolbar.findViewById(R.id.amvMenu);
        mAmvView.setOnMenuItemClickListener(this::onOptionsItemSelected);

        mSearcher = Searcher.create(ALGOLIA_APP_ID, mAlgoliaApiKey, ALGOLIA_INDEX_NAME);
        mHits = findViewById(R.id.hits);

        mHits.setOnItemClickListener((recyclerView, position, v) -> {

            JSONObject article = mHits.get(position);
            String link = null;
            try {
                link = article.getString("link");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (link != null && !link.equals("")) {
                    startWebViewActivity(article);
                } else {
                    startCommentActivity(article);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Menu amvMenu = mAmvView.getMenu();
        getMenuInflater().inflate(R.menu.app_bar_search, amvMenu);

        new InstantSearch(this, amvMenu, R.id.action_search, mSearcher); // link the Searcher to the UI
        //mSearcher.search(getIntent()); // Show results for empty query (on app launch) / voice query (from intent)

        final MenuItem itemSearch = amvMenu.findItem(R.id.action_search);
        mSearchBox = (SearchBox) itemSearch.getActionView();
        mSearchBox.requestFocus();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    @Subscribe
    public void onErrorEvent(ErrorEvent event) {
        Toast.makeText(this, "Error searching" + event.query.getQuery() + ":" + event.error.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
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
}
