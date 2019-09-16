package acorn.com.acorn_app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Consumer;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.ui.activities.NearbyActivity;

import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class LocationPermissionsUtils {
    private static final String TAG = "LocationPermissionUtils";

    public static final int LOCATION_PERMISSIONS_RC = 1301;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 6000;
    private static final int REQUEST_CHECK_SETTINGS = 7000;
    private static final long UPDATE_INTERVAL = 10000;
    private static final long FASTEST_INTERVAL = 5000;

    private Handler mHandler = new Handler();
    private boolean mPendingResult = false;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private LocationResult mLocationResult;

    private static final Object LOCK = new Object();
    private static LocationPermissionsUtils sInstance;

    public List<String> permissionsToRequest;
    public List<String> permissionsRejected = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();

    private AppCompatActivity activity;

    public LocationPermissionsUtils(AppCompatActivity activity) {
        this.activity = activity;
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
    }

//    public static LocationPermissionsUtils getInstance(AppCompatActivity activity) {
//        if (sInstance == null) {
//            synchronized (LOCK) {
//                sInstance = new LocationPermissionsUtils(activity);
//            }
//        }
//        return sInstance;
//    }

    public void requestLocationPermissions(Runnable onAllGranted) {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        permissionsToRequest = permissionsToRequest(permissions);

        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest
                    .toArray(new String[permissionsToRequest.size()]), LOCATION_PERMISSIONS_RC);
        } else {
            onAllGranted.run();
        }
    }

    public List<String> permissionsToRequest(List<String> requestedPermissions) {
        List<String> result = new ArrayList<>();

        for (String perm : requestedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    public boolean hasPermission(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean CheckPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
            } else {
                if (activity.getLocalClassName().equals("ui.activities.NearbyActivity")) {
                    activity.finish();
                } else {
                    createToast(activity, "Location based notifications disabled", Toast.LENGTH_SHORT);
                }
            }

            return false;
        }

        return true;
    }

    public void checkLocationSettings(Consumer<Location> onSuccess) {
        mLocationRequest = createLocationRequest(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        LocationServices.getSettingsClient(activity)
                .checkLocationSettings(builder.build())
                .addOnCompleteListener(task -> {
                    try {
                        LocationSettingsResponse response = task.getResult(ApiException.class);
                        startLocationUpdates(onSuccess);
                    } catch (ApiException exception) {
                        switch (exception.getStatusCode()) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                // Location settings are not satisfied. But could be fixed by showing the
                                // user a dialog.
                                try {
                                    // Cast to a resolvable exception.
                                    ResolvableApiException resolvable = (ResolvableApiException) exception;
                                    // Show the dialog by calling startResolutionForResult(),
                                    // and check the result in onActivityResult().
                                    resolvable.startResolutionForResult(
                                            activity,
                                            REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException e) {
                                    // Ignore the error.
                                } catch (ClassCastException e) {
                                    // Ignore, should be an impossible error.
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                // Location settings are not satisfied. However, we have no way to fix the
                                // settings so we won't show the dialog.
                                createToast(activity,
                                        "Please enable location services",
                                        Toast.LENGTH_SHORT);
                                if (activity.getLocalClassName().equals("ui.activities.NearbyActivity")) {
                                    activity.finish();
                                }
                                break;
                        }
                    }
                });
    }

    protected LocationRequest createLocationRequest(int priority) {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        locationRequest.setPriority(priority);
        return locationRequest;
    }

    private void startLocationUpdates(Consumer<Location> onComplete) {
        Log.d(TAG, "startLocationUpdates");
        Runnable getResult = () -> {
            onComplete.accept(mLocationResult.getLastLocation());
            stopLocationUpdates();
            mPendingResult = false;
        };

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                mLocationResult = locationResult;
                Log.d(TAG, "current location: " + locationResult.getLastLocation());

                if (locationResult.getLastLocation() != null) {
                    if (mPendingResult) mHandler.removeCallbacks(getResult);
                    mHandler.postDelayed(getResult, 1000);
                    mPendingResult = true;
                }
            }
        };

        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                Looper.getMainLooper());

        mHandler.postDelayed(this::stopLocationUpdates, 10000);
    }

    private void stopLocationUpdates() {
        Log.d(TAG, "stopLocationUpdates");
        if (mLocationCallback != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }
}
