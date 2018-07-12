package acorn.com.acorn_app.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtils {

    public static String parseDate(Long date) {
        try {
            SimpleDateFormat rawFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            rawFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
//            Date pubDate = rawFormat.parse(date);
            Date pubDate = new Date(-1L * date);
//            Log.d("date", rawFormat.format(pubDate));
            Date now = new Date();

            double diff = now.getTime() - pubDate.getTime();

            int diffSeconds = (int) Math.round(diff / 1000D);
            int diffMinutes = (int) Math.round(diff / (1000D * 60D));
            int diffHours = (int) Math.round(diff / (1000D * 60D * 60D));

            if (diffSeconds < 60) {
                return diffSeconds + "s ago";
            } else if (diffMinutes < 60) {
                return diffMinutes + "m ago";
            } else if (diffHours < 24) {
                return diffHours + "h ago";
            } else {
                SimpleDateFormat parsedFormat = new SimpleDateFormat("d MMM");
                return parsedFormat.format(pubDate);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String parseCommentDate(Long date) {
        try {
            SimpleDateFormat rawFormat = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
            SimpleDateFormat newFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM");
            rawFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            newFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            timeFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));

            Date pubDate = new Date(-1L * date);
            Date now = new Date();
            Date cutoff = newFormat.parse(rawFormat.format(now));
//            Log.d("date", newFormat.format(cutoff));

            if (pubDate.getTime() < cutoff.getTime()) {
                return dateFormat.format(pubDate);
            }
            return timeFormat.format(pubDate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
