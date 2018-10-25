package acorn.com.acorn_app.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.ui.activities.AcornActivity;
import acorn.com.acorn_app.ui.activities.CommentActivity;
import acorn.com.acorn_app.ui.activities.WebViewActivity;
import acorn.com.acorn_app.ui.viewModels.NotificationViewModel;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.CommentActivity.mCommentOpenObjectID;
import static android.support.v4.app.NotificationCompat.CATEGORY_SOCIAL;
import static android.support.v4.app.NotificationCompat.DEFAULT_SOUND;
import static android.support.v4.app.NotificationCompat.DEFAULT_VIBRATE;
import static android.support.v4.app.NotificationCompat.GROUP_ALERT_SUMMARY;
import static android.support.v4.app.NotificationCompat.PRIORITY_HIGH;
import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMsgService";

    private static NotificationManager mNotificationManager;
    
    private final int COMMENT_NOTIFICATION_ID = 9001;
    private final String COMMENT_GROUP_NAME = "comments";
    private final int COMMENT_SUMMARY_PENDINGINTENT_RC = 501;
    private static NotificationCompat.Builder commentNotificationBuilder;
    private static NotificationCompat.Builder commentSummaryNotificationBuilder;
    
    private final int MANUAL_ARTICLE_NOTIFICATION_ID = 9003;
    private final String MANUAL_ARTICLE_GROUP_NAME = "manualArticle";
    private final int MANUAL_ARTICLE_PENDINGINTENT_RC = 503;
    private static NotificationCompat.Builder manualArticleNotificationBuilder;
    

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

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String COMMENT_CHANNEL_ID = getString(R.string.comment_notification_channel_id);
        String COMMENT_CHANNEL_NAME = getString(R.string.comment_notification_channel_name);
        commentSummaryNotificationBuilder =
                new NotificationCompat.Builder(this, COMMENT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher)
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
                        .setSmallIcon(R.drawable.ic_launcher)
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
                        .setSmallIcon(R.drawable.ic_launcher)
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
        if (data.get("type").equals("comment")) {
            sendCommentNotification(data);
        } else if (data.get("type").equals("manualArticle")) {
            pushManualArticleNotification(data);
        }
    }

    private void sendCommentNotification(Map<String, String> data) {
        if (mCommentOpenObjectID != null && mCommentOpenObjectID.equals(data.get("articleId")))
            return;

        Intent intent = new Intent(this, AcornActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, COMMENT_SUMMARY_PENDINGINTENT_RC, intent,
                PendingIntent.FLAG_ONE_SHOT);

        SharedPreferences sharedPrefs = getSharedPreferences(getString(R.string.notif_pref_id), MODE_PRIVATE);
        updateSharedPrefs(sharedPrefs, data);

        String commentsText = sharedPrefs.getString(getString(R.string.notif_pref_c_text), "");
        if (commentsText.equals("")) {
            commentsText = data.get("commentText");
        } else {
            commentsText += "·" + data.get("commentText");
        }

        List<String> comments = Arrays.asList(commentsText.split("·"));
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
        int pendingIntentRC = 0;
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

        mNotificationManager.notify(comments.size(), commentNotificationBuilder.build());
        mNotificationManager.notify(COMMENT_NOTIFICATION_ID, commentSummaryNotificationBuilder.build());
    }

    private void updateSharedPrefs(SharedPreferences sharedPrefs, Map<String, String> data) {
        String keys = sharedPrefs.getString(getString(R.string.notif_pref_key), "");

        List<String> keyList = Arrays.asList(keys.split("·"));
        String key = "c_" + data.get("articleId");
        if (keyList.contains(key)) {
            String value = sharedPrefs.getString(key, "");
            List<String> valueList = Arrays.asList(value.split("·"));
            int count = Integer.parseInt(Arrays.asList(valueList.get(2).split(" ")).get(0)) + 1;
            // type, articleId, text, title, source, imageUrl, theme, extra, timestamp
            value = "comment" + "·" + // type
                    data.get("articleId") + "·" + // articleId
                    count + " new comments on an article you follow" + "·" + // text
                    data.get("title") + "·" + // title
                    data.get("source") + "·" + // source
                    data.get("imageUrl") + "·" + // imageUrl
                    data.get("mainTheme") + "·" + // theme
                    data.get("mainTheme") + "·" + // extra
                    data.get("timestamp") + "·" + // timestamp
                    ""; // link

            sharedPrefs.edit().putString(key, value).apply();

        } else {
            if (keys.equals("")) {
                keys = key;
            } else {
                keys += "·" + key;
            }
            // type, articleId, text, title, source, imageUrl, theme, extra, timestamp, link
            String value = "comment" + "·" + // type
                    data.get("articleId") + "·" + // articleId
                    "1 new comment on an article you follow" + "·" + // text
                    data.get("title") + "·" + // title
                    data.get("source") + "·" + // source
                    data.get("imageUrl") + "·" + // imageUrl
                    data.get("mainTheme") + "·" + // theme
                    data.get("mainTheme") + "·" + // extra
                    data.get("timestamp") + "·" + // timestamp
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
}
