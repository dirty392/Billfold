package com.billfold.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Posts bill reminders, and re-schedules them after a reboot or app update. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String PREFS = "billfold";
    static final String ACTION = "com.billfold.app.REMIND";
    static final String CHANNEL = "bills";

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION.equals(i.getAction())) {
            show(c, i.getStringExtra("title"), i.getStringExtra("text"));
        } else {
            schedule(c);
        }
    }

    static void schedule(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int old = p.getInt("remCount", 0);
        for (int k = 0; k < old; k++) am.cancel(pending(c, k, null, null));
        JSONArray a;
        try { a = new JSONArray(p.getString("rem", "[]")); } catch (Exception e) { a = new JSONArray(); }
        long now = System.currentTimeMillis();
        int n = 0;
        for (int k = 0; k < a.length() && n < 100; k++) {
            JSONObject o = a.optJSONObject(k);
            if (o == null) continue;
            long t = o.optLong("t");
            if (t <= now) continue;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pending(c, n, o.optString("title"), o.optString("text")));
            n++;
        }
        p.edit().putInt("remCount", n).apply();
    }

    private static PendingIntent pending(Context c, int n, String title, String text) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION);
        if (title != null) {
            i.putExtra("title", title);
            i.putExtra("text", text);
        }
        return PendingIntent.getBroadcast(c, 1000 + n, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void show(Context c, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || title == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Reminders before your bills are due");
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note = new Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF0B6B52)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build();
        nm.notify((int) (System.currentTimeMillis() % 1000000), note);
    }
}
