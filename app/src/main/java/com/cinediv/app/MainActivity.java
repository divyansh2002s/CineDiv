package com.cinediv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String SHEET_ID = "1QJyXC_iords4_7f_mDkIaLHikKXv-1oR4_z8UXh9n_U";
    private static final String MOVIES_GID = "1742426553";
    private static final String SHOWS_GID = "772049197";
    private static final String FALLBACK_API_URL = "https://script.google.com/macros/s/AKfycbxLdp5WZt_zqo5JD61nR3zGU1jd-u8_3F3moUnwRLP1pJHN0AnERPQ62HHrOtq7G7vr/exec";

    private static final int BG = Color.rgb(6, 6, 6);
    private static final int SURFACE = Color.rgb(17, 17, 17);
    private static final int SURFACE_ALT = Color.rgb(25, 24, 22);
    private static final int SURFACE_BRIGHT = Color.rgb(34, 32, 28);
    private static final int GOLD = Color.rgb(214, 169, 74);
    private static final int GOLD_LIGHT = Color.rgb(243, 207, 128);
    private static final int GOLD_SOFT = Color.rgb(45, 35, 17);
    private static final int TEXT = Color.rgb(247, 244, 237);
    private static final int MUTED = Color.rgb(174, 169, 159);
    private static final int SUCCESS = Color.rgb(121, 190, 145);
    private static final int DIVIDER = Color.rgb(58, 49, 31);
    private static final int WARNING = Color.rgb(230, 180, 80);
    private static final int DANGER = Color.rgb(215, 108, 108);

    private enum Section { CATALOG, FAVORITES, WATCHLIST, SETTINGS }
    private enum SortMode { AZ, NEWEST, OLDEST }

    private static final class FilterState {
        Integer from;
        Integer to;
        SortMode sort;

        FilterState(Integer from, Integer to, SortMode sort) {
            this.from = from;
            this.to = to;
            this.sort = sort;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<MediaItem> movies = new ArrayList<>();
    private final List<MediaItem> series = new ArrayList<>();
    private final List<MediaItem> visibleItems = new ArrayList<>();

    private Map<String, MediaItem> favorites = new LinkedHashMap<>();
    private Map<String, MediaItem> watchlist = new LinkedHashMap<>();

    private LocalStore store;
    private LinearLayout page;
    private LinearLayout content;
    private LinearLayout searchAreaRow;
    private EditText searchBox;
    private TextView clearSearchButton;
    private TextView countBadge;
    private TextView pageTitle;
    private TextView resultsInfo;
    private TextView filterSummary;
    private Button moviesButton;
    private Button seriesButton;
    private Button filterButton;
    private Button addButton;
    private Button vaultNav;
    private Button favoritesNav;
    private Button watchlistNav;
    private Button settingsNav;
    private ListView listView;
    private LinearLayout emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private MediaAdapter adapter;
    private ProgressBar progress;

    private String mode = "movie";
    private Section section = Section.CATALOG;
    private SortMode sortMode = SortMode.AZ;
    private Integer yearFrom = null;
    private Integer yearTo = null;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        store = new LocalStore(this);
        favorites = store.getFavorites();
        watchlist = store.getWatchlist();
        purgeRemovedTestItem();

        buildUi();
        loadCachedCatalog();
        syncCatalog(false);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(4), dp(14), dp(7));
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildHeader();
        buildSearchArea();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentParams.topMargin = dp(5);
        page.addView(content, contentParams);

        buildListContent();
        buildBottomNavigation();
        updateModeButtons();
        showSection(Section.CATALOG);
    }

    private void buildHeader() {
        FrameLayout brandFrame = new FrameLayout(this);
        page.addView(brandFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));

        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.brand_logo);
        brand.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        brand.setAdjustViewBounds(true);
        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        brandParams.setMargins(0, 0, dp(54), 0);
        brandFrame.addView(brand, brandParams);

        countBadge = text("0", 14, GOLD_LIGHT, true);
        countBadge.setGravity(Gravity.CENTER);
        countBadge.setMinWidth(dp(48));
        countBadge.setPadding(dp(12), 0, dp(12), 0);
        countBadge.setBackground(roundRect(GOLD_SOFT, 999, 1, GOLD));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        badgeParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        brandFrame.addView(countBadge, badgeParams);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeRow.setBackground(roundRect(SURFACE, 16, 1, DIVIDER));
        page.addView(modeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        moviesButton = segmentedButton("Movies");
        seriesButton = segmentedButton("Series");
        modeRow.addView(moviesButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        modeRow.addView(seriesButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        moviesButton.setOnClickListener(v -> switchMode("movie"));
        seriesButton.setOnClickListener(v -> switchMode("series"));
    }

    private void buildSearchArea() {
        searchAreaRow = new LinearLayout(this);
        searchAreaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        rowParams.topMargin = dp(9);
        page.addView(searchAreaRow, rowParams);

        LinearLayout searchShell = new LinearLayout(this);
        searchShell.setGravity(Gravity.CENTER_VERTICAL);
        searchShell.setPadding(dp(12), 0, dp(5), 0);
        searchShell.setBackground(roundRect(SURFACE, 15, 1, DIVIDER));
        searchAreaRow.addView(searchShell, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView searchIcon = text("\u2315", 21, GOLD, true);
        searchIcon.setGravity(Gravity.CENTER);
        searchShell.addView(searchIcon, new LinearLayout.LayoutParams(dp(28), dp(44)));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Search title, year, or words");
        searchBox.setHintTextColor(MUTED);
        searchBox.setTextColor(TEXT);
        searchBox.setTextSize(14);
        searchBox.setPadding(dp(5), 0, dp(5), 0);
        searchBox.setBackgroundColor(Color.TRANSPARENT);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchShell.addView(searchBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        clearSearchButton = text("\u00d7", 27, GOLD_LIGHT, false);
        clearSearchButton.setGravity(Gravity.CENTER);
        clearSearchButton.setVisibility(View.GONE);
        clearSearchButton.setBackground(roundRect(SURFACE_ALT, 999, 0, Color.TRANSPARENT));
        clearSearchButton.setOnClickListener(v -> {
            searchBox.setText("");
            searchBox.requestFocus();
            hideKeyboard(searchBox);
        });
        searchShell.addView(clearSearchButton, new LinearLayout.LayoutParams(dp(38), dp(38)));

        filterButton = outlineButton("Filter");
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(82),
                ViewGroup.LayoutParams.MATCH_PARENT);
        filterParams.leftMargin = dp(8);
        searchAreaRow.addView(filterButton, filterParams);

        filterSummary = text("All years  |  A-Z", 11, MUTED, false);
        filterSummary.setPadding(dp(4), dp(5), dp(4), 0);
        page.addView(filterSummary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        filterButton.setOnClickListener(v -> showFilterDialog());
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String value = s == null ? "" : s.toString().trim();
                query = normalize(value);
                clearSearchButton.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilters(true);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void buildListContent() {
        content.removeAllViews();

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        pageTitle = text("Cinema Vault", 18, TEXT, true);
        titleRow.addView(pageTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addButton = outlineButton("+ Add");
        addButton.setVisibility(section == Section.FAVORITES || section == Section.WATCHLIST
                ? View.VISIBLE : View.GONE);
        addButton.setOnClickListener(v -> showAddOptions());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(70), dp(34));
        addParams.rightMargin = dp(7);
        titleRow.addView(addButton, addParams);

        resultsInfo = text("0", 11, GOLD_LIGHT, true);
        resultsInfo.setGravity(Gravity.CENTER);
        resultsInfo.setMinWidth(dp(40));
        resultsInfo.setPadding(dp(10), 0, dp(10), 0);
        resultsInfo.setBackground(roundRect(GOLD_SOFT, 999, 1, DIVIDER));
        titleRow.addView(resultsInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

        Button syncButton = outlineButton("\u21bb");
        syncButton.setTextSize(19);
        syncButton.setOnClickListener(v -> syncCatalog(true));
        LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(dp(42), dp(34));
        syncParams.leftMargin = dp(7);
        titleRow.addView(syncButton, syncParams);

        FrameLayout listFrame = new FrameLayout(this);
        content.addView(listFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listView = new ListView(this);
        listView.setDividerHeight(0);
        listView.setBackgroundColor(BG);
        listView.setVerticalScrollBarEnabled(true);
        listView.setFastScrollEnabled(true);
        listView.setSmoothScrollbarEnabled(true);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(8));
        listView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        adapter = new MediaAdapter(this);
        listView.setAdapter(adapter);
        listFrame.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyState = new LinearLayout(this);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView emptyLogo = new ImageView(this);
        emptyLogo.setImageResource(R.drawable.brand_emblem);
        emptyLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emptyState.addView(emptyLogo, new LinearLayout.LayoutParams(dp(76), dp(76)));

        emptyTitle = text("Nothing here", 17, TEXT, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyState.addView(emptyTitle);

        emptyMessage = text("Try another search or clear the filters.", 12, MUTED, false);
        emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setPadding(0, dp(7), 0, 0);
        emptyState.addView(emptyMessage);

        Button emptyAdd = primaryButton("Add a title");
        emptyAdd.setOnClickListener(v -> showAddOptions());
        LinearLayout.LayoutParams emptyAddParams = new LinearLayout.LayoutParams(dp(150), dp(42));
        emptyAddParams.topMargin = dp(14);
        emptyState.addView(emptyAdd, emptyAddParams);
        emptyAdd.setVisibility(section == Section.FAVORITES || section == Section.WATCHLIST
                ? View.VISIBLE : View.GONE);

        listFrame.addView(emptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setEmptyView(emptyState);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER;
        listFrame.addView(progress, progressParams);
    }

    private void buildBottomNavigation() {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(4), dp(4), dp(4), dp(4));
        navigation.setBackground(roundRect(SURFACE, 18, 1, DIVIDER));
        page.addView(navigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        vaultNav = navButton("\u25a6  Vault", Section.CATALOG);
        favoritesNav = navButton("\u2665  Favorites", Section.FAVORITES);
        watchlistNav = navButton("\u2713  Watchlist", Section.WATCHLIST);
        settingsNav = navButton("\u2699  Settings", Section.SETTINGS);

        navigation.addView(vaultNav, navParams());
        navigation.addView(favoritesNav, navParams());
        navigation.addView(watchlistNav, navParams());
        navigation.addView(settingsNav, navParams());
        updateNavigation();
    }

    private Button navButton(String label, Section target) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setOnClickListener(v -> showSection(target));
        return button;
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        return params;
    }

    private void updateNavigation() {
        styleNav(vaultNav, section == Section.CATALOG);
        styleNav(favoritesNav, section == Section.FAVORITES);
        styleNav(watchlistNav, section == Section.WATCHLIST);
        styleNav(settingsNav, section == Section.SETTINGS);
    }

    private void styleNav(Button button, boolean active) {
        if (button == null) return;
        button.setTextColor(active ? GOLD_LIGHT : MUTED);
        button.setBackground(roundRect(active ? GOLD_SOFT : Color.TRANSPARENT,
                13, active ? 1 : 0, active ? GOLD : Color.TRANSPARENT));
    }

    private void switchMode(String newMode) {
        mode = "series".equals(newMode) ? "series" : "movie";
        updateModeButtons();
        updatePageTitle();
        updateFilterSummary();
        applyFilters(true);
    }

    private void updateModeButtons() {
        styleSegment(moviesButton, "movie".equals(mode));
        styleSegment(seriesButton, "series".equals(mode));
    }

    private void styleSegment(Button button, boolean active) {
        button.setTextColor(active ? Color.rgb(20, 16, 8) : MUTED);
        button.setBackground(roundRect(active ? GOLD : Color.TRANSPARENT,
                12, active ? 1 : 0, active ? GOLD_LIGHT : Color.TRANSPARENT));
    }

    private void showSection(Section newSection) {
        section = newSection;
        updateNavigation();
        boolean listSection = section != Section.SETTINGS;
        searchAreaRow.setVisibility(listSection ? View.VISIBLE : View.GONE);
        filterButton.setVisibility(listSection ? View.VISIBLE : View.GONE);
        filterSummary.setVisibility(listSection ? View.VISIBLE : View.GONE);

        if (section == Section.SETTINGS) {
            showSettings();
        } else {
            buildListContent();
            updatePageTitle();
            applyFilters(true);
        }
    }

    private void updatePageTitle() {
        if (pageTitle == null) return;
        String typeName = "movie".equals(mode) ? "Movies" : "Series";
        switch (section) {
            case FAVORITES:
                pageTitle.setText("Favorite " + typeName);
                break;
            case WATCHLIST:
                pageTitle.setText(typeName + " Watchlist");
                break;
            default:
                pageTitle.setText("Cinema Vault - " + typeName);
                break;
        }
    }

    private void showSettings() {
        content.removeAllViews();

        TextView heading = text("CineDiv Control Room", 20, TEXT, true);
        content.addView(heading, marginParams(-1, -2, 4, 8, 4, 10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout syncCard = card();
        cards.addView(syncCard, marginParams(-1, -2, 0, 0, 0, 12));
        syncCard.addView(text("Google Sheet connection", 15, TEXT, true));
        syncCard.addView(text("Read-only: Movies tab + Shows tab", 12, MUTED, false));
        syncCard.addView(text(lastSyncText(), 12, SUCCESS, false));
        Button syncNow = primaryButton("Refresh Cinema Vault");
        syncNow.setOnClickListener(v -> syncCatalog(true));
        syncCard.addView(syncNow, marginParams(-1, dp(44), 0, 12, 0, 0));

        LinearLayout countsCard = card();
        cards.addView(countsCard, marginParams(-1, -2, 0, 0, 0, 12));
        countsCard.addView(text("Titles loaded", 15, TEXT, true));
        countsCard.addView(text("Movies: " + movies.size(), 12, MUTED, false));
        countsCard.addView(text("Series: " + series.size(), 12, MUTED, false));
        countsCard.addView(text("Every non-empty row from columns A and B is included.",
                12, MUTED, false));

        LinearLayout storageCard = card();
        cards.addView(storageCard, marginParams(-1, -2, 0, 0, 0, 12));
        storageCard.addView(text("Personal lists", 15, TEXT, true));
        storageCard.addView(text("Favorites: " + favorites.size(), 12, MUTED, false));
        storageCard.addView(text("Watchlist: " + watchlist.size(), 12, MUTED, false));
        storageCard.addView(text("Manual titles and list choices stay on this phone.",
                12, MUTED, false));
        storageCard.addView(text("The app never writes to your Google Sheet.",
                12, GOLD_LIGHT, false));

        LinearLayout aboutCard = card();
        cards.addView(aboutCard, marginParams(-1, -2, 0, 0, 0, 12));
        aboutCard.addView(text("CineDiv 1.2", 15, TEXT, true));
        aboutCard.addView(text("Cinema Vault, gold-dark design, clearer search, and local list tools.",
                12, MUTED, false));
    }

    private String lastSyncText() {
        long timestamp = store.getLastSync();
        if (timestamp <= 0) return "Not synced yet";
        return "Last synced: " + DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(timestamp));
    }

    private void applyFilters(boolean resetScroll) {
        if (section == Section.SETTINGS || adapter == null) return;

        List<MediaItem> source;
        if (section == Section.FAVORITES) {
            source = LocalStore.values(favorites);
        } else if (section == Section.WATCHLIST) {
            source = LocalStore.values(watchlist);
        } else {
            source = "movie".equals(mode) ? movies : series;
        }

        visibleItems.clear();
        for (MediaItem item : source) {
            if (!mode.equals(item.type)) continue;
            if (isRemovedTest(item.name, item.year)) continue;
            if (!matchesQuery(item, query)) continue;
            if (!matchesYear(item, yearFrom, yearTo)) continue;
            visibleItems.add(item);
        }

        Collections.sort(visibleItems, comparatorFor(sortMode));
        adapter.notifyDataSetChanged();
        if (resetScroll && listView != null) listView.setSelection(0);

        int total = "movie".equals(mode) ? movies.size() : series.size();
        countBadge.setText(String.valueOf(total));
        resultsInfo.setText(String.valueOf(visibleItems.size()));
        updatePageTitle();
        updateEmptyState();
    }

    private Comparator<MediaItem> comparatorFor(SortMode modeToUse) {
        Comparator<MediaItem> byName = Comparator
                .comparing((MediaItem item) -> normalize(item.name))
                .thenComparing(item -> item.year);

        if (modeToUse == SortMode.AZ) return byName;

        Comparator<MediaItem> byYear = (left, right) -> {
            int leftYear = left.numericYear();
            int rightYear = right.numericYear();
            if (leftYear == 0 && rightYear == 0) return byName.compare(left, right);
            if (leftYear == 0) return 1;
            if (rightYear == 0) return -1;
            int comparison = Integer.compare(leftYear, rightYear);
            if (modeToUse == SortMode.NEWEST) comparison = -comparison;
            return comparison == 0 ? byName.compare(left, right) : comparison;
        };
        return byYear;
    }

    private boolean matchesQuery(MediaItem item, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) return true;
        String haystack = normalize(item.name + " " + item.year);
        String[] terms = normalizedQuery.split(" ");
        for (String term : terms) {
            if (!term.isEmpty() && !haystack.contains(term)) return false;
        }
        return true;
    }

    private boolean matchesYear(MediaItem item, Integer from, Integer to) {
        if (from == null && to == null) return true;
        int year = item.numericYear();
        if (year == 0) return false;
        if (from != null && year < from) return false;
        return to == null || year <= to;
    }

    private int countMatching(Integer from, Integer to) {
        List<MediaItem> source;
        if (section == Section.FAVORITES) source = LocalStore.values(favorites);
        else if (section == Section.WATCHLIST) source = LocalStore.values(watchlist);
        else source = "movie".equals(mode) ? movies : series;

        int count = 0;
        for (MediaItem item : source) {
            if (!mode.equals(item.type)) continue;
            if (isRemovedTest(item.name, item.year)) continue;
            if (!matchesQuery(item, query)) continue;
            if (!matchesYear(item, from, to)) continue;
            count++;
        }
        return count;
    }

    private void updateEmptyState() {
        if (emptyTitle == null || emptyMessage == null) return;
        if (section == Section.FAVORITES) {
            emptyTitle.setText("No favorites yet");
            emptyMessage.setText("Add from Cinema Vault or create a manual title.");
        } else if (section == Section.WATCHLIST) {
            emptyTitle.setText("Your watchlist is clear");
            emptyMessage.setText("Add from Cinema Vault or create a manual title.");
        } else if (!query.isEmpty()) {
            emptyTitle.setText("No matching titles");
            emptyMessage.setText("Clear the search or change the year filter.");
        } else {
            emptyTitle.setText("No titles in this view");
            emptyMessage.setText("Refresh the Cinema Vault or change the filter.");
        }
    }

    private void showFilterDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FilterState draft = new FilterState(yearFrom, yearTo, sortMode);

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(24));
        panel.setBackground(roundRect(SURFACE, 24, 1, GOLD));
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(headingRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        TextView heading = text("Shape your view", 20, TEXT, true);
        headingRow.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("\u00d7", 28, GOLD_LIGHT, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        headingRow.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        panel.addView(text("Sort", 12, GOLD_LIGHT, true),
                marginParams(-1, -2, 0, 10, 0, 6));
        LinearLayout sortRow = new LinearLayout(this);
        panel.addView(sortRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        Button az = chipButton("A-Z");
        Button newest = chipButton("Newest");
        Button oldest = chipButton("Oldest");
        sortRow.addView(az, new LinearLayout.LayoutParams(0, dp(38), 1f));
        sortRow.addView(newest, new LinearLayout.LayoutParams(0, dp(38), 1f));
        sortRow.addView(oldest, new LinearLayout.LayoutParams(0, dp(38), 1f));

        panel.addView(text("Quick decades", 12, GOLD_LIGHT, true),
                marginParams(-1, -2, 0, 14, 0, 6));
        HorizontalScrollView decadeScroll = new HorizontalScrollView(this);
        decadeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout decadeRow = new LinearLayout(this);
        decadeScroll.addView(decadeRow);
        panel.addView(decadeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        Button allYears = chipButton("All");
        decadeRow.addView(allYears, chipParams());
        List<Button> decadeButtons = new ArrayList<>();
        List<Integer> decades = availableDecades();
        for (Integer decade : decades) {
            Button button = chipButton(decade + "s");
            button.setTag(decade);
            decadeButtons.add(button);
            decadeRow.addView(button, chipParams());
        }

        panel.addView(text("Custom year range", 12, GOLD_LIGHT, true),
                marginParams(-1, -2, 0, 14, 0, 6));
        LinearLayout rangeRow = new LinearLayout(this);
        panel.addView(rangeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        EditText fromInput = dialogInput("From", InputType.TYPE_CLASS_NUMBER);
        EditText toInput = dialogInput("To", InputType.TYPE_CLASS_NUMBER);
        if (draft.from != null) fromInput.setText(String.valueOf(draft.from));
        if (draft.to != null) toInput.setText(String.valueOf(draft.to));
        rangeRow.addView(fromInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView rangeDivider = text("to", 12, MUTED, false);
        rangeDivider.setGravity(Gravity.CENTER);
        rangeRow.addView(rangeDivider, new LinearLayout.LayoutParams(dp(44),
                ViewGroup.LayoutParams.MATCH_PARENT));
        rangeRow.addView(toInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView preview = text("", 13, GOLD_LIGHT, true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(roundRect(GOLD_SOFT, 14, 1, DIVIDER));
        panel.addView(preview, marginParams(-1, dp(44), 0, 15, 0, 10));

        LinearLayout actionRow = new LinearLayout(this);
        panel.addView(actionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        Button reset = outlineButton("Reset");
        Button apply = primaryButton("Apply filter");
        actionRow.addView(reset, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.4f);
        applyParams.leftMargin = dp(10);
        actionRow.addView(apply, applyParams);

        final Runnable updatePreview = () -> {
            draft.from = parseOptionalYear(fromInput.getText().toString());
            draft.to = parseOptionalYear(toInput.getText().toString());
            if (draft.from != null && draft.to != null && draft.from > draft.to) {
                preview.setText("Start year must be before end year");
                preview.setTextColor(DANGER);
            } else {
                int count = countMatching(draft.from, draft.to);
                preview.setText(count + (count == 1 ? " title" : " titles") + " in this view");
                preview.setTextColor(GOLD_LIGHT);
            }
            styleChip(az, draft.sort == SortMode.AZ);
            styleChip(newest, draft.sort == SortMode.NEWEST);
            styleChip(oldest, draft.sort == SortMode.OLDEST);
            styleChip(allYears, draft.from == null && draft.to == null);
            for (Button button : decadeButtons) {
                int decade = (Integer) button.getTag();
                styleChip(button, draft.from != null && draft.to != null
                        && draft.from == decade && draft.to == decade + 9);
            }
        };

        az.setOnClickListener(v -> { draft.sort = SortMode.AZ; updatePreview.run(); });
        newest.setOnClickListener(v -> { draft.sort = SortMode.NEWEST; updatePreview.run(); });
        oldest.setOnClickListener(v -> { draft.sort = SortMode.OLDEST; updatePreview.run(); });
        allYears.setOnClickListener(v -> {
            draft.from = null;
            draft.to = null;
            fromInput.setText("");
            toInput.setText("");
            updatePreview.run();
        });
        for (Button button : decadeButtons) {
            button.setOnClickListener(v -> {
                int decade = (Integer) v.getTag();
                draft.from = decade;
                draft.to = decade + 9;
                fromInput.setText(String.valueOf(decade));
                toInput.setText(String.valueOf(decade + 9));
                updatePreview.run();
            });
        }

        TextWatcher rangeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview.run();
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        fromInput.addTextChangedListener(rangeWatcher);
        toInput.addTextChangedListener(rangeWatcher);

        reset.setOnClickListener(v -> {
            draft.from = null;
            draft.to = null;
            draft.sort = SortMode.AZ;
            fromInput.setText("");
            toInput.setText("");
            updatePreview.run();
        });

        apply.setOnClickListener(v -> {
            draft.from = parseOptionalYear(fromInput.getText().toString());
            draft.to = parseOptionalYear(toInput.getText().toString());
            if (draft.from != null && draft.to != null && draft.from > draft.to) {
                Toast.makeText(this, "Check the year range", Toast.LENGTH_SHORT).show();
                return;
            }
            yearFrom = draft.from;
            yearTo = draft.to;
            sortMode = draft.sort;
            updateFilterSummary();
            applyFilters(true);
            hideKeyboard(fromInput);
            dialog.dismiss();
        });

        updatePreview.run();
        dialog.setContentView(scroll);
        dialog.setOnShowListener(ignored -> styleBottomDialog(dialog));
        dialog.show();
        styleBottomDialog(dialog);
    }

    private void styleBottomDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM);
        window.setDimAmount(0.7f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private List<Integer> availableDecades() {
        Set<Integer> result = new TreeSet<>();
        List<MediaItem> source = "movie".equals(mode) ? movies : series;
        for (MediaItem item : source) {
            int year = item.numericYear();
            if (year > 0) result.add((year / 10) * 10);
        }
        return new ArrayList<>(result);
    }

    private Integer parseOptionalYear(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return null;
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void updateFilterSummary() {
        if (filterSummary == null) return;
        String yearText;
        if (yearFrom == null && yearTo == null) {
            yearText = "All years";
        } else if (yearFrom != null && yearTo != null
                && yearFrom % 10 == 0 && yearTo == yearFrom + 9) {
            yearText = yearFrom + "s";
        } else {
            yearText = (yearFrom == null ? "Any" : yearFrom)
                    + "-" + (yearTo == null ? "Now" : yearTo);
        }

        String sortText = sortMode == SortMode.NEWEST ? "Newest first"
                : sortMode == SortMode.OLDEST ? "Oldest first" : "A-Z";
        filterSummary.setText(yearText + "  |  " + sortText);
        boolean active = yearFrom != null || yearTo != null || sortMode != SortMode.AZ;
        filterButton.setText(active ? "Filter *" : "Filter");
        filterButton.setTextColor(active ? GOLD_LIGHT : TEXT);
    }

    private void showAddOptions() {
        if (section != Section.FAVORITES && section != Section.WATCHLIST) return;
        String destination = section == Section.FAVORITES ? "Favorites" : "Watchlist";
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Add to " + destination)
                .setItems(new String[]{"Choose from Cinema Vault", "Add manually"},
                        (dialog, which) -> {
                            if (which == 0) showCatalogPicker();
                            else showManualAddDialog();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCatalogPicker() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(16));
        panel.setBackground(roundRect(SURFACE, 22, 1, GOLD));

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(headingRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        TextView heading = text("Choose from Cinema Vault", 18, TEXT, true);
        headingRow.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("\u00d7", 28, GOLD_LIGHT, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        headingRow.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout searchShell = new LinearLayout(this);
        searchShell.setGravity(Gravity.CENTER_VERTICAL);
        searchShell.setPadding(dp(12), 0, dp(5), 0);
        searchShell.setBackground(roundRect(SURFACE_ALT, 14, 1, DIVIDER));
        panel.addView(searchShell, marginParams(-1, dp(48), 0, 8, 0, 10));

        EditText pickerSearch = new EditText(this);
        pickerSearch.setSingleLine(true);
        pickerSearch.setHint("Search the Vault");
        pickerSearch.setHintTextColor(MUTED);
        pickerSearch.setTextColor(TEXT);
        pickerSearch.setTextSize(14);
        pickerSearch.setBackgroundColor(Color.TRANSPARENT);
        searchShell.addView(pickerSearch, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView pickerClear = text("\u00d7", 25, GOLD_LIGHT, false);
        pickerClear.setGravity(Gravity.CENTER);
        pickerClear.setVisibility(View.GONE);
        pickerClear.setOnClickListener(v -> pickerSearch.setText(""));
        searchShell.addView(pickerClear, new LinearLayout.LayoutParams(dp(38), dp(38)));

        ListView pickerList = new ListView(this);
        pickerList.setDividerHeight(0);
        pickerList.setBackgroundColor(BG);
        pickerList.setFastScrollEnabled(true);
        panel.addView(pickerList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        List<MediaItem> all = new ArrayList<>();
        List<MediaItem> catalogSource = "movie".equals(mode) ? movies : series;
        Map<String, MediaItem> destination = section == Section.FAVORITES ? favorites : watchlist;
        for (MediaItem item : catalogSource) {
            if (!destination.containsKey(item.key()) && !isRemovedTest(item.name, item.year)) {
                all.add(item);
            }
        }
        Collections.sort(all, comparatorFor(SortMode.AZ));

        PickerAdapter pickerAdapter = new PickerAdapter(all);
        pickerList.setAdapter(pickerAdapter);
        pickerList.setOnItemClickListener((parent, view, position, id) -> {
            MediaItem item = pickerAdapter.getItem(position);
            addToCurrentCollection(item);
            Toast.makeText(this, item.name + " added", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        pickerSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String value = s == null ? "" : s.toString();
                pickerClear.setVisibility(value.trim().isEmpty() ? View.GONE : View.VISIBLE);
                pickerAdapter.filter(normalize(value));
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.72f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.82f));
        }
    }

    private void showManualAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        EditText titleInput = dialogInput(
                "Movie or series name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText yearInput = dialogInput("Release year (optional)", InputType.TYPE_CLASS_NUMBER);
        form.addView(titleInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        yearParams.topMargin = dp(10);
        form.addView(yearInput, yearParams);

        String destination = section == Section.FAVORITES ? "Favorites" : "Watchlist";
        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Manual title - " + destination)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = titleInput.getText().toString().trim();
                    String year = cleanYear(yearInput.getText().toString());
                    if (title.isEmpty()) {
                        titleInput.setError("Title is required");
                        return;
                    }
                    if (isRemovedTest(title, year)) {
                        titleInput.setError("This removed test entry cannot be added");
                        return;
                    }
                    MediaItem item = new MediaItem(title, year, mode);
                    Map<String, MediaItem> target = section == Section.FAVORITES ? favorites : watchlist;
                    if (target.containsKey(item.key())) {
                        Toast.makeText(this, "Already in " + destination, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addToCurrentCollection(item);
                    hideKeyboard(titleInput);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void addToCurrentCollection(MediaItem item) {
        MediaItem copy = new MediaItem(item.name, item.year, item.type, item.watched);
        if (section == Section.FAVORITES) {
            favorites.put(copy.key(), copy);
            store.saveFavorites(favorites);
        } else if (section == Section.WATCHLIST) {
            watchlist.put(copy.key(), copy);
            store.saveWatchlist(watchlist);
        }
        applyFilters(false);
    }

    private void toggleFavorite(MediaItem item) {
        if (favorites.containsKey(item.key())) favorites.remove(item.key());
        else favorites.put(item.key(), new MediaItem(item.name, item.year, item.type));
        store.saveFavorites(favorites);
        applyFilters(false);
    }

    private void toggleWatchlist(MediaItem item) {
        if (watchlist.containsKey(item.key())) watchlist.remove(item.key());
        else watchlist.put(item.key(), new MediaItem(item.name, item.year, item.type));
        store.saveWatchlist(watchlist);
        applyFilters(false);
    }

    private void toggleWatched(MediaItem item) {
        MediaItem stored = watchlist.get(item.key());
        if (stored == null) return;
        stored.watched = !stored.watched;
        store.saveWatchlist(watchlist);
        applyFilters(false);
    }

    private void showItemActions(MediaItem item) {
        List<String> actions = new ArrayList<>();
        actions.add(favorites.containsKey(item.key()) ? "Remove from Favorites" : "Add to Favorites");
        actions.add(watchlist.containsKey(item.key()) ? "Remove from Watchlist" : "Add to Watchlist");
        if (watchlist.containsKey(item.key())) {
            actions.add(watchlist.get(item.key()).watched ? "Mark as not watched" : "Mark as watched");
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(item.name)
                .setMessage(item.year.isEmpty() ? "Release year not listed" : item.year)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) toggleFavorite(item);
                    else if (which == 1) toggleWatchlist(item);
                    else toggleWatched(item);
                })
                .show();
    }

    private void loadCachedCatalog() {
        String cache = store.getCache();
        if (cache.isEmpty()) return;
        try {
            parseCatalog(cache);
            applyFilters(false);
        } catch (Exception ignored) {
        }
    }

    private void syncCatalog(boolean showSuccess) {
        setLoading(true);
        executor.execute(() -> {
            try {
                String response = CineDivCatalogClient.fetchCatalog(
                        SHEET_ID, MOVIES_GID, SHOWS_GID, FALLBACK_API_URL);
                runOnUiThread(() -> {
                    try {
                        parseCatalog(response);
                        store.saveCache(response);
                        store.setLastSync(System.currentTimeMillis());
                        setLoading(false);
                        if (section == Section.SETTINGS) showSettings();
                        else applyFilters(false);
                        if (showSuccess) {
                            Toast.makeText(this,
                                    "Loaded " + movies.size() + " movies and " + series.size() + " series",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception error) {
                        setLoading(false);
                        showError("Invalid catalog data", error);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (movies.isEmpty() && series.isEmpty()) showError("Sync failed", error);
                    else if (showSuccess) Toast.makeText(this,
                            "Could not refresh. Showing the saved Cinema Vault.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void parseCatalog(String raw) throws Exception {
        JSONObject object = new JSONObject(raw);
        if (!object.optBoolean("success", true)) {
            throw new Exception(object.optString("message", "Catalog returned an error"));
        }

        List<MediaItem> nextMovies = readArray(object.optJSONArray("movies"), "movie");
        List<MediaItem> nextSeries = readArray(object.optJSONArray("series"), "series");
        if (nextMovies.isEmpty() && nextSeries.isEmpty()) {
            throw new Exception("No movie or series rows were received");
        }

        movies.clear();
        movies.addAll(nextMovies);
        series.clear();
        series.addAll(nextSeries);
    }

    private List<MediaItem> readArray(JSONArray array, String type) {
        List<MediaItem> destination = new ArrayList<>();
        if (array == null) return destination;

        Map<String, MediaItem> unique = new LinkedHashMap<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) continue;
            String name = object.optString("name").trim();
            String year = cleanYear(object.optString("year"));
            if (name.isEmpty() || isRemovedTest(name, year)) continue;
            MediaItem item = new MediaItem(name, year, type);
            unique.put(item.key(), item);
        }
        destination.addAll(unique.values());
        return destination;
    }

    private String cleanYear(String value) {
        String clean = value == null ? "" : value.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:18|19|20|21)\\d{2}").matcher(clean);
        return matcher.find() ? matcher.group() : clean;
    }

    private boolean isRemovedTest(String name, String year) {
        return "test".equalsIgnoreCase(name == null ? "" : name.trim())
                && "1940".equals(year == null ? "" : year.trim());
    }

    private void purgeRemovedTestItem() {
        boolean favoriteChanged = favorites.entrySet().removeIf(entry ->
                isRemovedTest(entry.getValue().name, entry.getValue().year));
        boolean watchlistChanged = watchlist.entrySet().removeIf(entry ->
                isRemovedTest(entry.getValue().name, entry.getValue().year));
        if (favoriteChanged) store.saveFavorites(favorites);
        if (watchlistChanged) store.saveWatchlist(watchlist);
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String title, Exception error) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setMessage(error.getMessage() == null ? "Unknown error" : error.getMessage())
                .setPositiveButton("Retry", (dialog, which) -> syncCatalog(true))
                .setNegativeButton("Close", null)
                .show();
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value,
                Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button segmentedButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private Button outlineButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(TEXT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setBackground(roundRect(SURFACE, 13, 1, DIVIDER));
        return button;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(20, 16, 8));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundRect(GOLD, 13, 1, GOLD_LIGHT));
        return button;
    }

    private Button chipButton(String label) {
        Button button = outlineButton(label);
        button.setTextSize(12);
        return button;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        params.rightMargin = dp(7);
        return params;
    }

    private void styleChip(Button button, boolean active) {
        button.setTextColor(active ? Color.rgb(20, 16, 8) : MUTED);
        button.setBackground(roundRect(active ? GOLD : SURFACE_ALT,
                999, 1, active ? GOLD_LIGHT : DIVIDER));
    }

    private EditText dialogInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundRect(SURFACE_ALT, 12, 1, DIVIDER));
        return input;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundRect(SURFACE, 17, 1, DIVIDER));
        return card;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height,
                                                    int left, int top, int right, int bottom) {
        int actualWidth = width == -1 ? ViewGroup.LayoutParams.MATCH_PARENT
                : width == -2 ? ViewGroup.LayoutParams.WRAP_CONTENT : width;
        int actualHeight = height == -1 ? ViewGroup.LayoutParams.MATCH_PARENT
                : height == -2 ? ViewGroup.LayoutParams.WRAP_CONTENT : height;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(actualWidth, actualHeight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class MediaAdapter extends BaseAdapter {
        private final Context context;

        MediaAdapter(Context context) {
            this.context = context;
        }

        @Override public int getCount() { return visibleItems.size(); }
        @Override public MediaItem getItem(int position) { return visibleItems.get(position); }
        @Override public long getItemId(int position) { return getItem(position).key().hashCode(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                holder = createRow();
                convertView = holder.outer;
                convertView.setTag(holder);
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            MediaItem item = getItem(position);
            holder.initial.setText(item.name.isEmpty() ? "?"
                    : item.name.substring(0, 1).toUpperCase(Locale.ROOT));
            holder.name.setText(item.name);
            holder.year.setText(item.year.isEmpty() ? "Year not listed" : item.year);

            boolean favorite = favorites.containsKey(item.key());
            boolean listed = watchlist.containsKey(item.key());
            boolean watched = listed && watchlist.get(item.key()).watched;
            holder.favorite.setText(favorite ? "\u2665" : "\u2661");
            holder.favorite.setTextColor(favorite ? GOLD_LIGHT : MUTED);
            holder.watchlist.setText(watched ? "\u2713" : listed ? "-" : "+");
            holder.watchlist.setTextColor(watched ? SUCCESS : listed ? WARNING : MUTED);

            holder.name.setPaintFlags(watched
                    ? holder.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : holder.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.card.setAlpha(watched ? 0.72f : 1f);
            holder.status.setText(section == Section.WATCHLIST && watched ? "WATCHED"
                    : "series".equals(item.type) ? "SERIES" : "MOVIE");
            holder.status.setTextColor(section == Section.WATCHLIST && watched ? SUCCESS : GOLD);

            holder.favorite.setOnClickListener(v -> toggleFavorite(item));
            holder.watchlist.setOnClickListener(v -> {
                if (section == Section.WATCHLIST && listed) toggleWatched(item);
                else toggleWatchlist(item);
            });
            holder.card.setOnClickListener(v -> showItemActions(item));
            return convertView;
        }

        private RowHolder createRow() {
            RowHolder holder = new RowHolder();

            holder.outer = new LinearLayout(context);
            holder.outer.setPadding(0, 0, 0, dp(9));
            holder.outer.setLayoutParams(new ListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(87)));

            holder.card = new LinearLayout(context);
            holder.card.setGravity(Gravity.CENTER_VERTICAL);
            holder.card.setPadding(0, dp(7), dp(7), dp(7));
            holder.card.setBackground(roundRect(SURFACE, 16, 1, DIVIDER));
            holder.outer.addView(holder.card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

            View accent = new View(context);
            accent.setBackground(roundRect(GOLD, 8, 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(4), dp(50));
            accentParams.leftMargin = dp(5);
            accentParams.rightMargin = dp(8);
            holder.card.addView(accent, accentParams);

            holder.initial = text("A", 18, GOLD_LIGHT, true);
            holder.initial.setGravity(Gravity.CENTER);
            holder.initial.setBackground(roundRect(GOLD_SOFT, 13, 1, GOLD));
            holder.card.addView(holder.initial, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            labelParams.leftMargin = dp(12);
            holder.card.addView(labels, labelParams);

            holder.name = text("Title", 14, TEXT, true);
            holder.name.setMaxLines(2);
            labels.addView(holder.name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout meta = new LinearLayout(context);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            labels.addView(meta, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

            holder.year = text("Year", 10, MUTED, false);
            holder.year.setGravity(Gravity.CENTER);
            holder.year.setPadding(dp(8), 0, dp(8), 0);
            holder.year.setBackground(roundRect(SURFACE_BRIGHT, 999, 0, Color.TRANSPARENT));
            meta.addView(holder.year, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)));

            holder.status = text("MOVIE", 9, GOLD, true);
            holder.status.setPadding(dp(8), 0, dp(8), 0);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(22));
            statusParams.leftMargin = dp(6);
            meta.addView(holder.status, statusParams);

            holder.favorite = actionText("\u2661");
            holder.watchlist = actionText("+");
            holder.card.addView(holder.favorite, new LinearLayout.LayoutParams(dp(42), dp(52)));
            holder.card.addView(holder.watchlist, new LinearLayout.LayoutParams(dp(42), dp(52)));
            return holder;
        }

        private TextView actionText(String label) {
            TextView action = text(label, 23, MUTED, true);
            action.setGravity(Gravity.CENTER);
            action.setBackground(roundRect(SURFACE_ALT, 12, 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams ignored = new LinearLayout.LayoutParams(dp(42), dp(48));
            return action;
        }
    }

    private final class PickerAdapter extends BaseAdapter {
        private final List<MediaItem> all;
        private final List<MediaItem> shown = new ArrayList<>();

        PickerAdapter(List<MediaItem> source) {
            all = new ArrayList<>(source);
            shown.addAll(source);
        }

        void filter(String queryValue) {
            shown.clear();
            for (MediaItem item : all) {
                if (matchesQuery(item, queryValue)) shown.add(item);
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return shown.size(); }
        @Override public MediaItem getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return getItem(position).key().hashCode(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView year;
            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(7), dp(12), dp(7));
                row.setBackground(roundRect(SURFACE_ALT, 13, 1, DIVIDER));
                row.setLayoutParams(new ListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

                title = text("Title", 14, TEXT, true);
                title.setMaxLines(2);
                row.addView(title, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

                year = text("Year", 11, GOLD_LIGHT, true);
                year.setGravity(Gravity.CENTER);
                year.setPadding(dp(9), 0, dp(9), 0);
                year.setBackground(roundRect(GOLD_SOFT, 999, 1, DIVIDER));
                row.addView(year, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
                convertView = row;
                convertView.setTag(new TextView[]{title, year});
            } else {
                TextView[] views = (TextView[]) convertView.getTag();
                title = views[0];
                year = views[1];
            }
            MediaItem item = getItem(position);
            title.setText(item.name);
            year.setText(item.year.isEmpty() ? "-" : item.year);
            return convertView;
        }
    }

    private static final class RowHolder {
        LinearLayout outer;
        LinearLayout card;
        TextView initial;
        TextView name;
        TextView year;
        TextView status;
        TextView favorite;
        TextView watchlist;
    }
}
