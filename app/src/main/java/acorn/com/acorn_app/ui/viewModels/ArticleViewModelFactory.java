package acorn.com.acorn_app.ui.viewModels;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;

import acorn.com.acorn_app.data.NetworkDataSource;

public class ArticleViewModelFactory extends ViewModelProvider.NewInstanceFactory {

    private final NetworkDataSource mDataSource;

    public ArticleViewModelFactory(NetworkDataSource dataSource) {
        this.mDataSource = dataSource;
    }

    @Override
    public <T extends ViewModel> T create(Class<T> modelClass) {
        //noinspection unchecked
        return (T) new ArticleViewModel(mDataSource);
    }
}