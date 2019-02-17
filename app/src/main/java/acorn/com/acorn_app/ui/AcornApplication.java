package acorn.com.acorn_app.ui;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.database.FirebaseDatabase;

import acorn.com.acorn_app.R;

public class AcornApplication extends Application {
    @Override
    public void onCreate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int nightModePref = Integer.parseInt(
                prefs.getString(getString(R.string.pref_key_night_mode), "1"));
        AppCompatDelegate.setDefaultNightMode(nightModePref);
        super.onCreate();
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
