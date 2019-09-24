package acorn.com.acorn_app.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import acorn.com.acorn_app.data.ArticleRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.dbArticle;
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

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mDataSource = NetworkDataSource.getInstance(getApplicationContext(), mExecutors);
        mRoomDb = ArticleRoomDatabase.getInstance(getApplicationContext());

        Handler handler = new Handler();

        long lastDownloadArticlesTime = mSharedPreferences.getLong("lastDownloadArticlesTime", 0L);
        long now = (new Date()).getTime();
//        lastDownloadArticlesTime = 0L;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            if (isConnected && (lastDownloadArticlesTime == 0L || now > lastDownloadArticlesTime + 60L * 60L * 1000L)) { // 1 hour
                mSharedPreferences.edit().putLong("lastDownloadArticlesTime", now).apply();

                List<dbArticle> articleList = new ArrayList<>();
                Runnable insertArticlesIntoLocalDb = () -> {
                    Long cutOffDate = (new Date()).getTime() - 2L * 24L * 60L * 60L * 1000L; // more than 2 days ago
                    mExecutors.diskWrite().execute(() -> {
                        mRoomDb.articleDAO().insert(articleList);
                        Log.d(TAG, "inserted: " + articleList.size() + " articles");
                        mRoomDb.articleDAO().deleteOld(cutOffDate);
                    });
                };

                mExecutors.networkIO().execute(() -> {
                    mDataSource.downloadSubscribedArticles(500, (articles) -> {
                        articleList.addAll(articles);
                        handler.removeCallbacks(insertArticlesIntoLocalDb);
                        handler.postDelayed(insertArticlesIntoLocalDb, 200);
                    }, () -> {
                    });
                });
            }
        }
        handler.postDelayed(() -> {
            startActivity(new Intent(this, AcornActivity.class));
            finish();
        }, 1000);
    }
}
