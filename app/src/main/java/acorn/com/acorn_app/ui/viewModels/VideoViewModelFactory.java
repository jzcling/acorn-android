package acorn.com.acorn_app.ui.viewModels;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;

import acorn.com.acorn_app.data.NetworkDataSource;

public class VideoViewModelFactory extends ViewModelProvider.NewInstanceFactory {

    private final NetworkDataSource mDataSource;

    public VideoViewModelFactory(NetworkDataSource dataSource) {
        this.mDataSource = dataSource;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        //noinspection unchecked
        return (T) new VideoViewModel(mDataSource);
    }
}