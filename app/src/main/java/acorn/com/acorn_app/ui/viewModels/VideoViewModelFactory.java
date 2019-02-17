package acorn.com.acorn_app.ui.viewModels;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.NonNull;

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