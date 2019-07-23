package acorn.com.acorn_app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.ui.activities.CommentActivity;
import acorn.com.acorn_app.ui.activities.NearbyActivity;
import acorn.com.acorn_app.ui.activities.WebViewActivity;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.GeofenceErrorMessages;
import acorn.com.acorn_app.utils.IOUtils;

import static acorn.com.acorn_app.ui.activities.NearbyActivity.RADIUS;
import static androidx.core.app.NotificationCompat.CATEGORY_RECOMMENDATION;
import static androidx.core.app.NotificationCompat.DEFAULT_SOUND;
import static androidx.core.app.NotificationCompat.GROUP_ALERT_SUMMARY;
import static androidx.core.app.NotificationCompat.PRIORITY_HIGH;
import static androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC;

/**
 * Listener for geofence transition changes.
 *
 * Receives geofence transition events from Location Services in the form of an Intent containing
 * the transition type and geofence id(s) that triggered the transition. Creates a notification
 * as the output.
 */
public class GeofenceTransitionsJobIntentService extends JobIntentService {

    private static final int JOB_ID = 573;

    private static final String TAG = "GeofenceTransitionsIS";

    private static String CHANNEL_ID;
    private static String CHANNEL_NAME;
    public static final int PENDINGINTENT_RC = 599;

    private AppExecutors mExecutors;
    private NetworkDataSource mDataSource;

    /**
     * Convenience method for enqueuing work in to this service.
     */
    public static void enqueueWork(Context context, Intent intent) {
        CHANNEL_ID = context.getString(R.string.geofence_notification_channel_id);
        CHANNEL_NAME = context.getString(R.string.geofence_notification_channel_name);
        enqueueWork(context, GeofenceTransitionsJobIntentService.class, JOB_ID, intent);
    }

    /**
     * Handles incoming intents.
     * @param intent sent by Location Services. This Intent is provided to Location
     *               Services (inside a PendingIntent) when addGeofences() is called.
     */
    @Override
    protected void onHandleWork(Intent intent) {
        Log.d(TAG, "onHandleWork");
        mExecutors = AppExecutors.getInstance();
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            String errorMessage = GeofenceErrorMessages.getErrorString(this,
                    geofencingEvent.getErrorCode());
            Log.e(TAG, errorMessage);
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL
                || geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {

            // Get the geofences that were triggered. A single event can trigger multiple geofences.
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            // Get the transition details.
            String stationName = getFirstTriggeredStationName(triggeringGeofences);
            Log.d(TAG, "geofence station name: " + stationName);

            // Get articles near stationName
            mExecutors.networkIO().execute(() -> {
                mDataSource.getMrtStationByName(stationName, station -> {
                    Log.d(TAG, "geofence station: " + station.toString());
                    Double latitude = (Double) station.get(stationName).get("latitude");
                    Double longitude = (Double) station.get(stationName).get("longitude");
                    if (latitude != null && longitude != null)
                        mDataSource.getNearbyArticles(latitude, longitude, RADIUS, true,
                                3, (articles) -> {
                                    mExecutors.networkIO().execute(() -> {
                                        sendNotification(stationName, articles);
                                    });
                        });
                });
            });
        } else {
            // Log the error.
            Log.e(TAG, getString(R.string.geofence_transition_invalid_type, geofenceTransition));
        }
    }

    /**
     * Gets transition details and returns them as a formatted string.
     *
     * @param geofenceTransition    The ID of the geofence transition.
     * @param triggeringGeofences   The geofence(s) triggered.
     * @return                      The transition details formatted as String.
     */
    private String getGeofenceTransitionDetails(
            int geofenceTransition,
            List<Geofence> triggeringGeofences) {

        String geofenceTransitionString = getTransitionString(geofenceTransition);

        // Get the Ids of each geofence that was triggered.
        ArrayList<String> triggeringGeofencesIdsList = new ArrayList<>();
        for (Geofence geofence : triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.getRequestId());
        }
        String triggeringGeofencesIdsString = TextUtils.join(", ",  triggeringGeofencesIdsList);

        return geofenceTransitionString + ": " + triggeringGeofencesIdsString;
    }

    private String getFirstTriggeredStationName(List<Geofence> triggeringGeofences) {
        return triggeringGeofences.get(0).getRequestId();
    }

    /**
     * Posts a notification in the notification bar when a transition is detected.
     * If the user clicks the notification, control goes to the NearbyActivity.
     */
    private void sendNotification(String stationName, List<Article> articles) {
        final String GROUP_NAME = "mrtGeofenceGroup";
        final int NOTIFICATION_ID = 9100;
        final List<Notification> notifications = new ArrayList<>();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
//                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Set inbox style for the 3 articles
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setSummaryText("Don't miss out on these near " + stationName + "!");// Create general notification for station

        // Add station notif title first
        String stationNotifTitle = "\uD83D\uDCCD Check out what's around " + stationName + "!";
        inboxStyle.addLine(stationNotifTitle);

        // Send individual notifications for each article
        for (int i = articles.size() - 1; i >= 0; i--) {
            Article article = articles.get(i);

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

            inboxStyle.addLine(title);

            Intent individualIntent = new Intent(this, WebViewActivity.class);
            if (article.getLink() == null || article.getLink().equals("")) {
                individualIntent = new Intent(this, CommentActivity.class);
            }
            individualIntent.putExtra("id", article.getObjectID());
            individualIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent individualPendingIntent = TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(individualIntent)
                    .getPendingIntent(20+i, PendingIntent.FLAG_UPDATE_CURRENT);

            Bitmap bitmap = IOUtils.getBitmapFromUrl(imageUrl);
            contentText = (source != null && !source.equals("")) ?
                    source + " · " + article.getMainTheme() : article.getMainTheme();

            Notification notification =
                    new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notif_acorn)
                            .setColor(Color.RED)
                            .setLargeIcon(bitmap)
                            .setContentTitle(article.getTitle())
                            .setContentText(contentText)
                            .setContentIntent(individualPendingIntent)
                            .setAutoCancel(true)
                            .setGroup(GROUP_NAME)
                            .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                            .build();
            notifications.add(notification);
//            notificationManager.notify(20+i, notification);
        }

        // general station notification
        Intent stationIntent = new Intent(this, NearbyActivity.class);
        stationIntent.putExtra("stationName", stationName);
        stationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent stationPendingIntent = TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(stationIntent)
                .getPendingIntent(29, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification stationNotification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(Color.RED)
                        .setContentTitle(stationNotifTitle)
                        .setContentText("Get deals, events and restaurant recommendations!")
                        .setContentIntent(stationPendingIntent)
                        .setAutoCancel(true)
                        .setGroup(GROUP_NAME)
                        .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                        .build();
        notifications.add(stationNotification);
//        notificationManager.notify(29, stationNotification);

        // Prepare and send summary notification in inboxStyle
        Intent intent = new Intent(this, NearbyActivity.class);
        intent.putExtra("stationName", stationName);
        PendingIntent pendingIntent = TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(PENDINGINTENT_RC, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification summaryNotification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setOnlyAlertOnce(true)
                        .setGroup(GROUP_NAME)
                        .setGroupSummary(true)
                        .setDefaults(DEFAULT_SOUND)
                        .setVibrate(new long[]{0})
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setCategory(CATEGORY_RECOMMENDATION)
                        .setVisibility(VISIBILITY_PUBLIC)
                        .setStyle(inboxStyle)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(Color.RED)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setNumber(articles.size())
                        .build();

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.setVibrationPattern(new long[]{0});
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        for (int i = 0; i < notifications.size(); i++) {
            notificationManager.notify(20+i, notifications.get(i));
        }
        notificationManager.notify(NOTIFICATION_ID, summaryNotification);
    }

    /**
     * Maps geofence transition types to their human-readable equivalents.
     *
     * @param transitionType    A transition type constant defined in Geofence
     * @return                  A String indicating the type of transition
     */
    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return getString(R.string.geofence_transition_entered);
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return getString(R.string.geofence_transition_exited);
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                return getString(R.string.geofence_transition_dwelled);
            default:
                return getString(R.string.unknown_geofence_transition);
        }
    }
}
