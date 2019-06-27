package acorn.com.acorn_app.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Consumer;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.ArticleListLiveData;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.FbQuery;
import acorn.com.acorn_app.models.ReverseGeocode;
import acorn.com.acorn_app.services.MapsApiService;
import acorn.com.acorn_app.ui.adapters.NearbyArticleAdapter;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModel;
import acorn.com.acorn_app.ui.viewModels.ArticleViewModelFactory;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.InjectorUtils;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class NearbyActivity extends AppCompatActivity {
    // Static vars
    private static final String TAG = "NearbyActivity";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 6000;
    private static final int REQUEST_CHECK_SETTINGS = 7000;
    private static final long UPDATE_INTERVAL = 10000;
    private static final long FASTEST_INTERVAL = 5000;
    private static final int ALL_PERMISSIONS_RESULT = 1011;
    private static final String MAPS_API_URL = "https://maps.googleapis.com/maps/api/";
    private static final double RADIUS = 2000;

    // Views
    private TextView mLocationTv;
    private RecyclerView mRecyclerView;
    private NearbyArticleAdapter mAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    // Location
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private Double mLatitude;
    private Double mLongitude;
    private String mAddress;
//    private Retrofit mRetrofit;
//    private CompositeDisposable mCompositeDisposable;

    // Data
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    // Search
    private Map<String, Map<String, Double>> mMrtStationMap;
    private List<String> mMrtStationNames;
    private SimpleCursorAdapter mSearchAdapter;

    //For restoring state
    private static Parcelable mLlmState;

    // Permissions
    private List<String> permissionsToRequest;
    private List<String> permissionsRejected = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();

    //For filtering
    private String[] themeList;
    private boolean[] checkedStatus;
    private List<String> mThemeFilterList = new ArrayList<>();


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mLocationTv = findViewById(R.id.location_tv);
        mRecyclerView = findViewById(R.id.nearby_rv);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new NearbyArticleAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.nearby_sr);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);
//        mRetrofit = getRetrofit(MAPS_API_URL);
//        mCompositeDisposable = new CompositeDisposable();

        final String[] from = new String[] {"stationName"};
        final int[] to = new int[] {android.R.id.text1};
        mSearchAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1,
                null,
                from,
                to,
                CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        permissionsToRequest = permissionsToRequest(permissions);

        if (permissionsToRequest.size() > 0) {
            requestPermissions(permissionsToRequest
                    .toArray(new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
        }

        // Set up mrt station list
        mDataSource.getMrtStations((mrtStations) -> {
            mMrtStationMap = mrtStations;
            mMrtStationNames = new ArrayList<>(mrtStations.keySet());
            Collections.sort(mMrtStationNames);
        }, (error) -> {
            createToast(this, "Error getting list of MRT Stations", Toast.LENGTH_SHORT);
        });

        // Set up theme filters
        themeList = getResources().getStringArray(R.array.theme_array);
        checkedStatus = new boolean[themeList.length];
        for (int i = 0; i < themeList.length; i++) {
            checkedStatus[i] = false;
        }

        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            if (mLatitude != null && mLongitude != null && mAddress != null) {
                getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress);
            } else {
                createToast(this, "Could not determine your search location", Toast.LENGTH_SHORT);
            }
        });
        mSwipeRefreshLayout.setRefreshing(true);
    }

    private List<String> permissionsToRequest(List<String> requestedPermissions) {
        List<String> result = new ArrayList<>();

        for (String perm : requestedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_bar_nearby, menu);
        MenuItem searchItem = (MenuItem) menu.findItem(R.id.action_search);
        MenuItem filterItem = (MenuItem) menu.findItem(R.id.action_filter);

        // Set up search
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSuggestionsAdapter(mSearchAdapter);
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                Cursor cursor = (Cursor) mSearchAdapter.getItem(position);
                String txt = cursor.getString(cursor.getColumnIndex("stationName"));
                searchView.setQuery(txt, true);
                return true;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                Cursor cursor = (Cursor) mSearchAdapter.getItem(position);
                String txt = cursor.getString(cursor.getColumnIndex("stationName"));
                searchView.setQuery(txt, true);
                return true;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String address = "";
                boolean isStationFound = false;
                for (String name : mMrtStationNames) {
                    if (name.toLowerCase().equals(query.toLowerCase())) {
                        address = name;
                        isStationFound = true;
                    }
                }

                if (!isStationFound) {
                    createToast(NearbyActivity.this, "Please choose from one of the MRT Stations suggested", Toast.LENGTH_SHORT);
                    return false;
                }

                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                mSwipeRefreshLayout.setRefreshing(true);
                mAdapter.clear();
                Map<String, Double> location = mMrtStationMap.get(address);
                if (location != null) {
                    mLatitude = location.get("latitude");
                    mLongitude = location.get("longitude");
                    mAddress = address;
                    if (mLatitude != null && mLongitude != null) {
                        mLocationTv.setText("Fetching articles near " + mAddress);
                        getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress);
                    }
                } else {
                    mSwipeRefreshLayout.setRefreshing(false);
                    createToast(NearbyActivity.this, "Could not get location for " + query, Toast.LENGTH_SHORT);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                updateSearchSuggestions(newText);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Filter by themes")
                    .setMultiChoiceItems(themeList, checkedStatus, ((dialog, which, isChecked) -> {
                        checkedStatus[which] = isChecked;
                    }));

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            builder.setPositiveButton("Done", ((dialog, which) -> {
                mThemeFilterList.clear();
                for (int i = 0; i < themeList.length; i++) {
                    if (checkedStatus[i]) {
                        mThemeFilterList.add(themeList[i]);
                    }
                }
                Log.d(TAG, "themeFilterList: " + mThemeFilterList);
                mAdapter.filterByThemes(mThemeFilterList);
            }));

            android.app.AlertDialog dialog = builder.create();
            dialog.show();
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateSearchSuggestions(String query) {
        final MatrixCursor c = new MatrixCursor(new String[]{ BaseColumns._ID, "stationName" });
        for (int i=0; i < mMrtStationNames.size(); i++) {
            if (mMrtStationNames.get(i).toLowerCase().contains(query.toLowerCase()))
                c.addRow(new Object[] {i, mMrtStationNames.get(i)});
        }
        mSearchAdapter.changeCursor(c);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!CheckPlayServices()) {
            createToast(this, "Please update Google Play Services to enable this feature", Toast.LENGTH_SHORT);
            finish();
        }
        CheckLocationSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // stop location updates
    }

    @Override
    protected void onDestroy() {
//        if (!mCompositeDisposable.isDisposed()) {
//            mCompositeDisposable.dispose();
//        }
        super.onDestroy();
    }

    protected LocationRequest createLocationRequest(int priority) {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        locationRequest.setPriority(priority);
        return locationRequest;
    }

//    private void startLocationUpdates() {
//        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
//                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            Toast.makeText(this, "You need to enable permissions to display location !", Toast.LENGTH_SHORT).show();
//        }
//
//        mFusedLocationClient.requestLocationUpdates();
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case ALL_PERMISSIONS_RESULT:
                for (String perm : permissionsToRequest) {
                    if (!hasPermission(perm)) {
                        permissionsRejected.add(perm);
                    }
                }

                if (permissionsRejected.size() > 0) {
                    if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                        new AlertDialog.Builder(NearbyActivity.this)
                                .setMessage("Please enable locations permissions to use this feature.")
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        requestPermissions(permissionsRejected.toArray(new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                }).create().show();
                        return;
                    }
                }
                getReverseGeocode();

                break;
        }
    }

    private boolean CheckPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
            } else {
                finish();
            }

            return false;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case AppCompatActivity.RESULT_OK:
                        // All required changes were successfully made
                        getReverseGeocode();
                        break;
                    case AppCompatActivity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        createToast(this, "Please enable location services to use this feature", Toast.LENGTH_SHORT);
                        finish();
                        break;
                    default:
                        break;
                }
                break;
        }
    }

    private void CheckLocationSettings() {
        mSwipeRefreshLayout.setRefreshing(true);
        mLocationRequest = createLocationRequest(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        LocationServices.getSettingsClient(this)
                .checkLocationSettings(builder.build())
                .addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                        try {
                            LocationSettingsResponse response = task.getResult(ApiException.class);
                            getReverseGeocode();
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
                                                NearbyActivity.this,
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
                                    createToast(NearbyActivity.this,
                                            "Please enable location services to use this feature",
                                            Toast.LENGTH_SHORT);
                                    finish();
                                    break;
                            }
                        }
                    }
                });
    }

    @SuppressLint("MissingPermission")
    private void getLastLocation(Consumer<Location> onComplete) {
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            onComplete.accept(location);
                        }
                    }
                });
    }

//    private Retrofit getRetrofit(String baseUrl) {
//        return new Retrofit.Builder()
//                .baseUrl(baseUrl)
//                .addConverterFactory(GsonConverterFactory.create())
//                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
//                .build();
//    }

    private void getReverseGeocode() {
        getLastLocation(location -> {
            Log.d(TAG, "location: " + location.getLatitude() + ", " + location.getLongitude());
            final String RESULT_TYPE = "street_address";
            final String LOCATION_TYPE = "ROOFTOP";

            mLatitude = location.getLatitude();
            mLongitude = location.getLongitude();
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(mLatitude, mLongitude, 1);
                mAddress = "";
                for (int i = 0; i <= addresses.get(0).getMaxAddressLineIndex(); i++) {
                    Log.d(TAG, "address: " + addresses.get(0).getAddressLine(i));
                    mAddress += addresses.get(0).getAddressLine(i);
                }
                mLocationTv.setText("Fetching articles near " + mAddress);
                getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress);
            } catch (IOException error) {
                Log.d(TAG, "Error: " + error.getLocalizedMessage());
                mLocationTv.setText("Error getting location");
            }

//            mDataSource.getMapsApiKey((key) -> {
//                MapsApiService mapsApiService = mRetrofit.create(MapsApiService.class);
//                String latlng = location.getLatitude() + "," + location.getLongitude();
//                mLatitude = location.getLatitude();
//                mLongitude = location.getLongitude();
//
//                Single<ReverseGeocode> reverseGeocode =
//                        mapsApiService.getReverseGeocode(key, latlng, RESULT_TYPE, LOCATION_TYPE);
//                reverseGeocode.subscribeOn(Schedulers.io())
//                        .observeOn(AndroidSchedulers.mainThread())
//                        .subscribe(new SingleObserver<ReverseGeocode>() {
//                            @Override
//                            public void onSubscribe(Disposable d) {
//                                mCompositeDisposable.add(d);
//                            }
//
//                            @Override
//                            public void onSuccess(ReverseGeocode reverseGeocode) {
//                                mAddress = reverseGeocode.getResults().get(0).getFormattedAddress();
//                                mLocationTv.setText("Fetching articles near " + mAddress);
//                                getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress);
//                            }
//
//                            @Override
//                            public void onError(Throwable e) {
//                                createToast(NearbyActivity.this, "Error getting location", Toast.LENGTH_SHORT);
//                                Log.d(TAG, "error getting reverse geocode: " + e.getLocalizedMessage());
//                            }
//                        });
//            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mLlmState = mLinearLayoutManager.onSaveInstanceState();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mLinearLayoutManager.onRestoreInstanceState(mLlmState);
    }

    private void getNearbyArticles(double lat, double lng, double radius, String address) {
        mDataSource.getNearbyArticles(lat, lng, radius, (articles -> {
            mAdapter.setList(articles);
            mLocationTv.setText("Showing articles near " + address);
            mSwipeRefreshLayout.setRefreshing(false);
        }));
    }
}
