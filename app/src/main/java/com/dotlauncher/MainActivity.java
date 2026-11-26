package com.dotlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Главный экран: показывает assets/index.html поверх обоев телефона. */
public class MainActivity extends Activity {
    private WebView web;
    private volatile int version = 0;
    private volatile String cache = null;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            cache = null;
            version++;
            js("window.onResumeApp&&onResumeApp()");
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setVerticalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        js("window.onResumeApp&&onResumeApp()");
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        js("window.onHome&&onHome()");
    }

    @Override public void onBackPressed() {
        js("window.onBack&&onBack()");
    }

    private void js(final String code) {
        if (web == null) return;
        web.post(() -> web.evaluateJavascript(code, null));
    }

    private void start(Intent i) {
        runOnUiThread(() -> {
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
        });
    }

    private String buildApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        JSONArray arr = new JSONArray();
        int size = 120;
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("n", String.valueOf(ri.loadLabel(pm)));
                o.put("p", pkg);
                o.put("c", ri.activityInfo.name);
                Drawable d = ri.loadIcon(pm);
                Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(b);
                d.setBounds(0, 0, size, size);
                d.draw(cv);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
                b.recycle();
                o.put("i", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /** Методы, доступные странице как window.Android.* */
    public class Bridge {
        @JavascriptInterface public int version() { return version; }

        @JavascriptInterface public String apps() {
            String c = cache;
            if (c == null) { c = buildApps(); cache = c; }
            return c;
        }

        @JavascriptInterface public void launch(String pkg, String cls) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(pkg, cls)
                    .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            start(i);
        }

        @JavascriptInterface public void info(String pkg) {
            start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        }

        @JavascriptInterface public void uninstall(String pkg) {
            start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
        }

        @JavascriptInterface public void wallpaper() {
            start(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Обои"));
        }

        @JavascriptInterface public void settings() {
            start(new Intent(Settings.ACTION_SETTINGS));
        }

        @JavascriptInterface public void homeSettings() {
            start(new Intent(Settings.ACTION_HOME_SETTINGS));
        }

        @JavascriptInterface public int battery() {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            return bm == null ? 0 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
    }
}
