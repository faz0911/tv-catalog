package com.example.tvwebview;

/** One card in the catalog. */
public class Item {
    public final String title;
    public final String subtitle;
    public final String tag;
    public final String image;
    public final String url;

    public Item(String title, String subtitle, String tag, String image, String url) {
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.tag = tag == null ? "" : tag;
        this.image = image == null ? "" : image;
        this.url = url == null ? "" : url;
    }
}
