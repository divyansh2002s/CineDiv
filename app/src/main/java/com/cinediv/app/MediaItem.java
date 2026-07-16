package com.cinediv.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

public final class MediaItem {
    public final String name;
    public final String year;
    public final String type;
    public boolean watched;

    public MediaItem(String name, String year, String type) {
        this(name, year, type, false);
    }

    public MediaItem(String name, String year, String type, boolean watched) {
        this.name = name == null ? "" : name.trim();
        this.year = year == null ? "" : year.trim();
        this.type = "series".equalsIgnoreCase(type) ? "series" : "movie";
        this.watched = watched;
    }

    public String key() {
        return type + "|" + name.toLowerCase(Locale.ROOT) + "|" + year;
    }

    public int numericYear() {
        try {
            return Integer.parseInt(year);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("name", name);
        object.put("year", year);
        object.put("type", type);
        object.put("watched", watched);
        return object;
    }

    public static MediaItem fromJson(JSONObject object) {
        return new MediaItem(
                object.optString("name"),
                object.optString("year"),
                object.optString("type", "movie"),
                object.optBoolean("watched", false)
        );
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MediaItem && key().equals(((MediaItem) other).key());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key());
    }
}
