package acorn.com.acorn_app.test;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;

import acorn.com.acorn_app.utils.DateUtils;

import static org.junit.Assert.*;

public class DateUtilsTest {
    @Test
    public void parseDate_futureTime() {
        long futureTime = (new Date()).getTime() + 1000L;
        assertEquals("Check that future time returns an empty string",
                "", DateUtils.parseDate(-futureTime));
    }

    @Test
    public void parseDate_lt60sAgo() {
        long lt60sAgo = (new Date()).getTime() - 30L * 1000L;
        assertEquals("Check that any time less than 60 seconds ago returns _s ago",
                "30s ago", DateUtils.parseDate(-lt60sAgo));
    }

    @Test
    public void parseDate_lt60mAgo() {
        long lt60mAgo = (new Date()).getTime() - 30L * 60L * 1000L;
        assertEquals("Check that any time less than 60 minutes ago returns _m ago",
                "30m ago", DateUtils.parseDate(-lt60mAgo));
    }

    @Test
    public void parseDate_lt24hAgo() {
        long lt24hAgo = (new Date()).getTime() - 12L * 60L * 60L * 1000L;
        assertEquals("Check that any time less than 24 hours ago returns _h ago",
                "12h ago", DateUtils.parseDate(-lt24hAgo));
    }

    @Test
    public void parseDate_gt24hAgo() {
        long gt24hAgo = (new Date()).getTime() - 25L * 60L * 60L * 1000L;
        SimpleDateFormat parsedFormat = new SimpleDateFormat("d MMM", Locale.getDefault());
        String parsedDate = parsedFormat.format(gt24hAgo);
        assertEquals("Check that any time more than 24 hours ago returns date in d MMM",
                parsedDate, DateUtils.parseDate(-gt24hAgo));
    }

    @Test
    public void parseCommentDate_beforeCutoff() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour() + 1;
        long timeBeforeCutoff = now.minusHours(hour).toEpochSecond(ZoneOffset.of("+8")) * 1000L;
        SimpleDateFormat parsedFormat = new SimpleDateFormat("d MMM", Locale.getDefault());
        String parsedDate = parsedFormat.format(timeBeforeCutoff);
        assertEquals("Check that any time before the cut off of 12am returns date in d MMM",
                parsedDate, DateUtils.parseCommentDate(-timeBeforeCutoff));
    }

    @Test
    public void parseCommentDate_afterCutoff() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        long timeBeforeCutoff = now.minusHours(hour).toEpochSecond(ZoneOffset.of("+8")) * 1000L;
        SimpleDateFormat parsedFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String parsedDate = parsedFormat.format(timeBeforeCutoff);
        assertEquals("Check that any time before the cut off of 12am returns date in d MMM",
                parsedDate, DateUtils.parseCommentDate(-timeBeforeCutoff));
    }

    @Test
    public void getMidnightOf_threeDaysLater() {
        LocalDate today = LocalDate.now();
        long midnight = today.plusDays(3).atTime(0,0,0)
                .toEpochSecond(ZoneOffset.of("+8")) * 1000L;
        assertEquals((long) midnight, (long) DateUtils.getMidnightOf(3));
    }

    @Test
    public void getMidnightOf_threeDaysAgo() {
        LocalDate today = LocalDate.now();
        long midnight = today.minusDays(3).atTime(0,0,0)
                .toEpochSecond(ZoneOffset.of("+8")) * 1000L;
        assertEquals((long) midnight, (long) DateUtils.getMidnightOf(-3));
    }

    @Test
    public void getMidnightOf_today() {
        LocalDate today = LocalDate.now();
        long midnight = today.atTime(0,0,0)
                .toEpochSecond(ZoneOffset.of("+8")) * 1000L;
        assertEquals((long) midnight, (long) DateUtils.getMidnightOf(0));
    }
}