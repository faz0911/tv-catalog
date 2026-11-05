package com.example.tvwebview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal async image loader with memory cache (no external libraries). */
public class ImageLoader {
    private static final int TARGET_WIDTH = 640;

    private final LruCache<String, Bitmap> cache;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    public ImageLoader() {
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String key, Bitmap b) {
                return b.getByteCount() / 1024;
            }
        };
    }

    public void load(final String url, final ImageView view) {
        view.setTag(url);
        Bitmap cached = url.isEmpty() ? null : cache.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.placeholder);
        if (url.isEmpty()) return;
        pool.execute(() -> {
            final Bitmap bmp = download(url);
            if (bmp == null) return;
            cache.put(url, bmp);
            main.post(() -> {
                if (url.equals(view.getTag())) view.setImageBitmap(bmp);
            });
        });
    }

    private Bitmap download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            byte[] data = out.toByteArray();

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
