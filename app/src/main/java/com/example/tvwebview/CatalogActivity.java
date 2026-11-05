package com.example.tvwebview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV card-grid catalog. Cards come from a JSON feed whose URL the user sets
 * in the menu. Selecting a card opens its link inside the app (WebActivity).
 */
public class CatalogActivity extends Activity {

    private static final String PREFS = "settings";
    private static final String KEY_FEED = "feed_url";
    private static final int COLUMNS = 3;   // change here for a different grid width

    // >>> PUT YOUR FEED URL HERE (must return JSON). If not empty, it ALWAYS overrides
    // anything saved in the app. Leave "" to use the URL set from the Menu instead.
    private static final String HARDCODED_FEED =
            "https://raw.githubusercontent.com/faz0911/tv-catalog/main/sample-feed.json";

    private String currentFeed() {
        if (!HARDCODED_FEED.isEmpty()) return HARDCODED_FEED;
        return prefs.getString(KEY_FEED, "");
    }

    private interface UrlCallback { void onUrl(String url); }

    private final List<Item> items = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private CardAdapter adapter;
    private GridView grid;
    private TextView status, headerTitle;
    private AlertDialog menuDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        grid = findViewById(R.id.grid);
        status = findViewById(R.id.status);
        headerTitle = findViewById(R.id.header_title);
        Button menuBtn = findViewById(R.id.btn_menu);

        grid.setNumColumns(COLUMNS);
        adapter = new CardAdapter(this, items, new ImageLoader());
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> openItem(items.get(pos)));
        menuBtn.setOnClickListener(v -> showMenu());

        refresh();
        grid.requestFocus();
    }

    private void showPlaceholders(String message) {
        items.clear();
        for (int i = 0; i < 6; i++) {
            items.add(new Item("Title", "", "", "", ""));
        }
        adapter.notifyDataSetChanged();
        setStatus(message);
    }

    private void setStatus(String msg) {
        if (msg == null || msg.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setText(msg);
            status.setVisibility(View.VISIBLE);
        }
    }

    private void refresh() {
        final String feedUrl = currentFeed();
        if (feedUrl.isEmpty()) {
            headerTitle.setText("Catalog");
            showPlaceholders("No feed set. Open Menu > Set feed URL.");
            return;
        }
        setStatus("Loading…");
        io.execute(() -> {
            try {
                final Feed feed = Feed.fetch(feedUrl);
                main.post(() -> {
                    headerTitle.setText(feed.title == null || feed.title.isEmpty() ? "Catalog" : feed.title);
                    items.clear();
                    items.addAll(feed.items);
                    adapter.notifyDataSetChanged();
                    setStatus(feed.items.isEmpty() ? "Feed is empty." : "");
                    grid.requestFocus();
                });
            } catch (Exception e) {
                main.post(() -> showPlaceholders("Could not load feed: " + e.getMessage()));
            }
        });
    }

    private void openItem(Item it) {
        if (it.url.isEmpty()) {
            Toast.makeText(this, "This card has no link", Toast.LENGTH_SHORT).show();
            return;
        }
        openWeb(it.url);
    }

    private void openWeb(String url) {
        Intent i = new Intent(this, WebActivity.class);
        i.putExtra(WebActivity.EXTRA_URL, url);
        startActivity(i);
    }

    private static String normalize(String input) {
        String u = input.trim();
        if (u.isEmpty()) return "";
        if (!u.contains("://")) u = "https://" + u;
        return u;
    }

    private void askUrl(String title, String prefill, final UrlCallback cb) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setHint("https://example.com");
        input.setText(prefill);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String url = normalize(input.getText().toString());
                    if (!url.isEmpty() && Uri.parse(url).getHost() == null) {
                        Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    cb.onUrl(url);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMenu() {
        if (menuDialog != null && menuDialog.isShowing()) return;
        String[] labels = {"Set feed URL", "Refresh", "Open a web page by URL", "Exit app"};
        menuDialog = new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setItems(labels, (d, which) -> {
                    switch (which) {
                        case 0:
                            askUrl("Feed URL (JSON)", currentFeed(), url -> {
                                prefs.edit().putString(KEY_FEED, url).apply();
                                refresh();
                            });
                            break;
                        case 1:
                            refresh();
                            break;
                        case 2:
                            askUrl("Open web page", "", url -> {
                                if (!url.isEmpty()) openWeb(url);
                            });
                            break;
                        case 3:
                            finish();
                            break;
                    }
                })
                .create();
        menuDialog.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_SETTINGS
                || keyCode == KeyEvent.KEYCODE_PROG_RED) {
            showMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
