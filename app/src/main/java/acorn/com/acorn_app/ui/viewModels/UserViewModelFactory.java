package acorn.com.acorn_app.ui.viewModels;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import acorn.com.acorn_app.data.AddressRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.GeofenceUtils;
import acorn.com.acorn_app.utils.LocationPermissionsUtils;

public class UserViewModelFactory extends ViewModelProvider.NewInstanceFactory {

    private final Application mApplication;
    private final SharedPreferences mSharedPreferences;
    private final AppExecutors mExecutors;
    private final NetworkDataSource mDataSource;
    private final AddressRoomDatabase mAddressRoomDb;
    private final GeofenceUtils mGeofenceUtils;
    private final LocationPermissionsUtils mLocationPermissionUtils;

    public UserViewModelFactory(Application application, SharedPreferences sharedPreferences,
                                AppExecutors executors, NetworkDataSource dataSource,
                                AddressRoomDatabase addressRoomDb, GeofenceUtils geofenceUtils,
                                LocationPermissionsUtils locationPermissionsUtils) {
        mApplication = application;
        mSharedPreferences = sharedPreferences;
        mExecutors = executors;
        mDataSource = dataSource;
        mAddressRoomDb = addressRoomDb;
        mGeofenceUtils = geofenceUtils;
        mLocationPermissionUtils = locationPermissionsUtils;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        //noinspection unchecked
        return (T) new UserViewModel(mApplication, mSharedPreferences, mExecutors,
                mDataSource, mAddressRoomDb, mGeofenceUtils, mLocationPermissionUtils);
    }
}