package acorn.com.acorn_app.ui.fragments;


import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.ui.activities.AcornActivity;
import acorn.com.acorn_app.ui.activities.SettingsActivity;
import acorn.com.acorn_app.ui.activities.ThemeSelectionActivity;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.GeofenceConstants;
import acorn.com.acorn_app.utils.GeofenceErrorMessages;
import acorn.com.acorn_app.utils.GeofenceUtils;
import acorn.com.acorn_app.utils.LocationPermissionsUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.RC_THEME_PREF;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mSharedPreferences;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserThemePrefs;
import static acorn.com.acorn_app.ui.activities.SettingsActivity.mLocationPermissionsUtils;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static androidx.appcompat.app.AppCompatActivity.RESULT_OK;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "SettingsFragment";

    private SharedPreferences mSharedPreferences;
    public static Preference locationPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setHasOptionsMenu(true);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

//        onSharedPreferenceChanged(mSharedPreferences, getString(R.string.pref_key_night_mode));
        onSharedPreferenceChanged(mSharedPreferences, getString(R.string.pref_key_notif_comment));
        onSharedPreferenceChanged(mSharedPreferences, getString(R.string.pref_key_notif_article));
        onSharedPreferenceChanged(mSharedPreferences, getString(R.string.pref_key_notif_deals));
        onSharedPreferenceChanged(mSharedPreferences, getString(R.string.pref_key_notif_saved_articles_reminder));
        onSharedPreferenceChanged(mSharedPreferences, getString(R.string.pref_key_notif_location));

        // Set up theme editor
        Preference editThemes = (Preference) findPreference(getString(R.string.pref_key_edit_themes));
        editThemes.setOnPreferenceClickListener(preference -> {
            Intent editThemeIntent = new Intent(getActivity(), ThemeSelectionActivity.class);
            editThemeIntent.putStringArrayListExtra("themePrefs", mUserThemePrefs);
            startActivityForResult(editThemeIntent, RC_THEME_PREF);
            return true;
        });

        // Set reference for locationPreference
        locationPreference = (Preference) findPreference(getString(R.string.pref_key_notif_location));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_THEME_PREF) {
            if (resultCode == RESULT_OK) {
                mUserThemePrefs = new ArrayList<>();
                mUserThemePrefs.addAll(data.getStringArrayListExtra("themePrefs"));
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_key_night_mode))) {
            getActivity().recreate();
        } else if (key.equals(getString(R.string.pref_key_notif_comment)) ||
                key.equals(getString(R.string.pref_key_notif_article)) ||
                key.equals(getString(R.string.pref_key_notif_deals)) ||
                key.equals(getString(R.string.pref_key_notif_saved_articles_reminder))) {
            Preference preference = findPreference(key);
            setSummary(preference, mSharedPreferences.getBoolean(key, false));
        } else if (key.equals(getString(R.string.pref_key_notif_location))) {
            Preference preference = findPreference(key);
            boolean value = mSharedPreferences.getBoolean(key, false);
            setSummary(preference, value);
            if (value) {
                mLocationPermissionsUtils.requestLocationPermissions(() -> {});
            }
        }
    }

    private void setSummary(Preference preference, boolean value) {
        if (value) {
            preference.setSummary("Enabled");
        } else {
            preference.setSummary("Disabled");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationPreference = null;
    }
}
