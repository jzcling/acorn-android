package acorn.com.acorn_app.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Date;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.ArticleRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.utils.AppExecutors;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private SharedPreferences mSharedPreferences;
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();
    private ArticleRoomDatabase mRoomDb;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);
        mRoomDb = ArticleRoomDatabase.getInstance(this);

        Long lastDownloadArticlesTime = mSharedPreferences.getLong("lastDownloadArticlesTime", 0L);
        Long now = (new Date()).getTime();
//        lastDownloadArticlesTime = 0L;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        if (isConnected) {
            if (lastDownloadArticlesTime == 0L || now > lastDownloadArticlesTime + 60L * 60L * 1000L) { // 1 hour
                mSharedPreferences.edit().putLong("lastDownloadArticlesTime", now).apply();

                mExecutors.networkIO().execute(
                        () -> mDataSource.downloadSubscribedArticles(500, (articleList) -> {
                            Long cutOffDate = (new Date()).getTime() - 2L * 24L * 60L * 60L * 1000L; // more than 2 days ago
                            mExecutors.diskWrite().execute(() -> {
                                mRoomDb.articleDAO().insert(articleList);
                                Log.d(TAG, "inserted: " + articleList.size() + " articles");
                                mRoomDb.articleDAO().deleteOld(cutOffDate);
                            });
                        }, () -> { })
                );
            }
        }

        startActivity(new Intent(this, AcornActivity.class));
        finish();
    }
}
