package com.example.tvwebview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
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
 * - Links and popups (target=_blank / window.open) open inside this same view.
 * - Cursor mode: D-pad moves an on-screen pointer, OK clicks, pushing against a
 *   screen edge scrolls.
 * - Menu: text size, site mode (default/desktop/mobile), click type (touch/mouse).
 */
public class WebActivity extends Activity {

    public static final String EXTRA_URL = "url";

    private static final String PREFS = "settings";
    private static final String KEY_CURSOR = "cursor_mode";
    private static final String KEY_ZOOM = "text_zoom";
    private static final String KEY_UA = "ua_mode";        // 0 default, 1 desktop, 2 mobile
    private static final String KEY_CLICK = "click_mode";  // 0 touch, 1 mouse
    private static final int[] ZOOMS = {100, 125, 150, 175, 200};
    private static final String[] UA_NAMES = {"Default", "Desktop", "Mobile"};
    private static final String[] CLICK_NAMES = {"Touch", "Mouse", "Script"};
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";
    private static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Mobile Safari/537.36";

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
    private int uaMode;
    private int clickMode;
    private float cx, cy, density;
    private long lastUserAction;   // time of last OK/click; popups right after it are allowed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_web);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        cursorMode = prefs.getBoolean(KEY_CURSOR, true);
        textZoom = prefs.getInt(KEY_ZOOM, 100);
        uaMode = prefs.getInt(KEY_UA, 0);
        clickMode = prefs.getInt(KEY_CLICK, 0);
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
        s.setSupportMultipleWindows(true);              // popups reach onCreateWindow
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if (scheme == null) return false;
                switch (scheme) {
                    case "http":
                    case "https":
                    case "javascript":
                    case "about":
                    case "blob":
                    case "data":
                        return false;   // handled inside this view
                    default:
                        return true;    // block intent:, market:, etc.
                }
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
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                boolean recent = SystemClock.uptimeMillis() - lastUserAction < 3000;
                if (!isUserGesture && !recent) {
                    Toast.makeText(WebActivity.this, "Blocked automatic popup",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                final WebView temp = new WebView(WebActivity.this);
                temp.getSettings().setJavaScriptEnabled(true);
                temp.getSettings().setDomStorageEnabled(true);
                temp.setWebViewClient(new WebViewClient() {
                    private boolean done;

                    private void adopt(String url) {
                        if (done || url == null || url.isEmpty() || url.startsWith("about:")) return;
                        done = true;
                        Toast.makeText(WebActivity.this, "Popup opened here", Toast.LENGTH_SHORT).show();
                        webView.loadUrl(url);          // open popup in the same view
                        temp.stopLoading();
                        webView.post(temp::destroy);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        adopt(r.getUrl().toString());
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, Bitmap favicon) {
                        adopt(url);
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
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
        String ua = null; // null = WebView default
        if (uaMode == 1) ua = DESKTOP_UA;
        else if (uaMode == 2) ua = MOBILE_UA;
        s.setUserAgentString(ua);
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
        int kc = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN
                && (kc == KeyEvent.KEYCODE_DPAD_CENTER || kc == KeyEvent.KEYCODE_ENTER
                || kc == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            lastUserAction = SystemClock.uptimeMillis();
        }
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
        if (clickMode == 1) sendHover();
    }

    private void click() {
        webView.requestFocus();
        lastUserAction = SystemClock.uptimeMillis();
        if (clickMode == 2) {
            scriptClick();
        } else {
            tapAtCursor();
        }
    }

    /** Real pointer events (touch for clickMode 0, mouse for clickMode 1). */
    private void tapAtCursor() {
        final long down = SystemClock.uptimeMillis();
        lastUserAction = down;
        if (clickMode == 1) sendHover();
        sendPointer(MotionEvent.ACTION_DOWN, down, down);
        webView.postDelayed(() -> {
            long now = SystemClock.uptimeMillis();
            lastUserAction = now;
            sendPointer(MotionEvent.ACTION_UP, down, now);
        }, 80);
    }

    /**
     * Clicks the page element under the cursor from inside the page (works for in-page
     * overlays/modals). Falls back to a real tap for iframes, which scripts can't reach.
     * Shows a short message naming the element that was hit.
     */
    @SuppressWarnings("deprecation")
    private void scriptClick() {
        float sc = webView.getScale();
        final float scale = sc <= 0 ? 1f : sc;
        final String js =
                "(function(x,y){var vv=window.visualViewport;"
                + "var px=x+(vv?vv.offsetLeft:0),py=y+(vv?vv.offsetTop:0);"
                + "var el=document.elementFromPoint(px,py);if(!el)return 'none';"
                + "if(el.tagName==='IFRAME')return 'IFRAME';"
                + "function f(t,C,b){try{el.dispatchEvent(new C(t,{bubbles:true,cancelable:true,"
                + "view:window,clientX:px,clientY:py,button:0,buttons:b}));}catch(e){}}"
                + "f('pointerdown',window.PointerEvent||MouseEvent,1);f('mousedown',MouseEvent,1);"
                + "try{el.focus();}catch(e){}"
                + "f('pointerup',window.PointerEvent||MouseEvent,0);f('mouseup',MouseEvent,0);"
                + "f('click',MouseEvent,0);"
                + "return el.tagName+(typeof el.className==='string'&&el.className"
                + "?'.'+el.className.split(' ')[0]:'');"
                + "})(" + (cx / scale) + "," + (cy / scale) + ")";
        webView.evaluateJavascript(js, value -> {
            if (value != null && value.contains("IFRAME")) {
                tapAtCursor();   // scripts can't click inside iframes; use a real tap
            } else {
                Toast.makeText(WebActivity.this, "Clicked: " + value, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Sends a touch tap (clickMode 0) or a primary mouse button press/release (clickMode 1). */
    private void sendPointer(int action, long downTime, long eventTime) {
        MotionEvent ev;
        if (clickMode == 1) {
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = 0;
            pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.x = cx;
            pc.y = cy;
            pc.pressure = 1f;
            pc.size = 1f;
            int buttons = action == MotionEvent.ACTION_UP ? 0 : MotionEvent.BUTTON_PRIMARY;
            ev = MotionEvent.obtain(downTime, eventTime, action, 1,
                    new MotionEvent.PointerProperties[]{pp},
                    new MotionEvent.PointerCoords[]{pc},
                    0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        } else {
            ev = MotionEvent.obtain(downTime, eventTime, action,
                    cx, cy, 1f, 1f, 0, 1f, 1f, 0, 0);
            ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        }
        webView.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private void sendHover() {
        long t = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, cx, cy, 0);
        ev.setSource(InputDevice.SOURCE_MOUSE);
        webView.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    // ---------- Menu ----------

    private void showMenu() {
        if (menuShowing()) return;
        String[] labels = {
                "Reload",
                "Cursor mode: " + (cursorMode ? "ON" : "OFF"),
                "Text size: " + textZoom + "%",
                "Site mode: " + UA_NAMES[uaMode],
                "Click type: " + CLICK_NAMES[clickMode],
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
                            uaMode = (uaMode + 1) % UA_NAMES.length;
                            prefs.edit().putInt(KEY_UA, uaMode).apply();
                            applySettings();
                            Toast.makeText(this, "Site mode: " + UA_NAMES[uaMode] + " (reloading)",
                                    Toast.LENGTH_SHORT).show();
                            webView.reload();
                            break;
                        case 4:
                            clickMode = (clickMode + 1) % CLICK_NAMES.length;
                            prefs.edit().putInt(KEY_CLICK, clickMode).apply();
                            Toast.makeText(this, "Click type: " + CLICK_NAMES[clickMode],
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case 5:
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
