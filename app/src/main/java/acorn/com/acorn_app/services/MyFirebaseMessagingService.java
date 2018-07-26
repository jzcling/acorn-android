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
import acorn.com.acorn_app.ui.viewModels.NotificationViewModel;
import acorn.com.acorn_app.utils.AppExecutors;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.CommentActivity.mCommentOpenObjectID;
import static android.support.v4.app.NotificationCompat.CATEGORY_SOCIAL;
import static android.support.v4.app.NotificationCompat.DEFAULT_SOUND;
import static android.support.v4.app.NotificationCompat.DEFAULT_VIBRATE;
import static android.support.v4.app.NotificationCompat.GROUP_ALERT_SUMMARY;
import static android.support.v4.app.NotificationCompat.PRIORITY_HIGH;
import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMsgService";
    private final int COMMENT_NOTIFICATION_ID = 9001;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    private final String GROUP_NAME = "comments";
    private final int SUMMARY_PENDINGINTENT_RC = 501;
    private String CHANNEL_ID;
    private String CHANNEL_NAME;

    private static NotificationCompat.Builder notificationBuilder;
    private static NotificationCompat.Builder summaryNotificationBuilder;
    private static NotificationManager notificationManager;

    private void sendRegistrationToServer(String token) {
        DatabaseReference user = FirebaseDatabase.getInstance()
                .getReference("user/" + mUid + "/instanceId");
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
        CHANNEL_ID = getString(R.string.comment_notification_channel_id);
        CHANNEL_NAME = getString(R.string.comment_notification_channel_name);
        notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        summaryNotificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setGroup(GROUP_NAME)
                        .setGroupSummary(true)
                        .setDefaults(DEFAULT_SOUND|DEFAULT_VIBRATE)
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setCategory(CATEGORY_SOCIAL)
                        .setVisibility(VISIBILITY_PUBLIC);

        notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setAutoCancel(true)
                        .setGroup(GROUP_NAME)
                        .setGroupAlertBehavior(GROUP_ALERT_SUMMARY);

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
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (data.get("type").equals("comment")) {
            sendCommentNotification(data);
        }
    }

    private void sendCommentNotification(Map<String, String> data) {
        if (mCommentOpenObjectID != null && mCommentOpenObjectID.equals(data.get("articleId")))
            return;

        Intent intent = new Intent(this, AcornActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, SUMMARY_PENDINGINTENT_RC, intent,
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
        Log.d(TAG, "comments: " + comments);

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

        summaryNotificationBuilder
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

        notificationBuilder
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(individualPendingIntent);

        notificationManager.notify(comments.size(), notificationBuilder.build());
        notificationManager.notify(COMMENT_NOTIFICATION_ID, summaryNotificationBuilder.build());
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
                    data.get("timestamp"); // timestamp
            sharedPrefs.edit().putString(key, value).apply();
            Log.d(TAG, "value_key: " + key + ", value: " + value);
        } else {
            if (keys.equals("")) {
                keys = key;
            } else {
                keys += "·" + key;
            }
            // type, articleId, text, title, source, imageUrl, theme, extra, timestamp
            String value = "comment" + "·" + // type
                    data.get("articleId") + "·" + // articleId
                    "1 new comment on an article you follow" + "·" + // text
                    data.get("title") + "·" + // title
                    data.get("source") + "·" + // source
                    data.get("imageUrl") + "·" + // imageUrl
                    data.get("mainTheme") + "·" + // theme
                    data.get("mainTheme") + "·" + // extra
                    data.get("timestamp"); // timestamp
            sharedPrefs.edit().putString(getString(R.string.notif_pref_key), keys)
                    .putString(key, value).apply();
            Log.d(TAG, "keys_key: " + getString(R.string.notif_pref_key) + ", keys: " + keys);
            Log.d(TAG, "value_key: " + key + ", value: " + value);
        }
        NotificationViewModel.sharedPrefs.postValue(sharedPrefs);
    }
}
