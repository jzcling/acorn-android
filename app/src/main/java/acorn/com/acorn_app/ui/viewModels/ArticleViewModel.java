package acorn.com.acorn_app.ui.viewModels;

import androidx.lifecycle.ViewModel;

import acorn.com.acorn_app.data.ArticleListLiveData;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.FbQuery;

public class ArticleViewModel extends ViewModel {
    public static final int QUERY_LIMIT = 10;
    private final NetworkDataSource mDataSource;

    public ArticleViewModel(NetworkDataSource dataSource) {
        mDataSource = dataSource;
    }

    private FbQuery queryPlaceholder;

    public ArticleListLiveData getArticles(FbQuery query) {
        queryPlaceholder = query;
        return mDataSource.getArticles(query);
    }

    public ArticleListLiveData getAdditionalArticles(Object index, int indexType) {
        return mDataSource.getAdditionalArticles(queryPlaceholder, index, indexType);
    }

    public ArticleListLiveData getSavedArticles(FbQuery query) {
        return mDataSource.getSavedArticles(query);
    }
}
