package acorn.com.acorn_app.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;

import java.util.ArrayList;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.services.GeofenceBroadcastReceiver;
import acorn.com.acorn_app.services.GeofenceTransitionsJobIntentService;

public class GeofenceUtils {
    private static final String TAG = "GeofenceUtils";
    private static final Object LOCK = new Object();

    private static GeofenceUtils sInstance;
    private Context mContext;
    private OnCompleteListener onCompleteListener;
    private NetworkDataSource mDataSource;

    private SharedPreferences mSharedPreferences;
    public boolean mPreChangeValue;

    private GeofencingClient mGeofencingClient;
    public enum PendingGeofenceTask { ADD, REMOVE, NONE }
    private ArrayList<Geofence> mGeofenceList = new ArrayList<>();
    private PendingIntent mGeofencePendingIntent = null;
    public PendingGeofenceTask mPendingGeofenceTask = PendingGeofenceTask.NONE;

    private GeofenceUtils(Context context, NetworkDataSource dataSource, OnCompleteListener onCompleteListener) {
        this.mContext = context;
        this.onCompleteListener = onCompleteListener;
        this.mDataSource = dataSource;
        mGeofencingClient = LocationServices.getGeofencingClient(mContext);
        mSharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public static GeofenceUtils getInstance(Context context, NetworkDataSource dataSource,
                                            OnCompleteListener onCompleteListener) {
        if (sInstance == null) {
            synchronized (LOCK) {
                sInstance = new GeofenceUtils(context, dataSource, onCompleteListener);
            }
        }
        return sInstance;
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
    private void addGeofences() {
        mGeofencingClient.addGeofences(getGeofencingRequest(), getGeofencePendingIntent())
                .addOnCompleteListener(onCompleteListener);
    }

    @SuppressWarnings("MissingPermission")
    private void removeGeofences() {
        mGeofencingClient.removeGeofences(getGeofencePendingIntent()).addOnCompleteListener(onCompleteListener);
    }

    private PendingIntent getGeofencePendingIntent() {
        Log.d(TAG, "getGeofencePendingIntent");
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            Log.d(TAG, "geofence pending intent reused");
            return mGeofencePendingIntent;
        }
        Log.d(TAG, "new geofence pending intent");
        Intent intent = new Intent(mContext, GeofenceBroadcastReceiver.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        mGeofencePendingIntent = PendingIntent.getBroadcast(mContext.getApplicationContext(),
                GeofenceTransitionsJobIntentService.PENDINGINTENT_RC, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }

    public void populateGeofenceList(Runnable onComplete) {
        // get mrt stations
        mDataSource.getMrtStations((mrtStations) -> {
            for (Map.Entry<String, Map<String, Object>> entry : mrtStations.entrySet()) {
                Double latitude = (Double) entry.getValue().get("latitude");
                Double longitude = (Double) entry.getValue().get("longitude");
                boolean geofence = (boolean) entry.getValue().get("geofence");
                if (latitude != null && longitude != null && geofence) {
                    mGeofenceList.add(new Geofence.Builder()
                            .setRequestId(entry.getKey())
                            .setCircularRegion(
                                    latitude,
                                    longitude,
                                    1000
                            )
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                            .setLoiteringDelay(10 * 60 * 1000) // 10 min
                            .build());
                }
            }

            mGeofenceList.add(new Geofence.Builder()
                .setRequestId("One-north")
                .setCircularRegion(
                        1.304136,
                        103.901394,
                        1000
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(10 * 60 * 1000)
                .build());

            Log.d(TAG, "Geofence List: " + mGeofenceList.size() + ", " + mGeofenceList.toString());
            onComplete.run();
        }, null);
    }

    public boolean getGeofencesAdded() {
        return mSharedPreferences.getBoolean(
                mContext.getString(R.string.pref_key_notif_location), false);
    }

    public void updateGeofencesAdded(boolean added) {
        mSharedPreferences.edit()
                .putBoolean(mContext.getString(R.string.pref_key_notif_location), added)
                .apply();
    }

    public void performPendingGeofenceTask() {
        if (mPendingGeofenceTask == PendingGeofenceTask.ADD) {
            populateGeofenceList(() -> {
                if (mSharedPreferences.getBoolean(
                        mContext.getString(R.string.pref_key_notif_location), false))
                    addGeofences();
            });
        } else if (mPendingGeofenceTask == PendingGeofenceTask.REMOVE) {
            removeGeofences();
        }
    }
}
