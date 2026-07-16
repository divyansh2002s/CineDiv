package com.cinediv.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Read-only catalog client. It loads every row from the Movies and Shows tabs. */
public final class CineDivCatalogClient {
    private CineDivCatalogClient() {}

    public static String fetchCatalog(
            String sheetId,
            String moviesGid,
            String showsGid,
            String fallbackApiUrl
    ) throws Exception {
        Exception directError = null;

        try {
            JSONArray movies = fetchSheetTab(sheetId, moviesGid);
            JSONArray series = fetchSheetTab(sheetId, showsGid);

            // An unexpectedly empty response is usually a sharing/login HTML page, not a real catalog.
            if (movies.length() == 0 && series.length() == 0) {
                throw new Exception("Google Sheet returned no catalog rows");
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("source", "google-sheet-csv");
            result.put("movies", movies);
            result.put("series", series);
            return result.toString();
        } catch (Exception error) {
            directError = error;
        }

        // Fallback keeps the app usable even when direct CSV export is temporarily unavailable.
        if (fallbackApiUrl != null && !fallbackApiUrl.trim().isEmpty()) {
            try {
                String separator = fallbackApiUrl.contains("?") ? "&" : "?";
                String raw = get(fallbackApiUrl + separator + "cacheBust=" + System.currentTimeMillis());
                JSONObject object = new JSONObject(raw);
                if (!object.optBoolean("success", false)) {
                    throw new Exception(object.optString("message", "Catalog API returned an error"));
                }
                if (object.optJSONArray("movies") == null || object.optJSONArray("series") == null) {
                    throw new Exception("Catalog API response is incomplete");
                }
                object.put("source", "apps-script-fallback");
                return object.toString();
            } catch (Exception fallbackError) {
                String first = directError == null ? "Unknown direct-load error" : directError.getMessage();
                throw new Exception("Could not load the full catalog. Direct: " + first
                        + ". Fallback: " + fallbackError.getMessage());
            }
        }

        throw directError == null ? new Exception("Could not load catalog") : directError;
    }

    private static JSONArray fetchSheetTab(String sheetId, String gid) throws Exception {
        String url = "https://docs.google.com/spreadsheets/d/" + sheetId
                + "/export?format=csv&gid=" + gid
                + "&cacheBust=" + System.currentTimeMillis();
        String csv = get(url);

        String trimmed = csv.trim();
        if (trimmed.startsWith("<") || trimmed.toLowerCase().contains("<!doctype html")) {
            throw new Exception("Google Sheet export requires access");
        }

        List<List<String>> rows = parseCsv(csv);
        JSONArray items = new JSONArray();

        // Row 1 contains headers. Every later non-empty title is loaded, with no page or row limit.
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            String name = cell(row, 0).trim();
            String year = cell(row, 1).trim();
            if (name.isEmpty()) continue;
            if ("test".equalsIgnoreCase(name) && "1940".equals(year)) continue;

            JSONObject item = new JSONObject();
            item.put("name", name);
            item.put("year", year);
            items.put(item);
        }
        return items;
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    private static String get(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(45000);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "text/csv, application/json, text/plain, */*");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "CineDiv-Android/1.2");

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            connection.disconnect();
            throw new Exception("Empty response (HTTP " + status + ")");
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } finally {
            connection.disconnect();
        }

        if (status < 200 || status >= 400) {
            throw new Exception("Server error " + status);
        }
        return builder.toString();
    }

    /** Small RFC-4180 style parser that supports commas, quotes and line breaks inside titles. */
    static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < csv.length(); index++) {
            char character = csv.charAt(index);

            if (quoted) {
                if (character == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                row.add(cleanField(field.toString()));
                field.setLength(0);
            } else if (character == '\n') {
                row.add(cleanField(field.toString()));
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (character != '\r') {
                field.append(character);
            }
        }

        if (field.length() > 0 || !row.isEmpty()) {
            row.add(cleanField(field.toString()));
            rows.add(row);
        }
        return rows;
    }

    private static String cleanField(String value) {
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') return value.substring(1);
        return value;
    }
}
