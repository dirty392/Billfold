package com.billfold.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Locale;

/** Draws the three home-screen widgets from the snapshot the app saves (see Bridge.setWidgetData). */
final class Widgets {
    static final int UPCOMING = 0, MONTH = 1, DEBT = 2;
    private static final int[] ROWS = { R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4 };
    private static final int[] DOTS = { R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4 };
    private static final int[] NAMES = { R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4 };
    private static final int[] WHENS = { R.id.when0, R.id.when1, R.id.when2, R.id.when3, R.id.when4 };
    private static final int[] AMTS = { R.id.amt0, R.id.amt1, R.id.amt2, R.id.amt3, R.id.amt4 };

    private Widgets() { }

    static void save(Context c, String json) {
        c.getSharedPreferences(ReminderReceiver.PREFS, Context.MODE_PRIVATE).edit().putString("widget", json).apply();
        updateAll(c);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        update(c, m, m.getAppWidgetIds(new ComponentName(c, WidgetUpcoming.class)), UPCOMING);
        update(c, m, m.getAppWidgetIds(new ComponentName(c, WidgetMonth.class)), MONTH);
        update(c, m, m.getAppWidgetIds(new ComponentName(c, WidgetDebt.class)), DEBT);
    }

    static void update(Context c, AppWidgetManager m, int[] ids, int type) {
        if (ids == null || ids.length == 0) return;
        JSONObject data = load(c);
        for (int id : ids) {
            RemoteViews v;
            try {
                v = type == UPCOMING ? upcoming(c, data) : type == MONTH ? month(c, data) : debt(c, data);
            } catch (Exception e) {
                v = new RemoteViews(c.getPackageName(), type == UPCOMING ? R.layout.widget_upcoming : type == MONTH ? R.layout.widget_month : R.layout.widget_debt);
            }
            m.updateAppWidget(id, v);
        }
    }

    private static JSONObject load(Context c) {
        try {
            String s = c.getSharedPreferences(ReminderReceiver.PREFS, Context.MODE_PRIVATE).getString("widget", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int accent(Context c, JSONObject d) {
        try {
            return Color.parseColor(d.getString(night(c) ? "accentD" : "accentL"));
        } catch (Exception e) {
            return c.getColor(R.color.w_accent);
        }
    }

    private static NumberFormat money(JSONObject d, boolean cents) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.US);
        try { nf.setCurrency(Currency.getInstance(d.optString("cur", "USD"))); } catch (Exception ignored) { }
        if (!cents) { nf.setMaximumFractionDigits(0); nf.setMinimumFractionDigits(0); }
        return nf;
    }

    private static PendingIntent open(Context c, String tab, int code) {
        Intent i = new Intent(c, MainActivity.class).putExtra("tab", tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String shortDate(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.getDayOfMonth();
    }

    private static RemoteViews upcoming(Context c, JSONObject d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_upcoming);
        v.setOnClickPendingIntent(R.id.root, open(c, "list", 21));
        for (int r : ROWS) v.setViewVisibility(r, View.GONE);
        if (d == null) {
            v.setViewVisibility(R.id.empty, View.VISIBLE);
            v.setTextViewText(R.id.empty, "Open Billfold once to load your bills.");
            v.setTextViewText(R.id.week, "");
            return v;
        }
        int acc = accent(c, d);
        v.setTextColor(R.id.title, acc);
        NumberFormat nf = money(d, true), nw = money(d, false);
        LocalDate today = LocalDate.now();
        JSONArray up = d.optJSONArray("upcoming");
        double week = 0;
        int shown = 0;
        boolean isNight = night(c);
        int danger = c.getColor(R.color.w_danger), fg2 = c.getColor(R.color.w_fg2);
        for (int i = 0; up != null && i < up.length(); i++) {
            JSONObject o = up.optJSONObject(i);
            if (o == null) continue;
            LocalDate when;
            try { when = LocalDate.parse(o.optString("d")); } catch (Exception e) { continue; }
            long days = ChronoUnit.DAYS.between(today, when);
            if (days < -45) continue;
            double amt = o.optDouble("a", 0);
            if (days >= 0 && days <= 6) week += amt;
            if (shown >= ROWS.length) continue;
            String label;
            boolean late = days < 0;
            if (late) label = "Overdue · " + shortDate(when);
            else if (days == 0) label = "Due today";
            else if (days == 1) label = "Due tomorrow";
            else if (days <= 6) label = when.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US) + ", " + shortDate(when);
            else label = shortDate(when);
            if (o.optBoolean("auto")) label += " · Autopay";
            v.setViewVisibility(ROWS[shown], View.VISIBLE);
            v.setTextViewText(NAMES[shown], o.optString("n"));
            v.setTextViewText(WHENS[shown], label);
            v.setTextColor(WHENS[shown], late ? danger : fg2);
            v.setTextViewText(AMTS[shown], (o.optBoolean("est") ? "~" : "") + nf.format(amt));
            int dot;
            try { dot = Color.parseColor(o.optString(isNight ? "cd" : "cl")); } catch (Exception e) { dot = acc; }
            v.setInt(DOTS[shown], "setColorFilter", dot);
            shown++;
        }
        v.setTextViewText(R.id.week, week > 0 ? nw.format(Math.ceil(week)) + " due this week" : "");
        if (shown == 0) {
            v.setViewVisibility(R.id.empty, View.VISIBLE);
            v.setTextViewText(R.id.empty, "Nothing due. You're all caught up.");
        } else {
            v.setViewVisibility(R.id.empty, View.GONE);
        }
        return v;
    }

    private static RemoteViews month(Context c, JSONObject d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_month);
        v.setOnClickPendingIntent(R.id.root, open(c, "sum", 22));
        if (d == null) {
            v.setTextViewText(R.id.left, "—");
            v.setTextViewText(R.id.paid, "Open Billfold once to load this month.");
            return v;
        }
        int acc = accent(c, d);
        v.setTextColor(R.id.title, acc);
        LocalDate today = LocalDate.now();
        String ym = String.format(Locale.US, "%04d-%02d", today.getYear(), today.getMonthValue());
        JSONArray months = d.optJSONArray("months");
        JSONObject mo = null;
        for (int i = 0; months != null && i < months.length(); i++) {
            JSONObject o = months.optJSONObject(i);
            if (o != null && ym.equals(o.optString("ym"))) { mo = o; break; }
        }
        if (mo == null) {
            v.setTextViewText(R.id.left, "—");
            v.setTextViewText(R.id.paid, "Open Billfold to update this month.");
            return v;
        }
        NumberFormat nw = money(d, false);
        double left = mo.optDouble("left", 0);
        v.setTextViewText(R.id.title, today.getMonth().getDisplayName(TextStyle.FULL, Locale.US).toUpperCase(Locale.US));
        v.setTextViewText(R.id.left, nw.format(left < 0 ? Math.ceil(left) : Math.floor(left)));
        v.setTextColor(R.id.left, left < 0 ? c.getColor(R.color.w_danger) : c.getColor(R.color.w_fg));
        v.setTextViewText(R.id.income, nw.format(mo.optDouble("income", 0)));
        v.setTextViewText(R.id.bills, nw.format(Math.ceil(mo.optDouble("bills", 0))));
        v.setTextViewText(R.id.save, nw.format(mo.optDouble("save", 0)));
        int nb = mo.optInt("nb"), np = mo.optInt("np");
        v.setProgressBar(R.id.bar, 100, nb > 0 ? Math.round(np * 100f / nb) : 0, false);
        v.setTextViewText(R.id.paid, nb > 0 ? np + " of " + nb + " bills paid" : "No bills this month");
        return v;
    }

    private static RemoteViews debt(Context c, JSONObject d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_debt);
        v.setOnClickPendingIntent(R.id.root, open(c, "debt", 23));
        if (d == null) {
            v.setTextViewText(R.id.left, "—");
            v.setTextViewText(R.id.free, "Open Billfold once to load your debts.");
            v.setTextViewText(R.id.sub, "");
            return v;
        }
        v.setTextColor(R.id.title, accent(c, d));
        JSONObject db = d.optJSONObject("debt");
        NumberFormat nw = money(d, false);
        if (db == null || db.optInt("count") == 0) {
            v.setTextViewText(R.id.left, nw.format(0));
            v.setProgressBar(R.id.bar, 100, 0, false);
            v.setTextViewText(R.id.free, "No debts added");
            v.setTextViewText(R.id.sub, "Tap to add one in the Debt tab");
            return v;
        }
        double left = db.optDouble("left", 0), paid = db.optDouble("paid", 0);
        int count = db.optInt("count"), open = db.optInt("open");
        v.setTextViewText(R.id.left, nw.format(Math.ceil(left)));
        v.setProgressBar(R.id.bar, 100, paid + left > 0 ? (int) Math.round(paid * 100 / (paid + left)) : 100, false);
        String freeBy = db.optString("freeBy", "");
        v.setTextViewText(R.id.free, open == 0 ? "You're debt-free!" : freeBy.length() > 0 ? "Debt-free by " + freeBy : "Link payments to see your debt-free date");
        v.setTextViewText(R.id.sub, nw.format(paid) + " paid · " + count + (count == 1 ? " account" : " accounts"));
        return v;
    }
}
