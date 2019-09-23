package acorn.com.acorn_app.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {

    public static String parseDate(Long date) {
        SimpleDateFormat rawFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        rawFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date pubDate = new Date(-1L * date);
        Date now = new Date();

        double diff = now.getTime() - pubDate.getTime();

        int diffSeconds = (int) Math.round(diff / 1000D);
        int diffMinutes = (int) Math.round(diff / (1000D * 60D));
        int diffHours = (int) Math.round(diff / (1000D * 60D * 60D));

        if (diff < 0) {
            return "";
        } else if (diffSeconds < 60) {
            return diffSeconds + "s ago";
        } else if (diffMinutes < 60) {
            return diffMinutes + "m ago";
        } else if (diffHours < 24) {
            return diffHours + "h ago";
        } else {
            SimpleDateFormat parsedFormat = new SimpleDateFormat("d MMM", Locale.US);
            return parsedFormat.format(pubDate);
        }
    }

    public static String parseCommentDate(Long date) {
        try {
            SimpleDateFormat rawFormat = new SimpleDateFormat("yyyy-MM-dd 00:00:00", Locale.US);
            SimpleDateFormat newFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
            SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM", Locale.US);
            rawFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            newFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            timeFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));

            Date pubDate = new Date(-1L * date);
            Date now = new Date();
            Date cutoff = newFormat.parse(rawFormat.format(now));

            if (pubDate.getTime() < cutoff.getTime()) {
                return dateFormat.format(pubDate);
            }
            return timeFormat.format(pubDate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Long getMidnightOf(int daysLater) {
        SimpleDateFormat midnight = new SimpleDateFormat("yyyy-MM-dd 00:00:00", Locale.US);
        midnight.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        Date now = new Date();
        try {
            long thisMidnight = midnight.parse(midnight.format(now)).getTime();
            return thisMidnight + (daysLater * 24L * 60L * 60L * 1000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
