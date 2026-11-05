package com.example.tvwebview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * In-app browser for TV.
 * - Pages render exactly as the server delivers them (real Chromium WebView).
 * - All http/https links (incl. target=_blank / window.open) stay in this view.
 * - Cursor mode: D-pad moves an on-screen pointer, OK clicks, pushing against a
 *   screen edge scrolls. Turn it off in the menu to use native focus navigation.
 * - Menu: text size, desktop/mobile site, reload.
 */
public class WebActivity extends Activity {

    public static final String EXTRA_URL = "url";

    private static final String PREFS = "settings";
    private static final String KEY_CURSOR = "cursor_mode";
    private static final String KEY_ZOOM = "text_zoom";
    private static final String KEY_DESKTOP = "desktop_site";
    private static final int[] ZOOMS = {100, 125, 150, 175, 200};
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private WebView webView;
    private ProgressBar progress;
    private FrameLayout root;
    private CursorView cursor;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private AlertDialog menuDialog;
    private SharedPreferences prefs;

    private boolean cursorMode;
    private int textZoom;
    private boolean desktop;
    private float cx, cy, density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_web);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        cursorMode = prefs.getBoolean(KEY_CURSOR, true);
        textZoom = prefs.getInt(KEY_ZOOM, 100);
        desktop = prefs.getBoolean(KEY_DESKTOP, false);
        density = getResources().getDisplayMetrics().density;

        root = findViewById(R.id.root);
        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);
        cursor = findViewById(R.id.cursor);

        setupWebView();
        applySettings();
        updateCursorVisibility();
        root.post(() -> {
            cx = root.getWidth() / 2f;
            cy = root.getHeight() / 2f;
            cursor.setPos(cx, cy);
        });

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        if (cursorMode) {
            Toast.makeText(this, "D-pad moves cursor, OK clicks. MENU for options.",
                    Toast.LENGTH_LONG).show();
        }
        webView.loadUrl(url);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equals(scheme) || "https".equals(scheme));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
                updateCursorVisibility();
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });
    }

    private void applySettings() {
        WebSettings s = webView.getSettings();
        s.setTextZoom(textZoom);
        s.setUserAgentString(desktop ? DESKTOP_UA : null); // null = default UA
    }

    private void updateCursorVisibility() {
        cursor.setVisibility(cursorMode && customView == null ? View.VISIBLE : View.GONE);
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        webView.setVisibility(View.VISIBLE);
        webView.requestFocus();
        updateCursorVisibility();
    }

    // ---------- Cursor handling ----------

    private boolean menuShowing() {
        return menuDialog != null && menuDialog.isShowing();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (cursorMode && customView == null && !menuShowing() && handleCursorKey(e)) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private boolean handleCursorKey(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (e.getAction() == KeyEvent.ACTION_DOWN) moveCursor(code, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) click();
                return true;
        }
        return false;
    }

    private void moveCursor(int code, int repeat) {
        float step = (10 + Math.min(repeat, 25) * 3) * density;
        float dx = 0, dy = 0;
        if (code == KeyEvent.KEYCODE_DPAD_UP) dy = -step;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dy = step;
        else if (code == KeyEvent.KEYCODE_DPAD_LEFT) dx = -step;
        else dx = step;

        float w = root.getWidth(), h = root.getHeight();
        float edge = 40 * density;

        // Pushing against an edge scrolls the page instead of moving the cursor.
        if (dy < 0 && cy <= edge) { webView.scrollBy(0, (int) (dy * 2)); dy = 0; }
        if (dy > 0 && cy >= h - edge) { webView.scrollBy(0, (int) (dy * 2)); dy = 0; }
        if (dx < 0 && cx <= edge) { webView.scrollBy((int) (dx * 2), 0); dx = 0; }
        if (dx > 0 && cx >= w - edge) { webView.scrollBy((int) (dx * 2), 0); dx = 0; }

        cx = Math.max(0, Math.min(w - 1, cx + dx));
        cy = Math.max(0, Math.min(h - 1, cy + dy));
        cursor.setPos(cx, cy);
    }

    private void click() {
        webView.requestFocus();
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0);
        webView.dispatchTouchEvent(down);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cx, cy, 0);
        webView.dispatchTouchEvent(up);
        up.recycle();
    }

    // ---------- Menu ----------

    private void showMenu() {
        if (menuShowing()) return;
        String[] labels = {
                "Reload",
                "Cursor mode: " + (cursorMode ? "ON" : "OFF"),
                "Text size: " + textZoom + "%",
                "Desktop site: " + (desktop ? "ON" : "OFF"),
                "Back to catalog"
        };
        menuDialog = new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setItems(labels, (d, which) -> {
                    switch (which) {
                        case 0:
                            webView.reload();
                            break;
                        case 1:
                            cursorMode = !cursorMode;
                            prefs.edit().putBoolean(KEY_CURSOR, cursorMode).apply();
                            updateCursorVisibility();
                            break;
                        case 2:
                            int idx = 0;
                            for (int i = 0; i < ZOOMS.length; i++) if (ZOOMS[i] == textZoom) idx = i;
                            textZoom = ZOOMS[(idx + 1) % ZOOMS.length];
                            prefs.edit().putInt(KEY_ZOOM, textZoom).apply();
                            applySettings();
                            Toast.makeText(this, "Text size " + textZoom + "%", Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            desktop = !desktop;
                            prefs.edit().putBoolean(KEY_DESKTOP, desktop).apply();
                            applySettings();
                            webView.reload();
                            break;
                        case 4:
                            finish();
                            break;
                    }
                })
                .create();
        menuDialog.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_PROG_RED:
                showMenu();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (customView != null) hideCustomView();
                else if (webView.canGoBack()) webView.goBack();
                else finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
