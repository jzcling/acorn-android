package acorn.com.acorn_app.ui.viewModels;

import android.location.Location;

import androidx.lifecycle.ViewModel;

import java.util.List;

import acorn.com.acorn_app.data.ArticleListLiveData;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.FbQuery;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserThemePrefs;

public class ArticleViewModel extends ViewModel {
    public static final int QUERY_LIMIT = 100;
    private final NetworkDataSource mDataSource;

    public ArticleViewModel(NetworkDataSource dataSource) {
        mDataSource = dataSource;
    }

    private FbQuery queryPlaceholder;

    public ArticleListLiveData getArticles(FbQuery query, List<String> themeList) {
        queryPlaceholder = query;
        return mDataSource.getArticles(query, themeList);
    }

    public ArticleListLiveData getArticles(FbQuery query) {
        queryPlaceholder = query;
        return mDataSource.getArticles(query, mUserThemePrefs);
    }

    public ArticleListLiveData getAdditionalArticles(Object index, int indexType, List<String> themeList) {
        return mDataSource.getAdditionalArticles(queryPlaceholder, index, indexType, themeList);
    }

    public ArticleListLiveData getSavedArticles(FbQuery query) {
        return mDataSource.getSavedArticles(query);
    }
}
