package acorn.com.acorn_app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import acorn.com.acorn_app.data.NetworkDataSource;

import static acorn.com.acorn_app.ui.AcornApplication.mFirebaseAnalytics;
import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class Logger {
    private static final String TAG = "Logger";

    private Context mContext;
    private final AppExecutors mExecutors = AppExecutors.getInstance();
    private NetworkDataSource mDataSource;

    public Logger(Context context) {
        mContext = context;
        mDataSource = NetworkDataSource.getInstance(mContext, mExecutors);
    }

    public void logNotificationClicked(boolean fromNotif, String notifType,
                                       String userId, @Nullable String itemId) {
        if (fromNotif) {
            Bundle bundle = new Bundle();
            if (itemId != null) bundle.putString(FirebaseAnalytics.Param.ITEM_ID, itemId);
            bundle.putString("notification_type", notifType);
            mFirebaseAnalytics.logEvent("click_notification", bundle);
            mDataSource.logNotificationClicked(userId, itemId, notifType);
        }
    }

    public void logNotificationError(boolean fromNotif, String notifType,
                                       String userId, @Nullable String itemId) {
        if (fromNotif) {
            Bundle bundle = new Bundle();
            if (itemId != null) bundle.putString(FirebaseAnalytics.Param.ITEM_ID, itemId);
            bundle.putString("notification_type", notifType);
            mFirebaseAnalytics.logEvent("click_notification", bundle);
            mDataSource.logNotificationError(userId, itemId, notifType);
        }
    }
}
