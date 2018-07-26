package acorn.com.acorn_app.ui.viewModels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import java.util.List;

import acorn.com.acorn_app.models.Notif;
import acorn.com.acorn_app.utils.UiUtils;

public class NotificationViewModel extends AndroidViewModel {
    private final Application application;

    public static final MutableLiveData<SharedPreferences> sharedPrefs = new MutableLiveData<>();

    public NotificationViewModel(@NonNull Application application) {
        super(application);
        this.application = application;
    }

    public LiveData<List<Notif>> getNotificationList() {
        return Transformations.switchMap(sharedPrefs, NotifListLiveData::new);
    }

    public class NotifListLiveData extends LiveData<List<Notif>> {
        public NotifListLiveData(SharedPreferences prefs) {
            setValue(UiUtils.getNotificationList(application, prefs));
        }
    }
}
