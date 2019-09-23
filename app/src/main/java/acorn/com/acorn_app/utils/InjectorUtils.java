/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package acorn.com.acorn_app.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import acorn.com.acorn_app.data.AddressRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.ui.AcornApplication;
import acorn.com.acorn_app.ui.viewModels.FeedViewModelFactory;
import acorn.com.acorn_app.ui.viewModels.UserViewModelFactory;
import acorn.com.acorn_app.ui.viewModels.VideoViewModelFactory;

public class InjectorUtils {
    private static Application provideApplication(AppCompatActivity activity) {
        return activity.getApplication();
    }

    private static SharedPreferences provideSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static AppExecutors provideAppExecutors() {
        return AppExecutors.getInstance();
    }

    private static NetworkDataSource provideNetworkDataSource(Context context) {
        AppExecutors executors = AppExecutors.getInstance();
        return NetworkDataSource.getInstance(context.getApplicationContext(), executors);
    }

    private static AddressRoomDatabase provideAddressRoomDatabase(Context context) {
        return AddressRoomDatabase.getInstance(context.getApplicationContext());
    }

    private static GeofenceUtils provideGeofenceUtils(Context context) {
        NetworkDataSource dataSource = provideNetworkDataSource(context);
        return GeofenceUtils.getInstance(context.getApplicationContext(), dataSource);
    }

    private static LocationPermissionsUtils provideLocationPermissionUtils(AppCompatActivity activity) {
        return LocationPermissionsUtils.getInstance(activity);
    }

    public static FeedViewModelFactory provideArticleViewModelFactory(Context context) {
        NetworkDataSource dataSource = provideNetworkDataSource(context.getApplicationContext());
        return new FeedViewModelFactory(dataSource);
    }

    public static VideoViewModelFactory provideVideoViewModelFactory(Context context) {
        NetworkDataSource dataSource = provideNetworkDataSource(context.getApplicationContext());
        return new VideoViewModelFactory(dataSource);
    }

    public static UserViewModelFactory provideUserViewModelFactory(AppCompatActivity activity, Context context) {
        Application application = provideApplication(activity);
        SharedPreferences sharedPreferences = provideSharedPreferences(context);
        AppExecutors executors = provideAppExecutors();
        NetworkDataSource dataSource = provideNetworkDataSource(context.getApplicationContext());
        AddressRoomDatabase addressRoomDatabase = provideAddressRoomDatabase(context);
        GeofenceUtils geofenceUtils = provideGeofenceUtils(context);
        LocationPermissionsUtils locationPermissionsUtils = provideLocationPermissionUtils(activity);
        return new UserViewModelFactory(application, sharedPreferences, executors,
                dataSource, addressRoomDatabase, geofenceUtils, locationPermissionsUtils);
    }
}