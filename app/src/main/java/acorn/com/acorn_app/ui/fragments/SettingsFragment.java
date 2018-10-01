package acorn.com.acorn_app.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;

import java.util.ArrayList;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.ui.activities.SettingsActivity;
import acorn.com.acorn_app.ui.activities.ThemeSelectionActivity;

import static acorn.com.acorn_app.ui.activities.AcornActivity.RC_THEME_PREF;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUserThemePrefs;
import static android.app.Activity.RESULT_OK;

public class SettingsFragment extends PreferenceFragment {
    private static final String TAG = "SettingsFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        setHasOptionsMenu(true);

        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_key_night_mode)));
        bindSwitchPreferenceSummaryToValue(findPreference(getString(R.string.pref_key_notif_comment)));
        bindSwitchPreferenceSummaryToValue(findPreference(getString(R.string.pref_key_notif_article)));

        // Set up night mode
        ListPreference dayNightPref = (ListPreference) findPreference(getString(R.string.pref_key_night_mode));
        dayNightPref.setOnPreferenceChangeListener(((preference, newValue) -> {

            getActivity().recreate();
            return true;
        }));

        // Set up theme editor
        Preference editThemes = (Preference) findPreference(getString(R.string.pref_key_edit_themes));
        editThemes.setOnPreferenceClickListener(preference -> {
            Intent editThemeIntent = new Intent(getActivity(), ThemeSelectionActivity.class);
            editThemeIntent.putStringArrayListExtra("themePrefs", mUserThemePrefs);
            startActivityForResult(editThemeIntent, RC_THEME_PREF);
            return true;
        });

        // Set up comments notification pref
        Preference commentNotif = (Preference) findPreference(getString(R.string.pref_key_notif_comment));
        commentNotif.setOnPreferenceChangeListener(((preference, newValue) -> {

            return true;
        }));

        // Set up articles notification pref
        Preference articleNotif = (Preference) findPreference(getString(R.string.pref_key_notif_article));
        articleNotif.setOnPreferenceChangeListener(((preference, newValue) -> {

            return true;
        }));
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

    private final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
        String stringValue = value.toString();

        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(
                    index >= 0
                            ? listPreference.getEntries()[index]
                            : null);
        } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }
        return true;
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    private void bindSwitchPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        Boolean value = PreferenceManager
                .getDefaultSharedPreferences(preference.getContext())
                .getBoolean(preference.getKey(), true);
        String stringValue;
        if (value) {
            stringValue = "Enabled";
        } else {
            stringValue = "Disabled";
        }
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, stringValue);
    }
}
