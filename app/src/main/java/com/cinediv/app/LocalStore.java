package com.cinediv.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalStore {
    private static final String PREFS = "cinediv_local_store";
    // New cache key prevents an older partial or test catalog from surviving the 1.2 upgrade.
    private static final String CACHE = "catalog_cache_v3";
    private static final String FAVORITES = "favorites";
    private static final String WATCHLIST = "watchlist";
    private static final String LAST_SYNC = "last_sync_v3";

    private final SharedPreferences preferences;

    public LocalStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveCache(String json) {
        preferences.edit().putString(CACHE, json).apply();
    }

    public String getCache() {
        return preferences.getString(CACHE, "");
    }

    public void setLastSync(long timestamp) {
        preferences.edit().putLong(LAST_SYNC, timestamp).apply();
    }

    public long getLastSync() {
        return preferences.getLong(LAST_SYNC, 0L);
    }

    public Map<String, MediaItem> getFavorites() {
        return readCollection(FAVORITES);
    }

    public Map<String, MediaItem> getWatchlist() {
        return readCollection(WATCHLIST);
    }

    public void saveFavorites(Map<String, MediaItem> items) {
        writeCollection(FAVORITES, items);
    }

    public void saveWatchlist(Map<String, MediaItem> items) {
        writeCollection(WATCHLIST, items);
    }

    private Map<String, MediaItem> readCollection(String key) {
        Map<String, MediaItem> result = new LinkedHashMap<>();
        String raw = preferences.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                MediaItem item = MediaItem.fromJson(array.getJSONObject(index));
                if (!item.name.isEmpty()) result.put(item.key(), item);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void writeCollection(String key, Map<String, MediaItem> items) {
        JSONArray array = new JSONArray();
        for (MediaItem item : items.values()) {
            try {
                array.put(item.toJson());
            } catch (Exception ignored) {
            }
        }
        preferences.edit().putString(key, array.toString()).apply();
    }

    public static List<MediaItem> values(Map<String, MediaItem> map) {
        return new ArrayList<>(map.values());
    }
}
