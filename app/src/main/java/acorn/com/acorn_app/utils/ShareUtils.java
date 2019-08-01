package acorn.com.acorn_app.utils;

import android.net.Uri;
import android.util.Log;

import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.ShortDynamicLink;

import java.net.URLEncoder;
import java.util.function.Consumer;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class ShareUtils {
    private static final String TAG = "ShareUtils";

    public static String createShareUri(String articleId, String url, String sharerId) {
        // Remove http:// and https:// from url
        if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }

        return "https://acorncommunity.sg/article" + "?id=" + articleId + "&url=" + url + "&sharerId=" + sharerId;
    }

    public static String createVideoShareUri(String videoId, String sharerId) {
        // Remove http:// and https:// from url
        String youtubeId = videoId.substring(3);
        return "https://acorncommunity.sg/video" + "?id=" + videoId + "&youtubeId=" + youtubeId + "&sharerId=" + sharerId;
    }

    public static void createShortDynamicLink(String url, Consumer<String> onComplete) {
        FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(url))
                .setDomainUriPrefix("https://acorncommunity.sg/share")
                .setAndroidParameters(
                        new DynamicLink.AndroidParameters.Builder("acorn.com.acorn_app")
                                .setMinimumVersion(46)
                                .setFallbackUrl(Uri.parse(url))
                                .build())
                .setIosParameters(
                        new DynamicLink.IosParameters.Builder("sg.acorncommunity.acorn")
                                .setAppStoreId("1435141923")
                                .setMinimumVersion("1.2.5")
                                .setFallbackUrl(Uri.parse(url))
                                .build())
                .setGoogleAnalyticsParameters(
                        new DynamicLink.GoogleAnalyticsParameters.Builder()
                                .setSource(mUid)
                                .setMedium("share")
                                .build())
                .buildShortDynamicLink(ShortDynamicLink.Suffix.SHORT)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Uri shortLink = task.getResult().getShortLink();
                        onComplete.accept(shortLink.toString());
                    } else {
                        Log.d(TAG, "Failed to get short share link");
                    }
                });
    }
}
