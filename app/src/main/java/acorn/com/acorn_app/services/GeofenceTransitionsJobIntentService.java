package acorn.com.acorn_app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.dbAddress;
import acorn.com.acorn_app.ui.activities.AcornActivity;
import acorn.com.acorn_app.ui.activities.CommentActivity;
import acorn.com.acorn_app.ui.activities.NearbyActivity;
import acorn.com.acorn_app.ui.activities.SavedArticlesActivity;
import acorn.com.acorn_app.ui.activities.WebViewActivity;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.GeofenceErrorMessages;
import acorn.com.acorn_app.utils.GeofenceUtils;
import acorn.com.acorn_app.utils.IOUtils;

import static acorn.com.acorn_app.ui.activities.NearbyActivity.RADIUS;
import static androidx.core.app.NotificationCompat.CATEGORY_RECOMMENDATION;
import static androidx.core.app.NotificationCompat.DEFAULT_SOUND;
import static androidx.core.app.NotificationCompat.DEFAULT_VIBRATE;
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
    private static String SAVED_ADDRESS_CHANNEL_ID;
    private static String SAVED_ADDRESS_CHANNEL_NAME;
    public static final int PENDINGINTENT_RC = 599;

    private AppExecutors mExecutors;
    private NetworkDataSource mDataSource;

    private FirebaseAuth auth = FirebaseAuth.getInstance();

    /**
     * Convenience method for enqueuing work in to this service.
     */
    public static void enqueueWork(Context context, Intent intent) {
        CHANNEL_ID = context.getString(R.string.geofence_notification_channel_id);
        CHANNEL_NAME = context.getString(R.string.geofence_notification_channel_name);
        SAVED_ADDRESS_CHANNEL_ID = context.getString(R.string.saved_reminder_notification_channel_id);
        SAVED_ADDRESS_CHANNEL_NAME = context.getString(R.string.saved_reminder_notification_channel_name);
        enqueueWork(context, GeofenceTransitionsJobIntentService.class, JOB_ID, intent);
    }

    /**
     * Handles incoming intents.
     * @param intent sent by Location Services. This Intent is provided to Location
     *               Services (inside a PendingIntent) when addGeofences() is called.
     */
    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(TAG, "onHandleWork");
        mExecutors = AppExecutors.getInstance();
        mDataSource = NetworkDataSource.getInstance(getApplicationContext(), mExecutors);

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        GeofenceUtils geofenceUtils = GeofenceUtils.getInstance(this, mDataSource);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            boolean stationGeofenceHandled = false;
            GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
            if (geofencingEvent.hasError()) {
                String errorMessage = GeofenceErrorMessages.getErrorString(getApplicationContext(),
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

                List<Article> savedArticleList = new ArrayList<>();
                Map<String, List<String>> postcodeMap = new HashMap<>();
                List<Task<Boolean>> taskList = new ArrayList<>();

                // Get the transition details.
                for (Geofence geofence : triggeringGeofences) {
                    String geofenceId = geofence.getRequestId();
                    Log.d(TAG, "geofence entered/dwelled: " + geofenceId);

                    if (!geofenceId.startsWith("article")) {
                        // mrt geofence triggered
                        if (stationGeofenceHandled) continue;
                        // Get articles near stationName
                        mExecutors.networkIO().execute(() -> {
                            mDataSource.getMrtStations(mrtStations -> {
                                geofenceUtils.getNearestStationsFrom(location, 1, mrtStations, station -> {
                                    Log.d(TAG, "geofence station: " + station.toString());
                                    Map.Entry<String, Location> entry = station.entrySet().iterator().next();
                                    double latitude = entry.getValue().getLatitude();
                                    double longitude = entry.getValue().getLongitude();
                                    mDataSource.getNearbyArticles(latitude, longitude, RADIUS, null, true,
                                            3, articles -> {
                                                mExecutors.networkIO().execute(() -> {
                                                    sendNotification(entry.getKey(), articles);
                                                });
                                    });
                                });
                            }, null);
                        });
                        stationGeofenceHandled = true;
                    } else {
                        // saved address geofence triggered
                        String articleId = geofenceId.substring(8);
                        Log.d(TAG, "geofence articleId: " + articleId);
                        // Get article triggered

                        TaskCompletionSource<Boolean> savedArticleTaskSource = new TaskCompletionSource<>();
                        Task<Boolean> savedArticleTask = savedArticleTaskSource.getTask();
                        taskList.add(savedArticleTask);

                        mExecutors.networkIO().execute(() -> {
                            TaskCompletionSource<Boolean> articleTaskSource = new TaskCompletionSource<>();
                            Task<Boolean> articleTask = articleTaskSource.getTask();
                            mDataSource.getSingleArticle(articleId, article -> {
                                savedArticleList.add(article);
                                articleTaskSource.setResult(true);
                            });

                            TaskCompletionSource<Boolean> addressTaskSource = new TaskCompletionSource<>();
                            Task<Boolean> addressTask = addressTaskSource.getTask();
                            List<String> postcodeList = new ArrayList<>();
                            mDataSource.getSavedAddressFor(articleId, addresses -> {
                                for (dbAddress address : addresses) {
                                    Location addressLoc = new Location("");
                                    addressLoc.setLatitude(address.latitude);
                                    addressLoc.setLongitude(address.longitude);

                                    if (addressLoc.distanceTo(location) < 1000) {
                                        Pattern postcodePattern = Pattern.compile(".*(Singapore [0-9]{6}|S[0-9]{6})", Pattern.CASE_INSENSITIVE);
                                        String postcode = postcodePattern.matcher(address.address).replaceAll("$1");
                                        postcodeList.add(postcode);
                                    }
                                }
                                addressTaskSource.setResult(true);
                            });

                            Tasks.whenAll(articleTask, addressTask).addOnSuccessListener(aVoid -> {
                                postcodeMap.put(articleId, postcodeList);
                                savedArticleTaskSource.setResult(true);
                            });
                        });
                    }
                }

                Tasks.whenAll(taskList).addOnSuccessListener(aVoid -> {
                    mExecutors.networkIO().execute(() -> {
                        sendSavedAddressNotification(savedArticleList, postcodeMap);
                    });
                });
            } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                boolean hasStationGeofence = false;
                // Get the geofences that were triggered. A single event can trigger multiple geofences.
                List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
                for (Geofence geofence : triggeringGeofences) {
                    Log.d(TAG, "geofence exited: " + geofence);
                    if (!geofence.getRequestId().startsWith("article")) hasStationGeofence = true;
                }

                if (hasStationGeofence) {
                    geofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.REMOVE;
                    geofenceUtils.performPendingGeofenceTask(this, () -> {
                        geofenceUtils.mPendingGeofenceTask = GeofenceUtils.PendingGeofenceTask.ADD;
                        geofenceUtils.performPendingGeofenceTask(this, location);
                    });
                }
            } else {
                // Log the error.
                Log.e(TAG, getString(R.string.geofence_transition_invalid_type, geofenceTransition));
            }
        });
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

    private String getFirstTriggeredGeofence(List<Geofence> triggeringGeofences) {
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

        NotificationManager notificationManager = //NotificationManagerCompat.from(getApplicationContext());
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

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

            Intent individualIntent = new Intent(getApplicationContext(), WebViewActivity.class);
            if (article.getLink() == null || article.getLink().equals("")) {
                individualIntent = new Intent(getApplicationContext(), CommentActivity.class);
            }
            individualIntent.putExtra("id", article.getObjectID());
            individualIntent.putExtra("fromNotif", true);
            individualIntent.putExtra("notifType", "Nearby");
            individualIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent individualPendingIntent = TaskStackBuilder.create(getApplicationContext())
                    .addNextIntentWithParentStack(individualIntent)
                    .getPendingIntent(20+i, PendingIntent.FLAG_UPDATE_CURRENT);

            Bitmap bitmap = IOUtils.getBitmapFromUrl(imageUrl);
            contentText = (source != null && !source.equals("")) ?
                    source + " · " + article.getMainTheme() : article.getMainTheme();

            Notification notification =
                    new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notif_acorn)
                            .setColor(Color.RED)
                            .setLargeIcon(bitmap)
                            .setContentTitle(title)
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
        Intent stationIntent = new Intent(getApplicationContext(), NearbyActivity.class);
        stationIntent.putExtra("stationName", stationName);
        stationIntent.putExtra("fromNotif", true);
        stationIntent.putExtra("notifType", "Nearby");
        stationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent stationPendingIntent = TaskStackBuilder.create(getApplicationContext())
                .addNextIntentWithParentStack(stationIntent)
                .getPendingIntent(29, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification stationNotification =
                new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
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
        Intent intent = new Intent(getApplicationContext(), NearbyActivity.class);
        intent.putExtra("stationName", stationName);
        intent.putExtra("fromNotif", true);
        intent.putExtra("notifType", "Nearby");
        PendingIntent pendingIntent = TaskStackBuilder.create(getApplicationContext())
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(PENDINGINTENT_RC, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification summaryNotification =
                new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
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
            channel.enableVibration(false);

            if (notificationManager == null)
                notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            try {
                notificationManager.createNotificationChannel(channel);
            } catch (Exception e) {
                if (auth.getUid() != null) {
                    mDataSource.logNotificationError(auth.getUid(), "null_notif_manager: " + e.getLocalizedMessage(), "location");
                    return;
                }
            }
        }

        if (notificationManager != null) {
            for (int i = 0; i < notifications.size(); i++) {
                notificationManager.notify(20 + i, notifications.get(i));
            }
            notificationManager.notify(NOTIFICATION_ID, summaryNotification);
        }
    }

    /**
     * Posts a notification in the notification bar when a transition is detected.
     * If the user clicks the notification, control goes to the WebViewActivity.
     */
    private void sendSavedAddressNotification(List<Article> articleList, Map<String, List<String>> postcodeMap) {
        if (articleList.size() < 1) {
            Log.d(TAG, "no saved address notifications");
            return;
        }

        Log.d(TAG, "postcodeMap: " + postcodeMap);

        final String GROUP_NAME = "savedAddressGeofenceGroup";
        final int PENDINGINTENT_RC = 510;
        final List<Notification> notifications = new ArrayList<>();

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setSummaryText("These items you saved are nearby!");

        Intent intent = new Intent(this, SavedArticlesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("fromNotif", true);
        intent.putExtra("notifType", "Saved Address Reminder");
        PendingIntent summaryPendingIntent = PendingIntent.getActivity(this, PENDINGINTENT_RC, intent,
                PendingIntent.FLAG_ONE_SHOT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

        // create summary notification
        NotificationCompat.Builder savedAddressSummaryNotificationBuilder =
                new NotificationCompat.Builder(this, SAVED_ADDRESS_CHANNEL_ID)
                        .setOnlyAlertOnce(true)
                        .setGroup(GROUP_NAME)
                        .setGroupSummary(true)
                        .setDefaults(DEFAULT_SOUND|DEFAULT_VIBRATE)
                        .setLights(Color.YELLOW, 700, 300)
                        .setPriority(PRIORITY_HIGH)
                        .setCategory(CATEGORY_RECOMMENDATION)
                        .setVisibility(VISIBILITY_PUBLIC)
                        .setSmallIcon(R.drawable.ic_notif_acorn)
                        .setColor(getColor(R.color.colorPrimary))
                        .setStyle(inboxStyle)
                        .setContentIntent(summaryPendingIntent)
                        .setNumber(articleList.size())
                        .setAutoCancel(true);

        for (int i = 0; i < articleList.size(); i++) {
            // Send individual notifications for each article
            Article article = articleList.get(i);
            String imageUrl = null;
            if (article.getImageUrl() != null && !article.getImageUrl().equals("")) {
                imageUrl = article.getImageUrl();
            } else if (article.getPostImageUrl() != null && !article.getPostImageUrl().equals("")) {
                imageUrl = article.getPostImageUrl();
            }
            String title = null;
            if (article.getSource() != null && !article.getSource().equals("")) {
                title = article.getTitle();
            } else if (article.getPostAuthor() != null && !article.getPostAuthor().equals("")) {
                title = article.getPostText();
            }

            inboxStyle.addLine(article.getTitle());

            Intent individualIntent = new Intent(getApplicationContext(), WebViewActivity.class);
            if (article.getLink() == null || article.getLink().equals("")) {
                individualIntent = new Intent(getApplicationContext(), CommentActivity.class);
            }
            individualIntent.putExtra("id", article.getObjectID());
            individualIntent.putExtra("fromNotif", true);
            individualIntent.putExtra("notifType", "Saved Address Reminder");
            List<String> postcodes = postcodeMap.get(article.getObjectID());
            if (postcodes != null) {
                individualIntent.putExtra("postcode", postcodes.toArray(new String[(postcodes.size())]));
            }
            individualIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent individualPendingIntent = TaskStackBuilder.create(getApplicationContext())
                    .addNextIntentWithParentStack(individualIntent)
                    .getPendingIntent(40 + i, PendingIntent.FLAG_UPDATE_CURRENT);

            Bitmap bitmap = IOUtils.getBitmapFromUrl(imageUrl);

            Notification notification =
                    new NotificationCompat.Builder(getApplicationContext(), SAVED_ADDRESS_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notif_acorn)
                            .setColor(getColor(R.color.colorPrimary))
                            .setLargeIcon(bitmap)
                            .setContentTitle("An item you saved is nearby!")
                            .setContentText(title)
                            .setContentIntent(individualPendingIntent)
                            .setAutoCancel(true)
                            .setGroup(GROUP_NAME)
                            .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                            .build();
            notifications.add(notification);
        }

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(SAVED_ADDRESS_CHANNEL_ID,
                    SAVED_ADDRESS_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.setVibrationPattern(new long[]{0});
            channel.enableVibration(false);

            try {
                notificationManager.createNotificationChannel(channel);
            } catch (Exception e) {
                if (auth.getUid() != null) {
                    mDataSource.logNotificationError(auth.getUid(), "null_notif_manager: " + e.getLocalizedMessage(), "location");
                    return;
                }
            }
        }

        for (int i = 0; i < notifications.size(); i++) {
            notificationManager.notify(40+i, notifications.get(i));
        }
        notificationManager.notify(40, savedAddressSummaryNotificationBuilder.build());
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
