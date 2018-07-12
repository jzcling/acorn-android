package acorn.com.acorn_app.ui.viewModels;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;

import java.util.List;

public class UserThemeViewModel extends ViewModel {

    private MutableLiveData<List<String>> userThemeList = new MutableLiveData<>();

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
