package acorn.com.acorn_app.utils;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;

public final class GeofenceConstants {

    public GeofenceConstants() { }

    private static final String PACKAGE_NAME = "com.google.android.gms.location.Geofence";
    public static final String GEOFENCES_ADDED_KEY = PACKAGE_NAME + ".GEOFENCES_ADDED_KEY";

    private static final long GEOFENCE_EXPIRATION_IN_HOURS = 12;
    public static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS =
            GEOFENCE_EXPIRATION_IN_HOURS * 60 * 60 * 1000;
    public static final float GEOFENCE_RADIUS_IN_METERS = 1000;

    public static final HashMap<String, LatLng> TEST_LANDMARKS = new HashMap<>();
    static {
        TEST_LANDMARKS.put("One-north", new LatLng(1.301044,103.7878353));
        TEST_LANDMARKS.put("BARKER", new LatLng(1.319989, 103.834940));
    }
}