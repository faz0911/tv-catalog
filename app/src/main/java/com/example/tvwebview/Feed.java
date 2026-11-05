package com.example.tvwebview;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Downloads and parses the JSON feed. */
public class Feed {
    public String title;
    public final List<Item> items = new ArrayList<>();

    public static Feed fetch(String feedUrl) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(feedUrl).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 2 * 1024 * 1024) throw new Exception("Feed too large");
                out.write(buf, 0, n);
            }
            return parse(out.toString("UTF-8"), feedUrl);
        } finally {
            c.disconnect();
        }
    }

    static Feed parse(String json, String base) throws Exception {
        Feed f = new Feed();
        String t = json.trim();
        JSONArray arr;
        if (t.startsWith("[")) {
            arr = new JSONArray(t);
        } else {
            JSONObject o = new JSONObject(t);
            f.title = o.optString("title", null);
            arr = o.optJSONArray("items");
            if (arr == null) arr = new JSONArray();
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            f.items.add(new Item(
                    o.optString("title", ""),
                    o.optString("subtitle", ""),
                    o.optString("tag", ""),
                    resolve(base, o.optString("image", "")),
                    resolve(base, o.optString("url", ""))));
        }
        return f;
    }

    private static String resolve(String base, String ref) {
        if (ref == null || ref.isEmpty()) return "";
        try {
            return new URI(base).resolve(ref).toString();
        } catch (Exception e) {
            return ref;
        }
    }
}
