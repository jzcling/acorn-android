package acorn.com.acorn_app.ui.fragments;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.ui.activities.SettingsActivity;
import acorn.com.acorn_app.ui.activities.ThemeSelectionActivity;

import static acorn.com.acorn_app.ui.activities.AcornActivity.RC_THEME_PREF;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserThemePrefs;
import static acorn.com.acorn_app.ui.activities.SettingsActivity.mLocationPermissionsUtils;
import static androidx.appcompat.app.AppCompatActivity.RESULT_OK;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "SettingsFragment";

    private SharedPreferences mSharedPreferences;
    public static Preference locationPreference;

    private List<String> channelsToAdd = new ArrayList<>();

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
        onSharedPreferenceChanged(mSharedPreferences, getString(R.string.pref_key_feed_videos));

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

        // Set up video channels handler
        Preference channelsRemovedPref = (Preference) findPreference(getString(R.string.pref_key_feed_videos_channels));
        channelsRemovedPref.setOnPreferenceClickListener(preference -> {
            Set<String> channelsRemoved = mSharedPreferences.getStringSet(
                    getString(R.string.pref_key_feed_videos_channels), new ArraySet<>());

            String[] channels = new String[channelsRemoved.size()];
            channelsRemoved.toArray(channels);
            boolean[] checkedStatus = new boolean[channelsRemoved.size()];
            for (int i = 0; i < channelsRemoved.size(); i++) {
                checkedStatus[i] = true;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
            builder.setTitle("Channels Removed")
                    .setMultiChoiceItems(channels, checkedStatus, ((dialog, which, isChecked) -> {
                        checkedStatus[which] = isChecked;
                    }));

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            builder.setPositiveButton("Done", ((dialog, which) -> {
                channelsRemoved.clear();
                for (int i = 0; i < channels.length; i++) {
                    if (checkedStatus[i]) {
                        channelsRemoved.add(channels[i]);
                    } else {
                        channelsToAdd.add(channels[i]);
                    }
                }
                Log.d(TAG, "channelsRemoved: " + channelsRemoved + ", channelsToAdd: " + channelsToAdd);
                mSharedPreferences.edit().putStringSet(
                        getString(R.string.pref_key_feed_videos_channels), channelsRemoved
                ).apply();

                passData(channelsToAdd);
            }));

            AlertDialog dialog = builder.create();
            dialog.show();

            return true;
        });
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
            setSummary(preference, mSharedPreferences.getBoolean(key, true));
        } else if (key.equals(getString(R.string.pref_key_notif_location))) {
            Preference preference = findPreference(key);
            boolean value = mSharedPreferences.getBoolean(key, false);
            setSummary(preference, value);
            if (value) {
                mLocationPermissionsUtils.requestLocationPermissions(() -> {});
            }
        } else if (key.equals(getString(R.string.pref_key_feed_videos))) {
            Preference preference = findPreference(key);
            setSummary(preference, mSharedPreferences.getBoolean(key, true));
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

    public interface OnDataPass {
        public void OnDataPass(List<String> data);
    }

    OnDataPass dataPasser;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        dataPasser = (OnDataPass) context;
    }

    public void passData(List<String> data) {
        dataPasser.OnDataPass(data);
    }
}
