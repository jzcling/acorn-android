package acorn.com.acorn_app.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import java.util.Date;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.ui.activities.AcornActivity;
import acorn.com.acorn_app.ui.activities.WebViewActivity;
import acorn.com.acorn_app.ui.viewModels.NotificationViewModel;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.IOUtils;

import static android.support.v4.app.NotificationCompat.CATEGORY_RECOMMENDATION;
import static android.support.v4.app.NotificationCompat.DEFAULT_SOUND;
import static android.support.v4.app.NotificationCompat.DEFAULT_VIBRATE;
import static android.support.v4.app.NotificationCompat.GROUP_ALERT_SUMMARY;
import static android.support.v4.app.NotificationCompat.PRIORITY_HIGH;
import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;

public class RecArticlesJobService extends JobService {
    private static final String TAG = "RecArticlesService";
    private AppExecutors mExecutors;
    private NetworkDataSource mDataSource;
    private SharedPreferences sharedPrefs;
    private String themeSearchKey;
            
    @Override
    public boolean onStartJob(JobParameters params) {
        mExecutors = AppExecutors.getInstance();
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        Log.d(TAG, "themeSearchKey: " + sharedPrefs.getString("themeSearchKey",""));
        //Log.d(TAG, "searchRef: " + SEARCH_REF);
        themeSearchKey = sharedPrefs.getString("themeSearchKey", "");
        final String hitsRef = "search/" + themeSearchKey + "/hits";
        Log.d(TAG, "hitsRef: " + hitsRef);

        Log.d(TAG, "job started: " + params.getTag());
        mExecutors.networkIO().execute(() -> {
            mDataSource.getThemeData(() -> {
                mDataSource.getRecommendedArticles(hitsRef, () -> {
                    mExecutors.networkIO().execute(() -> {
                        sendRecArticlesNotification();
                        jobFinished(params,true);
                        Log.d(TAG, "job finished: " + params.getTag());
                        mDataSource.recordLastRecArticlesPushTime();
                    });
                });
            });
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "job stopped" + params.getTag());
        return true;
    }

    private void sendRecArticlesNotification() {
        final String GROUP_NAME = "recommendedArticles";
        final int ARTICLE_NOTIFICATION_ID = 9002;
        final int PENDINGINTENT_RC = 502;
        final String CHANNEL_ID = getString(R.string.article_notification_channel_id);
        final String CHANNEL_NAME = getString(R.string.article_notification_channel_name);
        final List<Article> articleList = NetworkDataSource.mRecArticleList;

        SharedPreferences sharedPrefs = getSharedPreferences(getString(R.string.notif_pref_id), MODE_PRIVATE);

        Intent intent = new Intent(this, AcornActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, PENDINGINTENT_RC, intent,
                PendingIntent.FLAG_ONE_SHOT);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setSummaryText("Based on your subscriptions");
        String keys = sharedPrefs.getString(getString(R.string.notif_pref_key), "");
        String value;
        for (int i = articleList.size() - 1; i >= 0; i--) {
            Article article = articleList.get(i);
            String key = "a_" + article.getObjectID();

            //Save in shared prefs
            if (keys.equals("")) {
                keys = key;
            } else {
                if (!keys.contains(key)) keys += "·" + key;
            }

            String imageUrl = null;
            if (article.getImageUrl() != null && !article.getImageUrl().equals("")) {
                imageUrl = article.getImageUrl();
            } else if (article.getPostImageUrl() != null && !article.getPostImageUrl().equals("")) {
                imageUrl = article.getPostImageUrl();
            }
            String source = null;
            String title = null;
            String contentText;
            if (article.getSource() != null && !article.getSource().equals("")) {
                source = article.getSource();
                title = article.getTitle();
            } else if (article.getPostAuthor() != null && !article.getPostAuthor().equals("")) {
                source = article.getPostAuthor();
                title = article.getPostText();
            }

            // type, articleId, text, title, source, imageUrl, theme, extra, timestamp
            value = "article" + "·" + // type
                    article.getObjectID() + "·" + // articleId
                    "Recommended based on your subscription to " + article.getMainTheme() + "·" + // text
                    title + "·" + // title
                    source + "·" + // source
                    imageUrl + "·" + // imageUrl
                    article.getMainTheme() + "·" + // theme
                    String.valueOf(article.getPubDate()) + "·" + // extra
                    String.valueOf((new Date()).getTime()); // timestamp
            sharedPrefs.edit().putString(key, value).apply();
            Log.d(TAG, "value_key: " + key + ", value: " + value);

            //Create notification
            inboxStyle.addLine(title);

            Intent individualIntent = new Intent(this, WebViewActivity.class);
            individualIntent.putExtra("id", article.getObjectID());
            individualIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent individualPendingIntent = TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(individualIntent)
                    .getPendingIntent(i, PendingIntent.FLAG_UPDATE_CURRENT);

            Bitmap bitmap = IOUtils.getBitmapFromUrl(imageUrl);
            contentText = source != null ? source + " · " + article.getMainTheme() : article.getMainTheme();

            NotificationCompat.Builder notificationBuilder =
                    new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setLargeIcon(bitmap)
                            .setContentTitle(article.getTitle())
                            .setContentText(contentText)
                            .setContentIntent(individualPendingIntent)
                            .setAutoCancel(true)
                            .setGroup(GROUP_NAME)
                            .setGroupAlertBehavior(GROUP_ALERT_SUMMARY);
            notificationManager.notify(i, notificationBuilder.build());
        }

        NotificationCompat.Builder summaryNotificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setOnlyAlertOnce(true)
                        .setGroup(GROUP_NAME)
                        .setGroupSummary(true)
                        .setDefaults(DEFAULT_SOUND|DEFAULT_VIBRATE)
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setCategory(CATEGORY_RECOMMENDATION)
                        .setVisibility(VISIBILITY_PUBLIC)
                        .setStyle(inboxStyle)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setAutoCancel(true)
        //                .setContentIntent(pendingIntent)
                        .setNumber(articleList.size());

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.canShowBadge();
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        sharedPrefs.edit().putString(getString(R.string.notif_pref_key), keys).apply();
        Log.d(TAG, "keys_key: " + getString(R.string.notif_pref_key) + ", keys: " + keys);
        NotificationViewModel.sharedPrefs.postValue(sharedPrefs);

        notificationManager.notify(ARTICLE_NOTIFICATION_ID, summaryNotificationBuilder.build());
    }
}
