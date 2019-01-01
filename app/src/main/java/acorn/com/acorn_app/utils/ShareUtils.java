package acorn.com.acorn_app.utils;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.ShortDynamicLink;

import java.util.function.Consumer;

public class ShareUtils {
    private static final String TAG = "ShareUtils";

    public static String createShareUri(String articleId, String url, String sharerId) {
        // Remove http:// and https:// from url
        if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }

//        Uri.Builder builder = new Uri.Builder();
//        builder.scheme("https")
//                .authority("acorncommunity.sg")
//                .appendPath("article")
//                .appendQueryParameter("id", articleId)
//                .appendQueryParameter("url", url)
//                .appendQueryParameter("sharerId", sharerId);
//        return builder.build();

        return "https://acorncommunity.sg/article?id=" + articleId + "&url=" + url + "&sharerId=" + sharerId;
    }

    public static void createShortDynamicLink(String url, Consumer<String> onComplete) {
        FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(url))
                .setDomainUriPrefix("https://acorncommunity.page.link")
                .setAndroidParameters(
                        new DynamicLink.AndroidParameters.Builder("acorn.com.acorn_app")
                                .setMinimumVersion(46)
                                .build())
                .setIosParameters(
                        new DynamicLink.IosParameters.Builder("sg.acorncommunity.acorn")
                                .setAppStoreId("1435141923")
                                .setMinimumVersion("1.2.3")
                                .build())
                .buildShortDynamicLink()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Uri shortLink = task.getResult().getShortLink();
                        onComplete.accept(shortLink.toString());
                    } else {
                        Log.d(TAG, "Failed to get short link");
                    }
                });
    }
}
