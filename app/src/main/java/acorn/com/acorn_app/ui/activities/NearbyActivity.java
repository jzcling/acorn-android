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
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Consumer;
import androidx.core.view.MenuItemCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.AddressRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.dbStation;
import acorn.com.acorn_app.ui.adapters.NearbyArticleAdapter;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.Logger;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mThemeSearchKey;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
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
    public static final double RADIUS = 1500;

    // Views
    private TextView mLocationTv;
    private RecyclerView mRecyclerView;
    private NearbyArticleAdapter mAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ConstraintLayout mSearchTypeLayout;
    private CheckedTextView mMrtSearchTv;
    private CheckedTextView mKeywordSearchTv;

    // Location
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationResult mLocationResult;
    private LocationCallback mLocationCallback;
    private Double mLatitude;
    private Double mLongitude;
    private String mAddress;
//    private Retrofit mRetrofit;
//    private CompositeDisposable mCompositeDisposable;

    private boolean mPendingResult;
    private Handler mHandler = new Handler();

    // Data
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();
    private AddressRoomDatabase mRoomDb;

    // Search
    private List<dbStation> mStationList;
    private List<String> mStationNames;
    private SimpleCursorAdapter mSearchAdapter;
    private SearchView mSearchView;
    private String mMrtSearchText;
    private String mKeywordSearchText;

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

    private Logger mLogger;


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mLogger = new Logger(this);

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
        mRoomDb = AddressRoomDatabase.getInstance(this);
//        mRetrofit = getRetrofit(MAPS_API_URL);
//        mCompositeDisposable = new CompositeDisposable();

        mSearchTypeLayout = (ConstraintLayout) findViewById(R.id.search_type_layout);
        mMrtSearchTv = (CheckedTextView) findViewById(R.id.search_mrt_stations_tv);
        mKeywordSearchTv = (CheckedTextView) findViewById(R.id.search_keyword_tv);
        mMrtSearchTv.setCheckMarkTintList(getColorStateList(R.color.nearby_search_checkmark_state_list));
        mKeywordSearchTv.setCheckMarkTintList(getColorStateList(R.color.nearby_search_checkmark_state_list));
        mMrtSearchTv.setOnClickListener(v -> {
            if (!mMrtSearchTv.isChecked()) {
                mMrtSearchTv.setChecked(true);
                mKeywordSearchTv.setChecked(false);
                mSearchView.setSuggestionsAdapter(mSearchAdapter);
                if (mMrtSearchText != null) {
                    mSearchView.setQuery(mMrtSearchText, false);
                } else {
                    mSearchView.setQuery(null, false);
                }
            }
        });
        mKeywordSearchTv.setOnClickListener(v -> {
            if (!mKeywordSearchTv.isChecked()) {
                mMrtSearchTv.setChecked(false);
                mKeywordSearchTv.setChecked(true);
                mSearchView.setSuggestionsAdapter(null);
                if (mKeywordSearchText != null) {
                    mSearchView.setQuery(mKeywordSearchText, false);
                } else {
                    mSearchView.setQuery(null, false);
                }
            }
        });

        final String[] from = new String[] {"stationName"};
        final int[] to = new int[] {android.R.id.text1};
        mSearchAdapter = new SimpleCursorAdapter(this,
                R.layout.layout_spinner_text,
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
        mExecutors.diskRead().execute(() -> {
            mStationList = mRoomDb.addressDAO().getAllStations();
            mStationNames = mRoomDb.addressDAO().getAllStationLocales();
            Collections.sort(mStationNames);
        });

        // Set up theme filters
        themeList = getResources().getStringArray(R.array.theme_array);
        checkedStatus = new boolean[themeList.length];
        for (int i = 0; i < themeList.length; i++) {
            checkedStatus[i] = false;
        }

        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            if (mLatitude != null && mLongitude != null && mAddress != null) {
                getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress, mKeywordSearchText);
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
        mSearchView = (SearchView) searchItem.getActionView();
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchTypeLayout.setVisibility(View.VISIBLE);

                if (mMrtSearchTv.isChecked()) {
                    mSearchView.setSuggestionsAdapter(mSearchAdapter);
                    if (mMrtSearchText != null)
                        mSearchView.setQuery(mMrtSearchText, false);
                }

                if (mKeywordSearchTv.isChecked()) {
                    mSearchView.setSuggestionsAdapter(null);
                    if (mKeywordSearchText != null)
                        mSearchView.setQuery(mKeywordSearchText, false);
                }
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchTypeLayout.setVisibility(View.GONE);
                return true;
            }
        });

        mSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                Cursor cursor = (Cursor) mSearchAdapter.getItem(position);
                String txt = cursor.getString(cursor.getColumnIndex("stationName"));
                mSearchView.setQuery(txt, true);
                mMrtSearchText = txt;
                return true;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                Cursor cursor = (Cursor) mSearchAdapter.getItem(position);
                String txt = cursor.getString(cursor.getColumnIndex("stationName"));
                mSearchView.setQuery(txt, true);
                mMrtSearchText = txt;
                return true;
            }
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (mMrtSearchTv.isChecked()) {
                    String address = "";
                    boolean isStationFound = false;
                    for (String name : mStationNames) {
                        if (name.toLowerCase().equals(query.toLowerCase())) {
                            String[] words = query.split(" ");
                            StringBuilder builder = new StringBuilder();
                            for (String word : words) {
                                String firstChar = word.substring(0, 1).toUpperCase();
                                builder.append(firstChar).append(word.substring(1).toLowerCase()).append(" ");
                            }
                            address = builder.toString().trim();
                            mMrtSearchText = address;
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

                    getNearbyArticlesForMrt(address, mKeywordSearchText);
                } else {
                    mKeywordSearchText = query;
                    if (mMrtSearchText != null) {
                        getNearbyArticlesForMrt(mMrtSearchText, mKeywordSearchText);
                    } else {
                        getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress, mKeywordSearchText);
                    }
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (mMrtSearchTv.isChecked()) updateSearchSuggestions(newText);
                return true;
            }
        });

        EditText searchEditText = (EditText) mSearchView.findViewById(R.id.search_src_text);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String searchText = searchEditText.getText().toString().trim();
                if (searchText.equals("")) {
                    if (mMrtSearchTv.isChecked())
                        mMrtSearchText = null;
                    if (mKeywordSearchTv.isChecked())
                        mKeywordSearchText = null;

                    if (mMrtSearchText != null) {
                        getNearbyArticlesForMrt(mMrtSearchText, mKeywordSearchText);
                    } else {
                        getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress, mKeywordSearchText);
                    }
                } else {
                    mSearchView.setQuery(searchText, true);
                }
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
            return false;
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

            AlertDialog dialog = builder.create();
            dialog.show();
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateSearchSuggestions(String query) {
        final MatrixCursor c = new MatrixCursor(new String[]{ BaseColumns._ID, "stationName" });
        for (int i=0; i < mStationNames.size(); i++) {
            if (mStationNames.get(i).toLowerCase().contains(query.toLowerCase()))
                c.addRow(new Object[] {i, mStationNames.get(i)});
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
        if (!mLocationTv.getText().toString().contains("Showing articles")) {
            CheckLocationSettings();
        }
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
                .addOnCompleteListener(task -> {
                    try {
                        LocationSettingsResponse response = task.getResult(ApiException.class);

                        // check if any intent provides an address, if not find articles near user
                        Intent intent = getIntent();
                        String stationName = intent.getStringExtra("stationName");
                        if (intent.getStringExtra("keyword") != null)
                            mKeywordSearchText = intent.getStringExtra("keyword");
                        String campaign = intent.getStringExtra("campaignId");
                        boolean fromNotif = intent.getBooleanExtra("fromNotif", false);
                        String notifType = intent.getStringExtra("notifType");
                        if (mUid == null) mUid = FirebaseAuth.getInstance().getUid();
                        if (mUid != null) {
                            if (stationName != null) {
                                mLogger.logNotificationClicked(fromNotif, notifType, mUid, stationName);
                            } else {
                                mLogger.logNotificationClicked(fromNotif, notifType, mUid, campaign);
                            }
                        } else {
                            if (stationName != null) {
                                mLogger.logNotificationError(fromNotif, notifType, "unknown", stationName);
                            } else {
                                mLogger.logNotificationError(fromNotif, notifType, "unknown", campaign);
                            }
                        }
                        if (stationName != null) {
                            mAddress = stationName;
                            if (mStationList != null) {
                                getNearbyArticlesForMrt(mAddress);
                            } else {
                                // Set up mrt station list
                                mExecutors.diskRead().execute(() -> {
                                    mStationList = mRoomDb.addressDAO().getAllStations();
                                    mStationNames = mRoomDb.addressDAO().getAllStationLocales();
                                    Collections.sort(mStationNames);

                                    getNearbyArticlesForMrt(mAddress);
                                });
                            }
                        } else {
                            getReverseGeocode();
                        }
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
                    mHandler.postDelayed(getResult, 500);
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

//    private Retrofit getRetrofit(String baseUrl) {
//        return new Retrofit.Builder()
//                .baseUrl(baseUrl)
//                .addConverterFactory(GsonConverterFactory.create())
//                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
//                .build();
//    }

    private void getReverseGeocode() {
        startLocationUpdates(location -> {
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
                if (mKeywordSearchText != null) {
                    mLocationTv.setText("Fetching articles about " + mKeywordSearchText + " near " + mAddress);
                } else {
                    mLocationTv.setText("Fetching articles near " + mAddress);
                }
                getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress, mKeywordSearchText);
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

    private void getNearbyArticlesForMrt(String station) {
        getNearbyArticlesForMrt(station, null);
    }

    private void getNearbyArticlesForMrt(String name, @Nullable String keyword) {
        mSwipeRefreshLayout.setRefreshing(true);
        mAdapter.clear();
        mExecutors.diskRead().execute(() -> {
            dbStation location = mRoomDb.addressDAO().getStation(name);
            if (location != null) {
                mLatitude = location.latitude;
                mLongitude = location.longitude;
                mAddress = name;
                if (keyword != null) {
                    mExecutors.mainThread().execute(() -> {
                        mLocationTv.setText("Fetching articles about " + keyword + " near " + mAddress);
                    });
                    getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress, keyword);
                } else {
                    mExecutors.mainThread().execute(() -> {
                        mLocationTv.setText("Fetching articles near " + mAddress);
                    });
                    getNearbyArticles(mLatitude, mLongitude, RADIUS, mAddress, keyword);
                }
            } else {
                mSwipeRefreshLayout.setRefreshing(false);
                mExecutors.mainThread().execute(() -> {
                    mLocationTv.setText("Could not get location for " + name);
                });
            }
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

    private void getNearbyArticles(double lat, double lng, double radius, String address,
                                   @Nullable String keyword) {
        mExecutors.networkIO().execute(() -> {
            mDataSource.getNearbyArticles(lat, lng, radius, keyword, (articles -> {
                mAdapter.setList(articles);
                if (articles.size() > 0) {
                    if (keyword != null) {
                        mLocationTv.setText("Showing articles about " + keyword + " near " + address);
                    } else {
                        mLocationTv.setText("Showing articles near " + address);
                    }
                } else {
                    if (keyword != null) {
                        mLocationTv.setText("Could not find any articles about " + keyword + " near " + address);
                    } else {
                        mLocationTv.setText("Could not find any articles near " + address);
                    }
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }));
        });
    }
}
