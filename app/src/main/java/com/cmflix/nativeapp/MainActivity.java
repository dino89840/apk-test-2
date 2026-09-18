package com.cmflix.nativeapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;

import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;




import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    


    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView errorText;
    private EditText searchInput;
    private LinearLayout categoryBar;
private LinearLayout searchHistoryContainer;

private View searchHistoryRow;

private TextView sectionTitle;
private TextView localClearButton;
private TextView clearSearchHistoryButton;

private Button accountButton;
private Button premiumButton;
private ImageView vipPlanBanner;

private long lastBackPressedAt = 0L;

private static final long BACK_EXIT_INTERVAL_MS =
        2000L;


private static final long PROFILE_CACHE_MS =
        6L * 60L * 60L * 1000L;

private boolean profileRefreshInFlight = false;


    private TitleAdapter adapter;
    private GridLayoutManager layoutManager;

    private final List<JSONObject> allItems =
            new ArrayList<>();
/*
 * Category တစ်ခုချင်းစီ၏ သိထားပြီးသား count။
 * API request အသစ် မခေါ်ဘဲ ရရှိထားသော response
 * နှင့် local data မှသာ update လုပ်မည်။
 */
private final Map<String, Integer>
        categoryCounts =
        new HashMap<>();

/*
 * Exact total သိသော categories များ။
 * Exact မသိသေးပါက UI တွင် 20+ လိုပြမည်။
 */
private final Set<String>
        exactCategoryCounts =
        new HashSet<>();

    private String category = "movies";
    private String search = "";

    private int currentPage = 0;
    private boolean hasMore = true;
    private boolean isLoading = false;
    private int requestGeneration = 0;

    private final String[][] categories = {
        {"Movies", "movies"},
        {"Free 18+", "series"},
        {"18+ VIP", "lugyi"},
        {"Continue", "continue"},
        {"Recent", "recent"},
        {"Downloads", "downloads"},
        {"Favorites", "favorites"}
};


    private final ActivityResultLauncher<Intent>
            authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    result -> {
    updateAccountButtons();

    PremiumExpiryDialog.showIfNeeded(
            MainActivity.this
    );

    if (

                                result.getResultCode()
                                        == RESULT_OK &&
                                "favorites".equals(category)
                        ) {
                            resetAndLoad();
                        }
                    }
            );

    @Override
protected void onCreate(Bundle savedInstanceState) {
    

    super.onCreate(savedInstanceState);

    

       ApiClient.initialize(this);
    setContentView(R.layout.activity_main);

    recycler = findViewById(R.id.recycler);

        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.errorText);
        searchInput = findViewById(R.id.searchInput);
        categoryBar =
        findViewById(R.id.categoryBar);

searchHistoryContainer =
        findViewById(
                R.id.searchHistoryContainer
        );

searchHistoryRow =
        findViewById(
                R.id.searchHistoryRow
        );

clearSearchHistoryButton =
        findViewById(
                R.id.clearSearchHistoryButton
        );

sectionTitle =
        findViewById(R.id.sectionTitle);

localClearButton =
        findViewById(R.id.localClearButton);

accountButton =
        findViewById(R.id.accountButton);

premiumButton =
        findViewById(R.id.premiumButton);
vipPlanBanner =
        findViewById(R.id.vipPlanBanner);



        setupRecycler();
        setupCategories();
        setupSearch();
        setupAccountButtons();
setupVipBanner();
setupDoubleBackExit();
setupLocalFeatureControls();

refreshSearchHistory();


        errorText.setOnClickListener(view -> {
            if (!isLoading) {
                loadNextPage();
            }
        });

        updateAccountButtons();
refreshProfileIfNeeded();
resetAndLoad();

/*
 * Cached vipUntil ကိုပဲဖတ်သောကြောင့်
 * ဒီစစ်ဆေးမှုမှာ API request အသစ်မရှိပါ။
 *
 * Splash view ပျောက်ပြီး main layout attach ဖြစ်မှ
 * warning dialog ကိုပြပါမယ်။
 */
recycler.post(
        () -> PremiumExpiryDialog.showIfNeeded(
                MainActivity.this
        )
);

    }

    @Override
protected void onResume() {
    super.onResume();

    updateAccountButtons();

PremiumExpiryDialog.showIfNeeded(
        MainActivity.this
);
    refreshProfileIfNeeded();
    refreshSearchHistory();

    refreshLocalCategoryCounts();
refreshCategoryLabels();

if (isLocalCategory(category)) {
    loadLocalCategory();
} else if (adapter != null) {
    /*
     * Poster အားလုံးကို ပြန် bind မလုပ်ဘဲ
     * progress bar များကိုသာ update လုပ်မည်။
     */
    adapter.refreshProgressSnapshot();
}

}


    private void setupRecycler() {
    int screenWidthDp =
            getResources()
                    .getConfiguration()
                    .screenWidthDp;

    int spanCount;

    /*
     * Phone မှာ title/meta ပါသော card ဖြစ်သောကြောင့်
     * 2 columns က ဖတ်ရလွယ်ပြီး poster size ကောင်းသည်။
     */
    if (screenWidthDp >= 840) {
        spanCount = 5;
    } else if (screenWidthDp >= 600) {
        spanCount = 3;
    } else {
        spanCount = 2;
    }

    layoutManager =
        new GridLayoutManager(
                this,
                spanCount
        );

layoutManager.setInitialPrefetchItemCount(
        spanCount * 2
);

recycler.setLayoutManager(
        layoutManager
);

recycler.setHasFixedSize(true);
recycler.setItemAnimator(null);

recycler.setItemViewCacheSize(
        Math.max(
                6,
                spanCount * 3
        )
);

recycler.getRecycledViewPool()
        .setMaxRecycledViews(
                0,
                Math.max(
                        12,
                        spanCount * 4
                )
        );


    recycler.getRecycledViewPool()
            .setMaxRecycledViews(
                    0,
                    12
            );

    adapter = new TitleAdapter(
            item -> {
                Intent intent =
                        new Intent(
                                MainActivity.this,
                                DetailActivity.class
                        );

                String slug =
                        item.optString(
                                "slug",
                                ""
                        ).trim();

                if (slug.isEmpty()) {
                    Toast.makeText(
                            MainActivity.this,
                            "ဒီ local item မှာ slug မရှိပါ။",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                intent.putExtra(
                        "slug",
                        slug
                );

                startActivity(intent);
            },
            this::removeFavoriteFromList
    );

    recycler.setAdapter(adapter);

    recycler.addOnScrollListener(
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(
                        RecyclerView recyclerView,
                        int dx,
                        int dy
                ) {
                    super.onScrolled(
                            recyclerView,
                            dx,
                            dy
                    );

                    if (
                            dy <= 0 ||
                            isLoading ||
                            !hasMore ||
                            isLocalCategory(category) ||
                            "favorites".equals(category)
                    ) {
                        return;
                    }

                    int totalCount =
                            layoutManager
                                    .getItemCount();

                    int lastVisible =
                            layoutManager
                                    .findLastVisibleItemPosition();

                    if (
                            totalCount <= 0 ||
                            lastVisible ==
                                    RecyclerView.NO_POSITION
                    ) {
                        return;
                    }

                    /*
                     * နောက်ဆုံး ၂ တန်းနားရောက်မှသာ
                     * next page ကိုကြို load လုပ်မည်။
                     *
                     * မူရင်း totalCount - 9 ကြောင့်
                     * ပထမ screen ကို စဆွဲတာနဲ့ request
                     * ဝင်နိုင်သည်။
                     */
                    int prefetchDistance =
                            Math.max(
                                    layoutManager
                                            .getSpanCount() * 2,
                                    4
                            );

                    if (
                            lastVisible >=
                                    totalCount -
                                            1 -
                                            prefetchDistance
                    ) {
                        loadNextPage();
                    }
                }
            }
    );
}


    private void setupCategories() {
    categoryBar.removeAllViews();

    /*
     * Local categories များကို network request
     * မလိုဘဲ count အရင်တွက်နိုင်သည်။
     */
    refreshLocalCategoryCounts();

    for (String[] item : categories) {
        String label = item[0];
        String value = item[1];

        Button button =
                new Button(this);

        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT,
                        dp(38)
                );

        params.setMarginStart(dp(3));
        params.setMarginEnd(dp(3));

        button.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        button.setLayoutParams(params);
        button.setTag(value);

        updateCategoryButtonText(
                button,
                label,
                value
        );

        button.setOnClickListener(view -> {
            if (
                    "favorites".equals(value) &&
                    !SessionManager.isLoggedIn()
            ) {
                openLogin();
                return;
            }

            if (value.equals(category)) {
                return;
            }

            category = value;
            search = "";

            searchInput.setText("");

            boolean local =
                    isLocalCategory(category);

            searchInput.setVisibility(
                    View.VISIBLE
            );

            sectionTitle.setText(label);

            localClearButton.setVisibility(
                    local
                            ? View.VISIBLE
                            : View.GONE
            );

            refreshSearchHistory();
            updateCategoryButtons();

            recycler.scrollToPosition(0);
            resetAndLoad();
        });

        categoryBar.addView(button);
    }

    updateCategoryButtons();
}


    private void updateCategoryButtons() {
    for (
            int index = 0;
            index < categoryBar.getChildCount();
            index++
    ) {
        View child =
                categoryBar.getChildAt(index);

        if (!(child instanceof Button)) {
            continue;
        }

        Button button =
                (Button) child;

        String value =
                String.valueOf(
                        button.getTag()
                );

        boolean selected =
                category.equals(value);

        updateCategoryButtonText(
                button,
                categoryLabel(value),
                value
        );

        button.setTextColor(
                selected
                        ? Color.WHITE
                        : Color.parseColor(
                                "#A8ADB8"
                        )
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setShape(
                GradientDrawable.RECTANGLE
        );

        background.setCornerRadius(
                dp(50)
        );

        background.setColor(
                selected
                        ? Color.parseColor(
                                "#E50914"
                        )
                        : Color.parseColor(
                                "#1A1D24"
                        )
        );

        if (!selected) {
            background.setStroke(
                    dp(1),
                    Color.parseColor(
                            "#303540"
                    )
            );
        }

        button.setBackground(background);
    }
}

private String categoryLabel(
        String value
) {
    for (String[] item : categories) {
        if (item[1].equals(value)) {
            return item[0];
        }
    }

    return value;
}

private void updateCategoryButtonText(
        Button button,
        String label,
        String value
) {
    Integer count =
            categoryCounts.get(value);

    if (count == null) {
        button.setText(label);
        return;
    }

    boolean exact =
            exactCategoryCounts
                    .contains(value);

    String countText =
            String.valueOf(
                    Math.max(0, count)
            );

    if (!exact) {
        countText += "+";
    }

    button.setText(
            label +
                    " (" +
                    countText +
                    ")"
    );
}

private void refreshCategoryLabels() {
    if (categoryBar == null) {
        return;
    }

    for (
            int index = 0;
            index < categoryBar.getChildCount();
            index++
    ) {
        View child =
                categoryBar.getChildAt(index);

        if (!(child instanceof Button)) {
            continue;
        }

        Button button =
                (Button) child;

        String value =
                String.valueOf(
                        button.getTag()
                );

        updateCategoryButtonText(
                button,
                categoryLabel(value),
                value
        );
    }
}

private void refreshLocalCategoryCounts() {
    int continueCount =
            LocalStore
                    .getContinueWatching()
                    .size();

    int recentCount =
            LocalStore
                    .getRecentlyViewed()
                    .size();

    int downloadCount =
            LocalStore
                    .getDownloadHistory()
                    .size();

    categoryCounts.put(
            "continue",
            continueCount
    );

    categoryCounts.put(
            "recent",
            recentCount
    );

    categoryCounts.put(
            "downloads",
            downloadCount
    );

    exactCategoryCounts.add(
            "continue"
    );

    exactCategoryCounts.add(
            "recent"
    );

    exactCategoryCounts.add(
            "downloads"
    );
}

/*
 * API response ထဲမှာ total ပါပြီးသားဆို
 * request အသစ်မခေါ်ဘဲ ယူသုံးမည်။
 */
private int extractTotalCount(
        JSONObject json
) {
    if (json == null) {
        return -1;
    }

    int direct =
            readNonNegativeInt(
                    json,
                    "total"
            );

    if (direct >= 0) {
        return direct;
    }

    direct =
            readNonNegativeInt(
                    json,
                    "totalCount"
            );

    if (direct >= 0) {
        return direct;
    }

    direct =
            readNonNegativeInt(
                    json,
                    "total_count"
            );

    if (direct >= 0) {
        return direct;
    }

    JSONObject pagination =
            json.optJSONObject(
                    "pagination"
            );

    direct =
            readNonNegativeInt(
                    pagination,
                    "total"
            );

    if (direct >= 0) {
        return direct;
    }

    direct =
            readNonNegativeInt(
                    pagination,
                    "totalCount"
            );

    if (direct >= 0) {
        return direct;
    }

    JSONObject meta =
            json.optJSONObject("meta");

    direct =
            readNonNegativeInt(
                    meta,
                    "total"
            );

    if (direct >= 0) {
        return direct;
    }

    return readNonNegativeInt(
            meta,
            "totalCount"
    );
}

private int readNonNegativeInt(
        JSONObject object,
        String key
) {
    if (
            object == null ||
            !object.has(key) ||
            object.isNull(key)
    ) {
        return -1;
    }

    int value =
            object.optInt(
                    key,
                    -1
            );

    return value >= 0
            ? value
            : -1;
}

private void updateOnlineCategoryCount(
        JSONObject response
) {
    /*
     * Search result count ကို category total
     * အဖြစ် မသတ်မှတ်ပါ။
     */
    if (
            search != null &&
            !search.trim().isEmpty()
    ) {
        return;
    }

    int total =
            extractTotalCount(response);

    if (total >= 0) {
        categoryCounts.put(
                category,
                total
        );

        exactCategoryCounts.add(
                category
        );
    } else {
        categoryCounts.put(
                category,
                allItems.size()
        );

        if (hasMore) {
            exactCategoryCounts.remove(
                    category
            );
        } else {
            exactCategoryCounts.add(
                    category
            );
        }
    }

    refreshCategoryLabels();
}

    private void setupSearch() {
        searchInput.setOnEditorActionListener(
                (view, actionId, event) -> {
                    boolean searchAction =
                            actionId ==
                                    EditorInfo.IME_ACTION_SEARCH;

                    boolean enter =
                            event != null &&
                                    event.getAction() ==
                                            KeyEvent.ACTION_DOWN &&
                                    event.getKeyCode() ==
                                            KeyEvent.KEYCODE_ENTER;

                    if (!searchAction && !enter) {
                        return false;
                    }

                    search = searchInput
        .getText()
        .toString()
        .trim();

if (!search.isEmpty()) {
    LocalStore.addSearch(search);
    refreshSearchHistory();
}

hideKeyboard();

                    recycler.scrollToPosition(0);
                    resetAndLoad();

                    return true;
                }
        );
    }

    private void setupAccountButtons() {
    accountButton.setOnClickListener(view -> {
        if (SessionManager.isLoggedIn()) {
            startActivity(
                    new Intent(
                            this,
                            ProfileActivity.class
                    )
            );
        } else {
            openLogin();
        }
    });

   premiumButton.setOnClickListener(view -> {
    if (!SessionManager.isLoggedIn()) {
        openLogin();
        return;
    }

    if (
            SessionManager.getVipUntil()
                    <= System.currentTimeMillis()
    ) {
        PremiumDialog.show(this);
        return;
    }

    startActivity(
            new Intent(
                    this,
                    ProfileActivity.class
            )
    );
});

}

private void setupVipBanner() {
    if (vipPlanBanner == null) {
        return;
    }

    vipPlanBanner.setOnClickListener(view -> {
        String telegramUrl =
                "https://t.me/iqowoq";

        Intent telegramIntent =
                new Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                                telegramUrl
                        )
                );

        try {
            startActivity(telegramIntent);
        } catch (
                android.content.ActivityNotFoundException error
        ) {
            Toast.makeText(
                    MainActivity.this,
                    "Telegram link ကိုဖွင့်နိုင်သော app မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();
        }
    });
}

private void setupDoubleBackExit() {
    getOnBackPressedDispatcher()
            .addCallback(
                    this,
                    new OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            long now =
                                    SystemClock.uptimeMillis();

                            if (
                                    now - lastBackPressedAt
                                            <= BACK_EXIT_INTERVAL_MS
                            ) {
                                setEnabled(false);

                                if (
                                        android.os.Build.VERSION.SDK_INT
                                                >= android.os.Build.VERSION_CODES.LOLLIPOP
                                ) {
                                    finishAndRemoveTask();
                                } else {
                                    finishAffinity();
                                }

                                return;
                            }

                            lastBackPressedAt = now;

                            Toast.makeText(
                                    MainActivity.this,
                                    "App မှထွက်ရန် Back ကို ထပ်နှိပ်ပါ။",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );
}




    private void updateAccountButtons() {
    boolean loggedIn =
            SessionManager.isLoggedIn();

    accountButton.setText(
            loggedIn
                    ? SessionManager.getUsername()
                    : "LOGIN"
    );

    if (!loggedIn) {
        premiumButton.setVisibility(
                View.GONE
        );

        return;
    }

    premiumButton.setText(
            SessionManager.getPremiumLabel()
    );

    premiumButton.setVisibility(
            View.VISIBLE
    );
}

private void openLogin() {
    Intent intent =
            new Intent(
                    MainActivity.this,
                    AuthActivity.class
            );

    authLauncher.launch(intent);
}
private void refreshProfileIfNeeded() {
    if (
            !SessionManager.isLoggedIn() ||
            profileRefreshInFlight ||
            !SessionManager.isProfileRefreshDue(
                    PROFILE_CACHE_MS
            )
    ) {
        return;
    }

    profileRefreshInFlight = true;

    ApiClient.get(
            "auth/me",
            new ApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject json) {
                    runOnUiThread(() -> {
                        profileRefreshInFlight = false;

                        JSONObject user =
                                json.optJSONObject("user");

                        /*
                         * user == null ဆိုတာ session တကယ်
                         * သက်တမ်းကုန်/ဖျက်ခံထားရတာဖြစ်သည်။
                         * VIP ပဲ cancel ခံရရင် user object
                         * ရှိနေပြီး vipUntil = 0 ဖြစ်ရမည်။
                         */
                        if (user == null) {
                            SessionManager.clear();
                            updateAccountButtons();
                            return;
                        }

                        String csrf =
                                json.optString(
                                        "csrf",
                                        SessionManager.getCsrf()
                                );

                        SessionManager.saveAuth(
        csrf,
        user.optString(
                "username",
                SessionManager.getUsername()
        ),
        user.optString(
                "email",
                SessionManager.getEmail()
        ),
        user.optLong(
                "vipUntil",
                0L
        ),
        user.optInt(
                "planMonths",
                0
        )
);


                        updateAccountButtons();
                    });
                }

                @Override
                public void onError(Exception error) {
                    /*
                     * Network error ဖြစ်ရုံနဲ့ session မရှင်းပါ။
                     * Cached information ကိုဆက်ပြမယ်။
                     */
                    runOnUiThread(() ->
                            profileRefreshInFlight = false
                    );
                }
            }
    );
}


        

    private void removeFavoriteFromList(
            JSONObject item
    ) {
        if (
                item == null ||
                !"favorites".equals(category)
        ) {
            return;
        }

        String titleId =
                item.optString("id", "");

        if (titleId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Movie ID မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            adapter.notifyDataSetChanged();
            return;
        }

        ApiClient.delete(
                "favorites/" +
                        ApiClient.encode(titleId),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            for (
                                    int index =
                                            allItems.size() - 1;
                                    index >= 0;
                                    index--
                            ) {
                                JSONObject current =
                                        allItems.get(index);

                                if (
                                        titleId.equals(
                                                current.optString(
                                                        "id",
                                                        ""
                                                )
                                        )
                                ) {
                                    allItems.remove(index);
                                }
                            }

                            adapter.submitList(
        new ArrayList<>(allItems)
);

categoryCounts.put(
        "favorites",
        allItems.size()
);

exactCategoryCounts.add(
        "favorites"
);

refreshCategoryLabels();


                            if (allItems.isEmpty()) {
                                errorText.setText(
                                        "Favorite မရှိသေးပါ။"
                                );

                                errorText.setVisibility(
                                        View.VISIBLE
                                );
                            } else {
                                errorText.setVisibility(
                                        View.GONE
                                );
                            }

                            Toast.makeText(
                                    MainActivity.this,
                                    "Favorite မှ ဖယ်ရှားပြီးပါပြီ။",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            adapter.notifyDataSetChanged();

                            Toast.makeText(
                                    MainActivity.this,
                                    safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }


    private void resetAndLoad() {
    requestGeneration++;

    adapter.setFavoriteMode(
            "favorites".equals(category)
    );

    currentPage = 0;
    hasMore = true;
    isLoading = false;

    allItems.clear();

    adapter.submitList(
            new ArrayList<>()
    );

    errorText.setVisibility(View.GONE);

    if (isLocalCategory(category)) {
        loadLocalCategory();
        return;
    }

    loadNextPage();
}


    private void loadNextPage() {
        if (isLoading || !hasMore) {
            return;
        }

        if (
                "favorites".equals(category) &&
                !SessionManager.isLoggedIn()
        ) {
            openLogin();
            return;
        }

        isLoading = true;

        final int generation =
                requestGeneration;

        final int requestedPage =
                currentPage + 1;

        progress.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);

        String path;

        if ("favorites".equals(category)) {
            path = "favorites";
        } else {
            path =
                    "titles?category=" +
                            ApiClient.encode(category) +
                            "&page=" +
                            requestedPage;

            if (!search.isEmpty()) {
                path +=
                        "&q=" +
                                ApiClient.encode(search);
            }
        }

        ApiClient.getCached(
        path,
        60L * 60L * 1000L,
        new ApiClient.Callback() {

                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            if (
                                    generation !=
                                            requestGeneration
                            ) {
                                return;
                            }

                            isLoading = false;
                            progress.setVisibility(
                                    View.GONE
                            );

                            JSONArray items =
                                    json.optJSONArray("items");

                            if (items != null) {
                                for (
                                        int index = 0;
                                        index < items.length();
                                        index++
                                ) {
                                    JSONObject item =
                                            items.optJSONObject(index);

                                    if (
        item != null &&
        matchesSearch(item)
) {
    allItems.add(item);
}

                                }
                            }

                            if ("favorites".equals(category)) {
    currentPage = 1;
    hasMore = false;
} else {
    currentPage =
            json.optInt(
                    "page",
                    requestedPage
            );

    hasMore =
            json.optBoolean(
                    "hasMore",
                    false
            );
}

/*
 * လက်ရှိ response ထဲက total သို့မဟုတ်
 * load ပြီးသား item count ကို tab မှာပြမည်။
 * ဒီနေရာမှာ API request အသစ်မခေါ်ပါ။
 */
updateOnlineCategoryCount(json);

/*
 * Mutable list ကိုတိုက်ရိုက်မပို့ရ။
 * ListAdapter အတွက် list copy အသစ်ပို့ပါ။
 */
adapter.submitList(
        new ArrayList<>(allItems)
);


                            if (allItems.isEmpty()) {
                                errorText.setText(
                                        "favorites".equals(category)
                                                ? "Favorite မရှိသေးပါ။"
                                                : search.isEmpty()
                                                ? "ဇာတ်ကား မရှိသေးပါ။"
                                                : "ရှာထားသော ဇာတ်ကား မတွေ့ပါ။"
                                );

                                errorText.setVisibility(
                                        View.VISIBLE
                                );
                            }
                        });
                    }

                    @Override
public void onError(Exception error) {
    runOnUiThread(() -> {
        if (
                generation !=
                        requestGeneration
        ) {
            return;
        }

        isLoading = false;

        progress.setVisibility(
                View.GONE
        );

        String message =
                safeMessage(error);

        /*
         * ပထမ page/movie တွေရှိနေပြီး pagination
         * request ပဲမအောင်မြင်ရင် cards ကြားမှာ
         * error TextView မပြပါ။
         */
        if (!allItems.isEmpty()) {
            hasMore = false;

            errorText.setVisibility(
                    View.GONE
            );

            Toast.makeText(
                    MainActivity.this,
                    message,
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        /*
         * ဘာ item မှမရှိသေးတဲ့ initial request
         * မအောင်မြင်မှသာ retry message ပြမယ်။
         */
        errorText.setText(
                message +
                        "\n\nပြန်စမ်းရန်နှိပ်ပါ။"
        );

        errorText.setVisibility(
                View.VISIBLE
        );
    });
}

                }
        );
    }

    private String safeMessage(
        Exception error
) {
    if (isNetworkError(error)) {
        return "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။";
    }

    if (
            error == null ||
            error.getMessage() == null ||
            error.getMessage()
                    .trim()
                    .isEmpty()
    ) {
        return "Request မအောင်မြင်ပါ။";
    }

    String message =
            error.getMessage().trim();

    /*
     * Server URL/domain ပါလာနိုင်သော raw messages
     * ကို UI ပေါ် တိုက်ရိုက်မတင်ပါ။
     */
    String lower =
            message.toLowerCase(
                    java.util.Locale.US
            );

    if (
            lower.contains("unable to resolve host") ||
            lower.contains("no address associated") ||
            lower.contains("failed to connect") ||
            lower.contains("connection refused")
    ) {
        return "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။";
    }

    return message;
}

private boolean isNetworkError(
        Throwable error
) {
    Throwable current = error;

    while (current != null) {
        if (
                current instanceof
                        java.net.UnknownHostException ||
                current instanceof
                        java.net.SocketTimeoutException ||
                current instanceof
                        java.net.ConnectException ||
                current instanceof
                        java.net.NoRouteToHostException ||
                current instanceof
                        javax.net.ssl.SSLException
        ) {
            return true;
        }

        current = current.getCause();
    }

    return false;
}


    private void hideKeyboard() {
        View current = getCurrentFocus();

        if (current == null) {
            return;
        }

        InputMethodManager manager =
                (InputMethodManager)
                        getSystemService(
                                Context.INPUT_METHOD_SERVICE
                        );

        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    current.getWindowToken(),
                    0
            );
        }

        current.clearFocus();
    }
private boolean isLocalCategory(
        String value
) {
    return "continue".equals(value) ||
            "recent".equals(value) ||
            "downloads".equals(value);
}

private void loadLocalCategory() {
    progress.setVisibility(View.GONE);
    isLoading = false;
    hasMore = false;

    List<JSONObject> items;

    switch (category) {
        case "continue":
            items =
                    LocalStore
                            .getContinueWatching();

            sectionTitle.setText(
                    "Continue Watching"
            );
            break;

        case "downloads":
            items =
                    LocalStore
                            .getDownloadHistory();

            sectionTitle.setText(
                    "Download History"
            );
            break;

        case "recent":
        default:
            items =
                    LocalStore
                            .getRecentlyViewed();

            sectionTitle.setText(
                    "Recently Viewed"
            );
            break;
    }
/*
 * Search filter မလုပ်မီ category အပြည့်၏
 * local count ကိုသိမ်းမည်။
 */
categoryCounts.put(
        category,
        items.size()
);

exactCategoryCounts.add(
        category
);

refreshCategoryLabels();

    if (
            search != null &&
            !search.trim().isEmpty()
    ) {
        List<JSONObject> filteredItems =
                new ArrayList<>();

        for (JSONObject item : items) {
            if (matchesSearch(item)) {
                filteredItems.add(item);
            }
        }

        items = filteredItems;
    }

    allItems.clear();
    allItems.addAll(items);

    adapter.setFavoriteMode(false);

    adapter.submitList(
            new ArrayList<>(allItems)
    );

    if (allItems.isEmpty()) {
        String message;

        if ("continue".equals(category)) {
            message =
                    "Continue Watching မရှိသေးပါ။";
        } else if (
                "downloads".equals(category)
        ) {
            message =
                    "Download history မရှိသေးပါ။";
        } else {
            message =
                    "Recently Viewed မရှိသေးပါ။";
        }

        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    } else {
        errorText.setVisibility(View.GONE);
    }
}

private void setupLocalFeatureControls() {
    clearSearchHistoryButton
            .setOnClickListener(view -> {
                LocalStore.clearSearchHistory();
                refreshSearchHistory();
            });

    localClearButton.setOnClickListener(
            view -> showClearHistoryDialog()
    );
}

private void showClearHistoryDialog() {
    if (!isLocalCategory(category)) {
        return;
    }

    String title;
    String message;

    if ("continue".equals(category)) {
        title = "Clear Continue Watching";
        message =
                "ကြည့်လက်စ progress များကို ရှင်းမှာ သေချာပါသလား?";
    } else if ("downloads".equals(category)) {
        title = "Clear Download History";
        message =
                "Download history အားလုံးကို ရှင်းမှာ သေချာပါသလား?";
    } else {
        title = "Clear Recently Viewed";
        message =
                "ကြည့်ရှုခဲ့သော history နှင့် watch progress များကို ရှင်းမှာ သေချာပါသလား?";
    }

    android.app.Dialog dialog =
            new android.app.Dialog(this);

    dialog.setContentView(
            R.layout.dialog_clear_history
    );

    dialog.setCancelable(true);
    dialog.setCanceledOnTouchOutside(true);

    android.view.Window window =
            dialog.getWindow();

    if (window != null) {
        window.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(
                        Color.TRANSPARENT
                )
        );

        window.addFlags(
                android.view.WindowManager.LayoutParams
                        .FLAG_DIM_BEHIND
        );

        android.view.WindowManager.LayoutParams attributes =
                window.getAttributes();

        attributes.dimAmount = 0.80f;
        window.setAttributes(attributes);
    }

    TextView titleView =
            dialog.findViewById(
                    R.id.clearDialogTitle
            );

    TextView messageView =
            dialog.findViewById(
                    R.id.clearDialogMessage
            );

    TextView confirmButton =
            dialog.findViewById(
                    R.id.clearDialogConfirm
            );

    TextView cancelButton =
            dialog.findViewById(
                    R.id.clearDialogCancel
            );

    titleView.setText(title);
    messageView.setText(message);

    cancelButton.setOnClickListener(
            view -> dialog.dismiss()
    );

    confirmButton.setOnClickListener(view -> {
        if ("continue".equals(category)) {
            LocalStore.clearContinueWatching();
        } else if ("downloads".equals(category)) {
            LocalStore.clearDownloadHistory();
        } else {
            LocalStore.clearRecentlyViewed();
        }

        dialog.dismiss();
        loadLocalCategory();
    });

    dialog.show();

    if (window != null) {
        int screenWidth =
                getResources()
                        .getDisplayMetrics()
                        .widthPixels;

        int dialogWidth =
                Math.min(
                        (int) (screenWidth * 0.88f),
                        dp(400)
                );

        window.setLayout(
                dialogWidth,
                android.view.ViewGroup.LayoutParams
                        .WRAP_CONTENT
        );
    }
}


private void refreshSearchHistory() {
    if (
            searchHistoryContainer == null ||
            searchHistoryRow == null
    ) {
        return;
    }

    searchHistoryContainer.removeAllViews();

    List<String> history =
            LocalStore.getSearchHistory();

    boolean show =
        !history.isEmpty();


    searchHistoryRow.setVisibility(
            show
                    ? View.VISIBLE
                    : View.GONE
    );

    if (!show) {
        return;
    }

    for (String query : history) {
        TextView chip =
                new TextView(this);

        chip.setText(query);
        chip.setSingleLine(true);
        chip.setTextSize(12);
        chip.setTextColor(Color.WHITE);

        chip.setPadding(
                dp(13),
                dp(8),
                dp(13),
                dp(8)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.parseColor("#1A1D24")
        );

        background.setCornerRadius(
                dp(50)
        );

        background.setStroke(
                dp(1),
                Color.parseColor("#353A45")
        );

        chip.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT,
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT
                );

        params.setMarginEnd(dp(7));

        chip.setLayoutParams(params);

        chip.setOnClickListener(view -> {
            search = query;
            searchInput.setText(query);
            searchInput.setSelection(
                    query.length()
            );

            hideKeyboard();
            recycler.scrollToPosition(0);
            resetAndLoad();
        });

        searchHistoryContainer.addView(chip);
    }
}
private boolean matchesSearch(
        JSONObject item
) {
    if (item == null) {
        return false;
    }

    String query =
            search == null
                    ? ""
                    : search.trim()
                    .toLowerCase(
                            java.util.Locale.US
                    );

    if (query.isEmpty()) {
        return true;
    }

    String searchableText =
            (
                    item.optString("title", "") + " " +
                    item.optString("year", "") + " " +
                    item.optString("rating", "") + " " +
                    item.optString("category", "") + " " +
                    item.optString("genres", "")
            ).toLowerCase(
                    java.util.Locale.US
            );

    return searchableText.contains(query);
}

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
