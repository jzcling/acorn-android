package acorn.com.acorn_app.utils;

import android.net.Uri;
import android.util.Log;

import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.ShortDynamicLink;

import java.util.function.Consumer;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class InviteUtils {
    private static final String TAG = "InviteUtils";

    public static void createShortDynamicLink(String uid, Consumer<String> onComplete) {
        String url = "https://acorncommunity.sg/?referrer=" + uid;
        FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(url))
                .setDomainUriPrefix("https://acorncommunity.sg/invite")
                .setAndroidParameters(
                        new DynamicLink.AndroidParameters.Builder("acorn.com.acorn_app")
                                .setMinimumVersion(46)
                                .build())
                .setIosParameters(
                        new DynamicLink.IosParameters.Builder("sg.acorncommunity.acorn")
                                .setAppStoreId("1435141923")
                                .setMinimumVersion("1.2.5")
                                .build())
                .setGoogleAnalyticsParameters(
                        new DynamicLink.GoogleAnalyticsParameters.Builder()
                                .setSource(mUid)
                                .setMedium("invite")
                                .build())
                .buildShortDynamicLink(ShortDynamicLink.Suffix.SHORT)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Uri shortLink = task.getResult().getShortLink();
                        onComplete.accept(shortLink.toString());
                    } else {
                        Log.d(TAG, "Failed to get short invite link");
                    }
                });
    }
}
