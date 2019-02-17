package acorn.com.acorn_app.ui.viewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class UserThemeViewModel extends ViewModel {

    private final MutableLiveData<List<String>> userThemeList = new MutableLiveData<>();

    public LiveData<List<String>> getThemes() {
        return Transformations.switchMap(userThemeList, UserThemeListLiveData::new);
    }

    public void setValue(List<String> userThemeList) {
        this.userThemeList.setValue(userThemeList);
    }

    private class UserThemeListLiveData extends LiveData<List<String>> {
        public UserThemeListLiveData(List<String> userThemeList) {
            setValue(userThemeList);
        }
    }
}
