package me.D4rkSn0w.motd.core;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class Countdown {

    private static final long WEEK = 604800;
    private static final long DAY = 86400;
    private static final long HOUR = 3600;
    private static final long MINUTE = 60;

    private Countdown() {
    }

    public static long secondsLeft(String target) {
        LocalDateTime time = parse(target);
        if (time == null) {
            return Long.MIN_VALUE;
        }
        long seconds = Duration.between(LocalDateTime.now(ZoneId.systemDefault()), time).getSeconds();
        return seconds < 0 ? 0 : seconds;
    }

    public static long parseDuration(String text) {
        if (text == null) {
            return Long.MIN_VALUE;
        }
        String clean = text.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        if (clean.isEmpty()) {
            return Long.MIN_VALUE;
        }

        long total = 0;
        long digits = 0;
        final long cap = 100L * 365L * 86400L;
        boolean any = false;
        boolean sawUnit = false;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c >= '0' && c <= '9') {
                digits = digits * 10 + (c - '0');
                if (digits > cap) {
                    digits = cap;
                }
                any = true;
                continue;
            }
            if (!any) {
                return Long.MIN_VALUE;
            }
            long factor;
            switch (c) {
                case 'w': factor = WEEK; break;
                case 'd': factor = DAY; break;
                case 'h': factor = HOUR; break;
                case 'm': factor = MINUTE; break;
                case 's': factor = 1; break;
                default: return Long.MIN_VALUE;
            }
            total += digits * factor;
            if (total > cap || total < 0) {
                total = cap;
            }
            digits = 0;
            any = false;
            sawUnit = true;
        }

        if (any) {
            total += digits;
            if (total > cap || total < 0) {
                total = cap;
            }
        } else if (!sawUnit) {
            return Long.MIN_VALUE;
        }
        return total;
    }

    public static LocalDateTime parse(String target) {
        if (target == null) {
            return null;
        }
        String clean = target.trim().replace('T', ' ');
        if (clean.isEmpty()) {
            return null;
        }
        String[] halves = clean.split("\\s+");
        String[] date = halves[0].split("-");
        if (date.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(date[0]);
            int month = Integer.parseInt(date[1]);
            int day = Integer.parseInt(date[2]);

            int hour = 0;
            int minute = 0;
            int second = 0;
            if (halves.length > 1) {
                String[] clock = halves[1].split(":");
                hour = clock.length > 0 ? Integer.parseInt(clock[0]) : 0;
                minute = clock.length > 1 ? Integer.parseInt(clock[1]) : 0;
                second = clock.length > 2 ? Integer.parseInt(clock[2]) : 0;
            }
            return LocalDate.of(year, month, day).atTime(hour, minute, second);
        } catch (Throwable error) {
            return null;
        }
    }

    public static String format(long seconds, String pattern) {
        long rest = seconds;

        long weeks = 0;
        long days = 0;
        long hours = 0;
        long minutes = 0;

        if (uses(pattern, "wochen")) {
            weeks = rest / WEEK;
            rest -= weeks * WEEK;
        }
        if (uses(pattern, "tage")) {
            days = rest / DAY;
            rest -= days * DAY;
        }
        if (uses(pattern, "stunden")) {
            hours = rest / HOUR;
            rest -= hours * HOUR;
        }
        if (uses(pattern, "minuten")) {
            minutes = rest / MINUTE;
            rest -= minutes * MINUTE;
        }
        long secs = uses(pattern, "sekunden") ? rest : 0;

        return pattern
                .replace("%wochen2%", pad(weeks))
                .replace("%tage2%", pad(days))
                .replace("%stunden2%", pad(hours))
                .replace("%minuten2%", pad(minutes))
                .replace("%sekunden2%", pad(secs))
                .replace("%wochen%", String.valueOf(weeks))
                .replace("%tage%", String.valueOf(days))
                .replace("%stunden%", String.valueOf(hours))
                .replace("%minuten%", String.valueOf(minutes))
                .replace("%sekunden%", String.valueOf(secs));
    }

    private static boolean uses(String pattern, String unit) {
        return pattern.contains("%" + unit + "%") || pattern.contains("%" + unit + "2%");
    }

    private static String pad(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
