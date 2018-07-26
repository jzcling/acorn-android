package acorn.com.acorn_app.ui.viewModels;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;

import java.util.List;

import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;

public class ArticleViewModel extends ViewModel {
    public static final int QUERY_LIMIT = 10;
    private final NetworkDataSource mDataSource;

    public ArticleViewModel(NetworkDataSource dataSource) {
        mDataSource = dataSource;
    }

    public final MutableLiveData<FbQuery> newQuery = new MutableLiveData<>();
    private FbQuery queryPlaceholder;

    public LiveData<List<Article>> getArticles() {
        return Transformations.switchMap(newQuery, query -> {
            queryPlaceholder = query;
            return mDataSource.getArticles(query);
        });
    }

    public LiveData<List<Article>> getAdditionalArticles(Object index, int indexType) {
        return mDataSource.getAdditionalArticles(queryPlaceholder, index, indexType);
    }
}
