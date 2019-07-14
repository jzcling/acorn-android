package acorn.com.acorn_app.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import android.util.Log;
import android.view.MenuItem;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.ui.fragments.SettingsFragment;
import acorn.com.acorn_app.utils.LocationPermissionsUtils;

import static acorn.com.acorn_app.ui.fragments.SettingsFragment.locationPreference;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private int dayNightValue;

    public static LocationPermissionsUtils mLocationPermissionsUtils;
    private SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        dayNightValue = Integer.parseInt(prefs.getString(getString(R.string.pref_key_night_mode), "0"));

        mLocationPermissionsUtils = new LocationPermissionsUtils(this);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        AppCompatDelegate.setDefaultNightMode(dayNightValue);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings, new SettingsFragment()).commit();
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || SettingsFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void finish() {
        Intent result = new Intent();
        result.putExtra("dayNightValue", dayNightValue);
        setResult(RESULT_OK, result);
        super.finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch(requestCode) {
            case LocationPermissionsUtils.LOCATION_PERMISSIONS_RC:
                for (String perm : mLocationPermissionsUtils.permissionsToRequest) {
                    if (!mLocationPermissionsUtils.hasPermission(perm)) {
                        mLocationPermissionsUtils.permissionsRejected.add(perm);
                    }
                }

                if (mLocationPermissionsUtils.permissionsRejected.size() > 0) {
                    Log.d(TAG, "permissions rejected");
                    if (shouldShowRequestPermissionRationale(mLocationPermissionsUtils.permissionsRejected.get(0))) {
                        Log.d(TAG, "show rationale");
                        new AlertDialog.Builder(this)
                                .setMessage("Please enable locations permissions to receive recommendations near you.")
                                .setPositiveButton("OK", (dialogInterface, i) ->
                                        requestPermissions(mLocationPermissionsUtils.permissionsRejected
                                                        .toArray(new String[mLocationPermissionsUtils.permissionsRejected.size()]),
                                                LocationPermissionsUtils.LOCATION_PERMISSIONS_RC))
                                .setNegativeButton("Cancel", (dialog, which) -> {
                                    mSharedPreferences.edit()
                                            .putBoolean(getString(R.string.pref_key_notif_location), false)
                                            .apply();
                                    if (locationPreference != null) {
                                        locationPreference.performClick();
                                    }
                                }).create().show();
                        return;
                    }
                }

                break;
        }
    }
}
