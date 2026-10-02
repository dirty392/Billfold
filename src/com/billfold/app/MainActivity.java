package com.billfold.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_SAVE = 11, REQ_FILE = 12, REQ_NOTIFY = 13;
    private static final String NOTIFY_PERM = "android.permission.POST_NOTIFICATIONS";
    private WebView web;
    private String pendingText, pendingName;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingTab;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        pendingTab = tabFrom(getIntent());
        web = new WebView(this);
        web.setBackgroundColor(0x00000000);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        // The page itself never goes online; only the metal price check below (Java, one fixed address) does.
        s.setBlockNetworkLoads(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (pendingTab != null) { openTab(pendingTab); pendingTab = null; }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(i, REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        setContentView(web);
        if (saved != null) web.restoreState(saved);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String t = tabFrom(intent);
        if (t != null) openTab(t);
    }

    private static String tabFrom(Intent i) {
        String t = i == null ? null : i.getStringExtra("tab");
        return t != null && t.matches("[a-z]{2,8}") ? t : null;
    }

    private void openTab(String t) {
        js("window.__openTab && window.__openTab('" + t + "')");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.__back && window.__back()) ? 'y' : 'n'", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || !value.contains("y")) MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_FILE) {
            if (fileCallback != null) {
                Uri[] uris = null;
                if (result == RESULT_OK && data != null && data.getData() != null) uris = new Uri[] { data.getData() };
                fileCallback.onReceiveValue(uris);
                fileCallback = null;
            }
        } else if (req == REQ_SAVE) {
            boolean ok = false;
            if (result == RESULT_OK && data != null && data.getData() != null && pendingText != null) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(data.getData(), "wt");
                    os.write(pendingText.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    ok = true;
                } catch (Exception ignored) { }
            }
            js("window.__fileSaved && window.__fileSaved(" + ok + "," + JSONObject.quote(pendingName == null ? "" : pendingName) + ")");
            pendingText = null;
            pendingName = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req == REQ_NOTIFY) js("window.__notifyStatus && window.__notifyStatus()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) js("window.__notifyStatus && window.__notifyStatus()");
    }

    private void js(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() { web.evaluateJavascript(code, null); }
        });
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0 && buf.size() < 65536) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private String notifyStatus() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(NOTIFY_PERM) != PackageManager.PERMISSION_GRANTED) return "denied";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled() ? "granted" : "denied";
    }

    /** Methods the page calls as window.Android.*  */
    class Bridge {
        @JavascriptInterface
        public void setBars(final String bg, final String nav, final boolean light) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Window w = getWindow();
                        w.setStatusBarColor(Color.parseColor(bg));
                        w.setNavigationBarColor(Color.parseColor(nav));
                        w.getDecorView().setBackgroundColor(Color.parseColor(bg));
                        View d = w.getDecorView();
                        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        int cur = d.getSystemUiVisibility();
                        d.setSystemUiVisibility(light ? (cur | flags) : (cur & ~flags));
                    } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void saveFile(final String name, final String mime, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pendingText = text;
                    pendingName = name;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_TITLE, name);
                    try {
                        startActivityForResult(i, REQ_SAVE);
                    } catch (Exception e) {
                        js("window.__fileSaved && window.__fileSaved(false,'')");
                    }
                }
            });
        }

        @JavascriptInterface
        public void setReminders(String json) {
            getSharedPreferences(ReminderReceiver.PREFS, MODE_PRIVATE).edit().putString("rem", json).apply();
            ReminderReceiver.schedule(MainActivity.this);
        }

        @JavascriptInterface
        public void setWidgetData(String json) {
            Widgets.save(MainActivity.this, json);
        }

        /** Live metal prices, only called when the user turned them on in Settings. Sends only the metal symbol and currency. */
        @JavascriptInterface
        public void fetchPrices(final String symbols, final String currency) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONObject out = new JSONObject();
                    JSONObject prices = new JSONObject();
                    JSONArray failed = new JSONArray();
                    String cur = currency != null && currency.matches("[A-Z]{3}") ? currency : "USD";
                    try { out.put("cur", cur); } catch (Exception ignored) { }
                    String[] syms = symbols == null ? new String[0] : symbols.split(",");
                    for (int i = 0; i < syms.length && i < 8; i++) {
                        String sym = syms[i].trim();
                        if (!sym.matches("XAU|XAG|XPT|XPD|HG")) continue;
                        try {
                            JSONObject j = new JSONObject(get("https://api.gold-api.com/price/" + sym + "/" + cur));
                            double p = j.optDouble("price", 0);
                            if (!(p > 0)) throw new Exception("no price");
                            JSONObject q = new JSONObject();
                            q.put("price", p);
                            q.put("updatedAt", j.optString("updatedAt", ""));
                            q.put("currency", j.optString("currency", cur));
                            prices.put(sym, q);
                        } catch (Exception e) {
                            failed.put(sym);
                        }
                    }
                    try { out.put("prices", prices); out.put("failed", failed); } catch (Exception ignored) { }
                    js("window.__prices && window.__prices(" + JSONObject.quote(out.toString()) + ")");
                }
            }).start();
        }

        @JavascriptInterface
        public String notifyStatus() {
            return MainActivity.this.notifyStatus();
        }

        @JavascriptInterface
        public void requestNotify() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(NOTIFY_PERM) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[] { NOTIFY_PERM }, REQ_NOTIFY);
                    } else if (!"granted".equals(MainActivity.this.notifyStatus())) {
                        try {
                            Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                            i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                            startActivity(i);
                        } catch (Exception ignored) { }
                    }
                }
            });
        }
    }
}
