package acorn.com.acorn_app.ui.viewModels;

import androidx.lifecycle.ViewModel;

import java.util.List;

import acorn.com.acorn_app.data.FeedListLiveData;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.FbQuery;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserThemePrefs;

public class FeedViewModel extends ViewModel {
    public static final int QUERY_LIMIT = 100;
    private final NetworkDataSource mDataSource;

    public FeedViewModel(NetworkDataSource dataSource) {
        mDataSource = dataSource;
    }

    private FbQuery queryPlaceholder;

    public FeedListLiveData getArticles(FbQuery query, List<String> themeList, int seed) {
        queryPlaceholder = query;
        return mDataSource.getArticles(query, themeList, seed);
    }

    public FeedListLiveData getArticles(FbQuery query) {
        queryPlaceholder = query;
        return mDataSource.getArticles(query, mUserThemePrefs, null);
    }

    public FeedListLiveData getAdditionalArticles(Object index, int indexType,
                                                  List<String> themeList, int seed) {
        return mDataSource.getAdditionalArticles(queryPlaceholder, index, indexType, themeList, seed);
    }

    public FeedListLiveData getSavedArticles(FbQuery query) {
        return mDataSource.getSavedArticles(query);
    }
}
