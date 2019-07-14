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

import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;
import acorn.com.acorn_app.utils.IOUtils;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import android.os.Handler;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.ui.activities.AcornActivity;
import acorn.com.acorn_app.ui.activities.CommentActivity;
import acorn.com.acorn_app.ui.activities.WebViewActivity;
import acorn.com.acorn_app.ui.viewModels.NotificationViewModel;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.CommentActivity.mCommentOpenObjectID;
import static androidx.core.app.NotificationCompat.CATEGORY_RECOMMENDATION;
import static androidx.core.app.NotificationCompat.CATEGORY_SOCIAL;
import static androidx.core.app.NotificationCompat.DEFAULT_SOUND;
import static androidx.core.app.NotificationCompat.DEFAULT_VIBRATE;
import static androidx.core.app.NotificationCompat.GROUP_ALERT_SUMMARY;
import static androidx.core.app.NotificationCompat.PRIORITY_HIGH;
import static androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMsgService";

    private final AppExecutors mExecutors = AppExecutors.getInstance();
    private NetworkDataSource mDataSource;
    private SharedPreferences sharedPrefs;

    private static NotificationManager mNotificationManager;
    
    private final int COMMENT_NOTIFICATION_ID = 9001;
    private final String COMMENT_GROUP_NAME = "comments";
    private final int COMMENT_SUMMARY_PENDINGINTENT_RC = 501;
    private NotificationCompat.Builder commentNotificationBuilder;
    private NotificationCompat.Builder commentSummaryNotificationBuilder;
    
    private final int MANUAL_ARTICLE_NOTIFICATION_ID = 9003;
    private final String MANUAL_ARTICLE_GROUP_NAME = "manualArticle";
    private final int MANUAL_ARTICLE_PENDINGINTENT_RC = 503;
    private NotificationCompat.Builder manualArticleNotificationBuilder;

    private final int SAVED_REMINDER_NOTIFICATION_ID = 9004;
    private final String SAVED_REMINDER_GROUP_NAME = "savedArticlesReminder";
    private final int SAVED_REMINDER_PENDINGINTENT_RC = 504;
    private NotificationCompat.Builder savedReminderSummaryNotificationBuilder;
    private NotificationCompat.Builder savedReminderNotificationBuilder;

    private void sendRegistrationToServer(String token) {
        DatabaseReference user = FirebaseDatabase.getInstance()
                .getReference("user/" + mUid + "/token");
        user.setValue(token);
    }

    @Override
    public void onNewToken(String s) {
        super.onNewToken(s);
        sendRegistrationToServer(s);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mDataSource = NetworkDataSource.getInstance(this, mExecutors);
        sharedPrefs = getSharedPreferences(getString(R.string.notif_pref_id), MODE_PRIVATE);

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String COMMENT_CHANNEL_ID = getString(R.string.comment_notification_channel_id);
        String COMMENT_CHANNEL_NAME = getString(R.string.comment_notification_channel_name);
        commentSummaryNotificationBuilder =
                new NotificationCompat.Builder(this, COMMENT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(getColor(R.color.colorPrimary))
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setGroup(COMMENT_GROUP_NAME)
                        .setGroupSummary(true)
                        .setDefaults(DEFAULT_SOUND|DEFAULT_VIBRATE)
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setCategory(CATEGORY_SOCIAL)
                        .setVisibility(VISIBILITY_PUBLIC);

        commentNotificationBuilder =
                new NotificationCompat.Builder(this, COMMENT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(getColor(R.color.colorPrimary))
                        .setAutoCancel(true)
                        .setGroup(COMMENT_GROUP_NAME)
                        .setGroupAlertBehavior(GROUP_ALERT_SUMMARY);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(COMMENT_CHANNEL_ID,
                    COMMENT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.enableVibration(true);
            mNotificationManager.createNotificationChannel(channel);
        }

        String MANUAL_ARTICLE_CHANNEL_ID = getString(R.string.manual_article_notification_channel_id);
        String MANUAL_ARTICLE_CHANNEL_NAME = getString(R.string.manual_article_notification_channel_name);

        manualArticleNotificationBuilder =
                new NotificationCompat.Builder(this, MANUAL_ARTICLE_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(getColor(R.color.colorPrimary))
                        .setAutoCancel(true)
                        .setGroup(MANUAL_ARTICLE_GROUP_NAME)
                        .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                        .setDefaults(DEFAULT_SOUND|DEFAULT_VIBRATE)
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setVisibility(VISIBILITY_PUBLIC);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(MANUAL_ARTICLE_CHANNEL_ID,
                    MANUAL_ARTICLE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.enableVibration(true);
            mNotificationManager.createNotificationChannel(channel);
        }

        String SAVED_REMINDER_CHANNEL_ID = getString(R.string.saved_reminder_notification_channel_id);
        String SAVED_REMINDER_CHANNEL_NAME = getString(R.string.saved_reminder_notification_channel_name);

        savedReminderSummaryNotificationBuilder =
                new NotificationCompat.Builder(this, SAVED_REMINDER_CHANNEL_ID)
                        .setOnlyAlertOnce(true)
                        .setGroup(SAVED_REMINDER_GROUP_NAME)
                        .setGroupSummary(true)
                        .setDefaults(DEFAULT_SOUND|DEFAULT_VIBRATE)
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setCategory(CATEGORY_RECOMMENDATION)
                        .setVisibility(VISIBILITY_PUBLIC)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(getColor(R.color.colorPrimary))
                        .setAutoCancel(true);

        savedReminderNotificationBuilder =
                new NotificationCompat.Builder(this, SAVED_REMINDER_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(getColor(R.color.colorPrimary))
                        .setAutoCancel(true)
                        .setGroup(SAVED_REMINDER_GROUP_NAME)
                        .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                        .setDefaults(DEFAULT_SOUND|DEFAULT_VIBRATE)
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setVisibility(VISIBILITY_PUBLIC);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(SAVED_REMINDER_CHANNEL_ID,
                    SAVED_REMINDER_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.enableVibration(true);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "onMessageReceived");
        Map<String, String> data = remoteMessage.getData();
        switch (data.get("type")) {
            case "comment":
                sendCommentNotification(data);
                break;
            case "manualArticle":
                pushManualArticleNotification(data);
                break;
            case "savedArticlesReminder":
                try {
                    Thread.sleep((long) (new Random().nextDouble()) * 10L * 1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                pushSavedReminderNotification();
                break;
        }
    }

    private void sendCommentNotification(Map<String, String> data) {
        if (mCommentOpenObjectID != null && mCommentOpenObjectID.equals(data.get("articleId")))
            return;

        Intent intent = new Intent(this, AcornActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, COMMENT_SUMMARY_PENDINGINTENT_RC, intent,
                PendingIntent.FLAG_ONE_SHOT);

        updateSharedPrefsForComments(sharedPrefs, data);

        String commentsText = sharedPrefs.getString(getString(R.string.notif_pref_c_text), "");
        if (commentsText.equals("")) {
            commentsText = data.get("commentText");
        } else {
            commentsText += "~·~" + data.get("commentText");
        }

        List<String> comments = Arrays.asList(commentsText.split("~·~"));
        String commentCount;
        String contentTitle;
        String contentText = data.get("commenter") + ": " + data.get("commentText");
        if (comments.size() == 1) {
            commentCount = "1 comment";
        } else {
            commentCount = String.valueOf(comments.size()) + " comments";
        }


        if (data.get("title").length() > 24) {
            contentTitle = data.get("title").substring(0,23) + "...";
        } else {
            contentTitle = data.get("title");
        }

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        int pendingIntentRC = 20;
        for (int i = 0; i < comments.size(); i++) {
            inboxStyle.addLine(comments.get(i));
            pendingIntentRC++;
        }
        inboxStyle.setSummaryText(commentCount);

        commentSummaryNotificationBuilder
                .setStyle(inboxStyle)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setNumber(comments.size());

        Intent individualIntent = new Intent(this, CommentActivity.class);
        individualIntent.putExtra("id", data.get("articleId"));
        individualIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent individualPendingIntent = TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(individualIntent)
                .getPendingIntent(pendingIntentRC, PendingIntent.FLAG_UPDATE_CURRENT);

        commentNotificationBuilder
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(individualPendingIntent);

        mNotificationManager.notify(100+comments.size(), commentNotificationBuilder.build());
        mNotificationManager.notify(COMMENT_NOTIFICATION_ID, commentSummaryNotificationBuilder.build());
    }

    private void updateSharedPrefsForComments(SharedPreferences sharedPrefs, Map<String, String> data) {
        String keys = sharedPrefs.getString(getString(R.string.notif_pref_key), "");

        List<String> keyList = Arrays.asList(keys.split("~·~"));
        String key = "c_" + data.get("articleId");
        if (keyList.contains(key)) {
            String value = sharedPrefs.getString(key, "");
            List<String> valueList = Arrays.asList(value.split("~·~"));
            int count = Integer.parseInt(Arrays.asList(valueList.get(2).split(" ")).get(0)) + 1;
            // type, articleId, text, title, source, imageUrl, theme, extra, timestamp
            value = "comment" + "~·~" + // type
                    data.get("articleId") + "~·~" + // articleId
                    count + " new comments on an article you follow" + "~·~" + // text
                    data.get("title") + "~·~" + // title
                    data.get("source") + "~·~" + // source
                    data.get("imageUrl") + "~·~" + // imageUrl
                    data.get("mainTheme") + "~·~" + // theme
                    data.get("mainTheme") + "~·~" + // extra
                    data.get("timestamp") + "~·~" + // timestamp
                    ""; // link

            sharedPrefs.edit().putString(key, value).apply();

        } else {
            if (keys.equals("")) {
                keys = key;
            } else {
                keys += "~·~" + key;
            }
            // type, articleId, text, title, source, imageUrl, theme, extra, timestamp, link
            String value = "comment" + "~·~" + // type
                    data.get("articleId") + "~·~" + // articleId
                    "1 new comment on an article you follow" + "~·~" + // text
                    data.get("title") + "~·~" + // title
                    data.get("source") + "~·~" + // source
                    data.get("imageUrl") + "~·~" + // imageUrl
                    data.get("mainTheme") + "~·~" + // theme
                    data.get("mainTheme") + "~·~" + // extra
                    data.get("timestamp") + "~·~" + // timestamp
                    ""; // link

            sharedPrefs.edit().putString(getString(R.string.notif_pref_key), keys)
                    .putString(key, value).apply();


        }
        NotificationViewModel.sharedPrefs.postValue(sharedPrefs);
    }

    private void pushManualArticleNotification(Map<String, String> data) {

        Intent individualIntent = new Intent(this, WebViewActivity.class);
        individualIntent.putExtra("id", data.get("articleId"));
        individualIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent individualPendingIntent = TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(individualIntent)
                .getPendingIntent(MANUAL_ARTICLE_PENDINGINTENT_RC, PendingIntent.FLAG_UPDATE_CURRENT);

        String contentTitle = data.get("title");
        String source = null;
        if (data.get("source") != null && !data.get("source").equals("")) {
            source = data.get("source");
        }
        String contentText = (source != null && !source.equals("")) ?
                source + " · " + data.get("mainTheme") : data.get("mainTheme");
        Log.d(TAG, contentTitle + ", " + contentText);

        manualArticleNotificationBuilder
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(individualPendingIntent);

        mNotificationManager.notify(MANUAL_ARTICLE_NOTIFICATION_ID, manualArticleNotificationBuilder.build());
    }

    private void pushSavedReminderNotification() {
        Log.d(TAG, "pushSavedReminderNotification started");
        mDataSource.getSavedArticlesReminderData((reminderList) -> {
            mExecutors.networkIO().execute(() -> {
                if (reminderList.size() == 0) {
                    Log.d(TAG, "no reminder for today");
                    return;
                }
                NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                inboxStyle.setSummaryText("Don't forget these saved articles!");

                Intent intent = new Intent(this, AcornActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent summaryPendingIntent = PendingIntent.getActivity(this, SAVED_REMINDER_PENDINGINTENT_RC, intent,
                        PendingIntent.FLAG_ONE_SHOT);

                String keys = sharedPrefs.getString(getString(R.string.notif_pref_key), "");
                String value;
                for (int i = 0; i < reminderList.size(); i++) {
                    Article article = reminderList.get(i);
                    String contentTitle = article.getTitle();
                    String source = null;
                    if (article.getSource() != null && !article.getSource().equals("")) {
                        source = article.getSource();
                    }

                    String imageUrl = null;
                    if (article.getImageUrl() != null && !article.getImageUrl().equals("")) {
                        imageUrl = article.getImageUrl();
                    } else if (article.getPostImageUrl() != null && !article.getPostImageUrl().equals("")) {
                        imageUrl = article.getPostImageUrl();
                    }

                    Bitmap bitmap = IOUtils.getBitmapFromUrl(imageUrl);
                    String contentText = (source != null && !source.equals("")) ?
                            source + " · " + article.getMainTheme() : article.getMainTheme();
                    Log.d(TAG, contentTitle + ", " + contentText);
                    String key = "s_" + article.getObjectID();

                    //Save in shared prefs
                    if (keys.equals("")) {
                        keys = key;
                    } else {
                        if (!keys.contains(key)) keys += "~·~" + key;
                    }

                    // type, articleId, text, title, source, imageUrl, theme, extra, timestamp
                    value = "savedArticleReminder" + "~·~" + // type
                            article.getObjectID() + "~·~" + // articleId
                            "Don't forget this saved article!~·~" + // text
                            contentTitle + "~·~" + // title
                            source + "~·~" + // source
                            imageUrl + "~·~" + // imageUrl
                            article.getMainTheme() + "~·~" + // theme
                            String.valueOf(article.getPubDate()) + "~·~" + // extra
                            String.valueOf((new Date()).getTime()) + "~·~" + // timestamp
                            article.getLink(); // link
                    sharedPrefs.edit().putString(key, value).apply();

                    inboxStyle.addLine(contentTitle);
                    Intent individualIntent = new Intent(this, WebViewActivity.class);
                    individualIntent.putExtra("id", article.getObjectID());
                    individualIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent individualPendingIntent = TaskStackBuilder.create(this)
                            .addNextIntentWithParentStack(individualIntent)
                            .getPendingIntent(30+i, PendingIntent.FLAG_UPDATE_CURRENT);

                    savedReminderNotificationBuilder
                            .setLargeIcon(bitmap)
                            .setContentTitle(contentTitle)
                            .setContentText(contentText)
                            .setContentIntent(individualPendingIntent);
                    mNotificationManager.notify(30+i, savedReminderNotificationBuilder.build());
                }

                savedReminderSummaryNotificationBuilder
                        .setStyle(inboxStyle)
                        .setContentIntent(summaryPendingIntent)
                        .setNumber(reminderList.size());

                sharedPrefs.edit().putString(getString(R.string.notif_pref_key), keys).apply();

                NotificationViewModel.sharedPrefs.postValue(sharedPrefs);

                mNotificationManager.notify(SAVED_REMINDER_NOTIFICATION_ID, savedReminderSummaryNotificationBuilder.build());
            });

        });
    }
}
