package com.example.tvwebview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class CardAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Item> items;
    private final ImageLoader loader;

    public CardAdapter(Context ctx, List<Item> items, ImageLoader loader) {
        this.inflater = LayoutInflater.from(ctx);
        this.items = items;
        this.loader = loader;
    }

    @Override public int getCount() { return items.size(); }
    @Override public Item getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }

    private static class Holder {
        ImageView image;
        TextView title, subtitle, tag;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_card, parent, false);
            h = new Holder();
            h.image = convertView.findViewById(R.id.card_image);
            h.title = convertView.findViewById(R.id.card_title);
            h.subtitle = convertView.findViewById(R.id.card_subtitle);
            h.tag = convertView.findViewById(R.id.card_tag);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }
        Item it = getItem(position);
        h.title.setText(it.title);
        setOrHide(h.subtitle, it.subtitle);
        setOrHide(h.tag, it.tag);
        loader.load(it.image, h.image);
        return convertView;
    }

    private static void setOrHide(TextView v, String text) {
        if (text.isEmpty()) {
            v.setVisibility(View.GONE);
        } else {
            v.setText(text);
            v.setVisibility(View.VISIBLE);
        }
    }
}
