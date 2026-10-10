package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.text.format.DateUtils;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.DateFormatter;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class PodcastFormat {
    private static final int MS_PER_MINUTE = 60 * 1000;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int DAYS_AS_WEEKDAY = 6;

    private PodcastFormat() {
    }

    public static String dayLabel(Context context, Date date) {
        if (date == null) {
            return "";
        }
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        Calendar now = Calendar.getInstance();
        int diff = dayNumber(now) - dayNumber(target);
        if (diff == 0) {
            return context.getString(R.string.podcast_today);
        } else if (diff == 1) {
            return context.getString(R.string.podcast_yesterday);
        } else if (diff > 1 && diff <= DAYS_AS_WEEKDAY) {
            return target.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
        }
        return DateFormatter.formatAbbrev(context, date);
    }

    public static String duration(Context context, long durationMs) {
        long totalMinutes = Math.max(1, Math.round((double) durationMs / MS_PER_MINUTE));
        if (totalMinutes >= MINUTES_PER_HOUR) {
            return context.getString(R.string.podcast_hours_min,
                    (int) (totalMinutes / MINUTES_PER_HOUR), (int) (totalMinutes % MINUTES_PER_HOUR));
        }
        return context.getString(R.string.podcast_min, (int) totalMinutes);
    }

    public static String timeLeft(Context context, int durationMs, int positionMs) {
        if (durationMs <= 0) {
            return "";
        }
        if (positionMs <= 0) {
            return duration(context, durationMs);
        }
        long leftMinutes = Math.max(1, Math.round((double) (durationMs - positionMs) / MS_PER_MINUTE));
        if (leftMinutes >= MINUTES_PER_HOUR) {
            return context.getString(R.string.podcast_hours_min,
                    (int) (leftMinutes / MINUTES_PER_HOUR), (int) (leftMinutes % MINUTES_PER_HOUR))
                    + " " + context.getString(R.string.podcast_left_suffix);
        }
        return context.getString(R.string.podcast_min_left, (int) leftMinutes);
    }

    private static int dayNumber(Calendar calendar) {
        Calendar copy = (Calendar) calendar.clone();
        copy.set(Calendar.HOUR_OF_DAY, 12);
        long offset = copy.get(Calendar.ZONE_OFFSET) + copy.get(Calendar.DST_OFFSET);
        return (int) ((copy.getTimeInMillis() + offset) / DateUtils.DAY_IN_MILLIS);
    }
}
