package acorn.com.acorn_app.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.AddressRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.dbAddress;
import acorn.com.acorn_app.models.dbStation;
import acorn.com.acorn_app.services.GeofenceBroadcastReceiver;
import acorn.com.acorn_app.services.GeofenceTransitionsJobIntentService;

public class GeofenceUtils {
    private static final String TAG = "GeofenceUtils";
    private static final Object LOCK = new Object();

    private static GeofenceUtils sInstance;
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();
    private AddressRoomDatabase mRoomDb;

    private SharedPreferences mSharedPreferences;
    public boolean mPreChangeValue;

    private GeofencingClient mGeofencingClient;
    public enum PendingGeofenceTask { ADD, REMOVE, NONE }
    private ArrayList<Geofence> mGeofenceList = new ArrayList<>();
    private ArrayList<Geofence> mStationGeofenceList = new ArrayList<>();
    private ArrayList<Geofence> mAddressGeofenceList = new ArrayList<>();
    private PendingIntent mGeofencePendingIntent = null;
    public PendingGeofenceTask mPendingGeofenceTask = PendingGeofenceTask.NONE;

    private GeofenceUtils() { }

    public static GeofenceUtils getInstance(Context context, NetworkDataSource dataSource) {
        if (sInstance == null) {
            synchronized (LOCK) {
                sInstance = new GeofenceUtils();
                sInstance.initiate(context, dataSource);
            }
        }
        return sInstance;
    }

    private void initiate(Context context, NetworkDataSource dataSource) {
        mDataSource = dataSource;
        mGeofencingClient = LocationServices.getGeofencingClient(context);
        mSharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        mRoomDb = AddressRoomDatabase.getInstance(context);
    }

    // Geofence methods
    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL
                | GeofencingRequest.INITIAL_TRIGGER_ENTER);

        // Add the geofences to be monitored by geofencing service.
        builder.addGeofences(mGeofenceList);

        // Return a GeofencingRequest.
        return builder.build();
    }

    @SuppressWarnings("MissingPermission")
    private void addGeofences(Context context) {
        Log.d(TAG, "addGeofences");
        mGeofencingClient.addGeofences(getGeofencingRequest(), getGeofencePendingIntent(context))
                .addOnCompleteListener(task -> {
                    mPendingGeofenceTask = PendingGeofenceTask.NONE;
                    if (task.isSuccessful()) {
                        updateGeofencesAdded(context, !mPreChangeValue);
                        Log.d(TAG, context.getString(R.string.geofences_added));
                    } else {
                        mSharedPreferences.edit()
                                .putBoolean(context.getString(R.string.pref_key_notif_location), mPreChangeValue)
                                .apply();
                        // Get the status code for the error and log it using a user-friendly message.
                        String errorMessage = GeofenceErrorMessages.getErrorString(context, task.getException());
                        Log.d(TAG, "geofence error: " + errorMessage);
                    }
                });
    }

    @SuppressWarnings("MissingPermission")
    private void removeGeofences(Context context, Runnable onComplete) {
        mGeofencingClient.removeGeofences(getGeofencePendingIntent(context)).addOnCompleteListener(task -> {
            mPendingGeofenceTask = PendingGeofenceTask.NONE;
            if (task.isSuccessful()) {
                updateGeofencesAdded(context, !mPreChangeValue);
                Log.d(TAG, context.getString(R.string.geofences_removed));
            } else {
                mSharedPreferences.edit()
                        .putBoolean(context.getString(R.string.pref_key_notif_location), mPreChangeValue)
                        .apply();
                // Get the status code for the error and log it using a user-friendly message.
                String errorMessage = GeofenceErrorMessages.getErrorString(context, task.getException());
                Log.d(TAG, "geofence error: " + errorMessage);
            }
            mGeofenceList.clear();
            if (onComplete != null) onComplete.run();
        });
    }

    private PendingIntent getGeofencePendingIntent(Context context) {
        Log.d(TAG, "getGeofencePendingIntent");
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            Log.d(TAG, "geofence pending intent reused");
            return mGeofencePendingIntent;
        }
        Log.d(TAG, "new geofence pending intent");
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        mGeofencePendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(),
                GeofenceTransitionsJobIntentService.PENDINGINTENT_RC, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }

    private void populateGeofenceList(String station, Runnable onComplete) {
        mExecutors.diskRead().execute(() -> {
            List<dbStation> stations = mRoomDb.addressDAO().getAllStations();

            // add 6 closest saved addresses
            TaskCompletionSource<Boolean> savedAddressSource = new TaskCompletionSource<>();
            Task<Boolean> savedAddressTask = savedAddressSource.getTask();
            getNearestSavedAddressesFrom(station, 6, stations, addresses -> {
                for (Map.Entry<String, Location> entry : addresses.entrySet()) {
                    double latitude = entry.getValue().getLatitude();
                    double longitude = entry.getValue().getLongitude();
                    mAddressGeofenceList.add(new Geofence.Builder()
                            .setRequestId("article_" + entry.getKey())
                            .setCircularRegion(
                                    latitude,
                                    longitude,
                                    1000
                            )
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL
                                    | Geofence.GEOFENCE_TRANSITION_EXIT)
//                                    | Geofence.GEOFENCE_TRANSITION_ENTER)
                            .setLoiteringDelay(10 * 60 * 1000) // 10 min
                            .build());
                }
                savedAddressSource.setResult(true);
            });

            // add 6 closest mrt stations
            TaskCompletionSource<Boolean> mrtSource = new TaskCompletionSource<>();
            Task<Boolean> mrtTask = mrtSource.getTask();
            getNearestStationsFrom(station, 6, stations, stationMap -> {
                for (Map.Entry<String, Location> entry : stationMap.entrySet()) {
                    double latitude = entry.getValue().getLatitude();
                    double longitude = entry.getValue().getLongitude();
                    mStationGeofenceList.add(new Geofence.Builder()
                            .setRequestId(entry.getKey())
                            .setCircularRegion(
                                    latitude,
                                    longitude,
                                    1000
                            )
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL
                                    | Geofence.GEOFENCE_TRANSITION_EXIT)
//                                    | Geofence.GEOFENCE_TRANSITION_ENTER)
                            .setLoiteringDelay(10 * 60 * 1000) // 10 min
                            .build());
                }
                mrtSource.setResult(true);
            });

            Tasks.whenAll(savedAddressTask, mrtTask).addOnSuccessListener(aVoid -> {
                mGeofenceList.addAll(mStationGeofenceList);
                mGeofenceList.addAll(mAddressGeofenceList);
                Log.d(TAG, "Geofence List: " + mGeofenceList.size() + ", " + mGeofenceList.toString());

                mStationGeofenceList.clear();
                mAddressGeofenceList.clear();
                onComplete.run();
            });
        });
    }

    private void populateGeofenceList(Location location, Runnable onComplete) {
        Log.d(TAG, "populateGeofenceList");
        // add 6 closest saved addresses
        TaskCompletionSource<Boolean> savedAddressSource = new TaskCompletionSource<>();
        Task<Boolean> savedAddressTask = savedAddressSource.getTask();
        getNearestSavedAddressesFrom(location, 6, addresses -> {
            for (Map.Entry<String, Location> entry : addresses.entrySet()) {
                double latitude = entry.getValue().getLatitude();
                double longitude = entry.getValue().getLongitude();
                mGeofenceList.add(new Geofence.Builder()
                        .setRequestId("article_" + entry.getKey())
                        .setCircularRegion(
                                latitude,
                                longitude,
                                1000
                        )
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL
                                | Geofence.GEOFENCE_TRANSITION_EXIT)
//                                | Geofence.GEOFENCE_TRANSITION_ENTER)
                        .setLoiteringDelay(10 * 60 * 1000) // 10 min
                        .build());
            }
            Log.d(TAG, "savedAddressTask complete");
            savedAddressSource.setResult(true);
        });

        // add 6 closest mrt stations
        TaskCompletionSource<Boolean> mrtSource = new TaskCompletionSource<>();
        Task<Boolean> mrtTask = mrtSource.getTask();
        mExecutors.diskRead().execute(() -> {
            List<dbStation> stations = mRoomDb.addressDAO().getAllStations();
//            Log.d(TAG, "stations: " + stations);
            getNearestStationsFrom(location, 6, stations, stationMap -> {
                for (Map.Entry<String, Location> entry : stationMap.entrySet()) {
                    double latitude = entry.getValue().getLatitude();
                    double longitude = entry.getValue().getLongitude();
                    mGeofenceList.add(new Geofence.Builder()
                            .setRequestId(entry.getKey())
                            .setCircularRegion(
                                    latitude,
                                    longitude,
                                    1000
                            )
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL
                                    | Geofence.GEOFENCE_TRANSITION_EXIT)
//                                    | Geofence.GEOFENCE_TRANSITION_ENTER)
                            .setLoiteringDelay(10 * 60 * 1000) // 10 min
                            .build());
                }
                Log.d(TAG, "mrtTask complete");
                mrtSource.setResult(true);
            });
        });

        Tasks.whenAll(savedAddressTask, mrtTask).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Geofence List: " + mGeofenceList.size() + ", " + mGeofenceList.toString());
            onComplete.run();
        });
    }

    public void getNearestStationsFrom(String station, int limit,
                                        List<dbStation> stations,
                                        Consumer<Map<String, Location>> onComplete) {
        mExecutors.diskRead().execute(() -> {
            dbStation fromStation = mRoomDb.addressDAO().getStation(station);
            if (fromStation == null) {
                Log.d(TAG, "Error getting station: " + station);
                return;
            }

            double fromLat = fromStation.latitude;
            double fromLng = fromStation.longitude;

            Location fromLoc = new Location("");
            fromLoc.setLatitude(fromLat);
            fromLoc.setLongitude(fromLng);

            getNearestStationsFrom(fromLoc, limit, stations, onComplete);
        });
    }

    public void getNearestStationsFrom(Location fromLoc, int limit,
                                        List<dbStation> stations,
                                        Consumer<Map<String, Location>> onComplete) {
        Map<String, Map<String, Object>> distanceMap = new HashMap<>();
        for (dbStation station : stations) {
            double latitude = station.latitude;
            double longitude = station.longitude;

            Location toLoc = new Location("");
            toLoc.setLatitude(latitude);
            toLoc.setLongitude(longitude);
            Float distance = fromLoc.distanceTo(toLoc);

            Map<String, Object> value = new HashMap<>();
            value.put("location", toLoc);
            value.put("distance", distance);
            distanceMap.put(station.stationLocale, value);
        }

        LinkedHashMap<String, Map<String, Object>> sortedDistanceMap = distanceMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((o1, o2) -> {
                    Float o1Dist = (Float) o1.get("distance");
                    Float o2Dist = (Float) o2.get("distance");
                    if (o1Dist != null && o2Dist != null) {
                        return (int) (o1Dist - o2Dist);
                    } else {
                        return 0;
                    }
                }))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e2, LinkedHashMap::new));

        List<String> sortedStations = new ArrayList<>(sortedDistanceMap.keySet());
        LinkedHashMap<String, Location> sortedStationMap = new LinkedHashMap<>();
        int cutoff = Math.min(limit, sortedStations.size());
        for (int i = 0; i < cutoff; i++) {
            String stationName = sortedStations.get(i);
            Map<String, Object> value = sortedDistanceMap.get(stationName);
            if (value != null) {
                Location location = (Location) value.get("location");
                sortedStationMap.put(stationName, location);
            }
        }

        onComplete.accept(sortedStationMap);
    }

    private void getNearestSavedAddressesFrom(String station, int limit,
                                              List<dbStation> stations,
                                              Consumer<Map<String, Location>> onComplete) {
        mExecutors.diskRead().execute(() -> {
            dbStation fromStation = mRoomDb.addressDAO().getStation(station);
            if (fromStation == null) {
                Log.d(TAG, "Error getting station: " + station);
                return;
            }

            double fromLat = fromStation.latitude;
            double fromLng = fromStation.longitude;

            Location fromLoc = new Location("");
            fromLoc.setLatitude(fromLat);
            fromLoc.setLongitude(fromLng);

            getNearestSavedAddressesFrom(fromLoc, limit, onComplete);
        });
    }

    private void getNearestSavedAddressesFrom(Location fromLoc, int limit,
                                              Consumer<Map<String, Location>> onComplete) {
        mExecutors.diskRead().execute(() -> {
            List<dbAddress> addresses = mRoomDb.addressDAO().getAllAddresses();
            if (addresses.size() < 1) {
                onComplete.accept(new HashMap<>());
                return;
            }

            Map<String, Map<String, Object>> distanceMap = new HashMap<>();
            for (dbAddress address : addresses) {
                if (address.latitude != null && address.longitude != null) {
                    Location toLoc = new Location("");
                    toLoc.setLatitude(address.latitude);
                    toLoc.setLongitude(address.longitude);
                    Float distance = fromLoc.distanceTo(toLoc);

                    Map<String, Object> currentValue = distanceMap.get(address.articleId);
                    Float currentDistance = currentValue != null ? (Float) currentValue.get("distance") : null;
                    Map<String, Object> value = new HashMap<>();
                    value.put("location", toLoc);
                    value.put("distance", distance);
                    if (currentDistance != null) {
                        if (currentDistance > distance) distanceMap.put(address.articleId, value);
                    } else {
                        distanceMap.put(address.articleId, value);
                    }
                } else {
                    Log.d(TAG, "address " + address.objectID + " has no location: " + address.articleId);
                }
            }

            LinkedHashMap<String, Map<String, Object>> sortedDistanceMap = distanceMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue((o1, o2) -> {
                        Float o1Dist = (Float) o1.get("distance");
                        Float o2Dist = (Float) o2.get("distance");
                        if (o1Dist != null && o2Dist != null) {
                            return (int) (o1Dist - o2Dist);
                        } else {
                            return 0;
                        }
                    }))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (e1, e2) -> e2, LinkedHashMap::new));

            List<String> sortedAddresses = new ArrayList<>(sortedDistanceMap.keySet());
            LinkedHashMap<String, Location> sortedAddressMap = new LinkedHashMap<>();
            int cutoff = Math.min(limit, sortedAddresses.size());
            for (int i = 0; i < cutoff; i++) {
                String articleId = sortedAddresses.get(i);
                Map<String, Object> value = sortedDistanceMap.get(articleId);
                if (value != null) {
                    Location location = (Location) value.get("location");
                    sortedAddressMap.put(articleId, location);
                }
            }

            onComplete.accept(sortedAddressMap);
        });
    }

    public boolean getGeofencesAdded(Context context) {
        return mSharedPreferences.getBoolean(
                context.getString(R.string.pref_key_notif_location), false);
    }

    private void updateGeofencesAdded(Context context, boolean added) {
        long now = (new Date()).getTime();
        mSharedPreferences.edit()
                .putBoolean(context.getString(R.string.pref_key_notif_location), added)
                .putLong("lastUpdatedGeofences", now)
                .apply();
    }

    public void performPendingGeofenceTask(Context context, Object locationReference) {
        performPendingGeofenceTask(context, locationReference, null);
    }

    public void performPendingGeofenceTask(Context context, Runnable onComplete) {
        performPendingGeofenceTask(context,null, onComplete);
    }

    public void performPendingGeofenceTask(Context context, @Nullable Object locationReference, @Nullable Runnable onComplete) {
        if (mPendingGeofenceTask == PendingGeofenceTask.ADD) {
            mGeofenceList.clear();
            if (locationReference instanceof String) {
                populateGeofenceList((String) locationReference, () -> {
                    if (mSharedPreferences.getBoolean(
                            context.getString(R.string.pref_key_notif_location), false)
                            && mGeofenceList.size() > 0)
                        addGeofences(context);
                });
            } else if (locationReference instanceof Location) {
                populateGeofenceList((Location) locationReference, () -> {
                    if (mSharedPreferences.getBoolean(
                            context.getString(R.string.pref_key_notif_location), false)
                            && mGeofenceList.size() > 0)
                        addGeofences(context);
                });
            }
        } else if (mPendingGeofenceTask == PendingGeofenceTask.REMOVE) {
            removeGeofences(context, onComplete);
        }
    }
}
