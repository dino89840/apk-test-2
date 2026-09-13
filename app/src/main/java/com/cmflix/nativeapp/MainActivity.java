package com.cmflix.nativeapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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
private Button themeButton;


private static final long PROFILE_CACHE_MS =
        6L * 60L * 60L * 1000L;

private boolean profileRefreshInFlight = false;


    private TitleAdapter adapter;
    private GridLayoutManager layoutManager;

    private final List<JSONObject> allItems =
            new ArrayList<>();

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
        SplashScreen.installSplashScreen(this);
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

themeButton =
        findViewById(R.id.themeButton);



        setupRecycler();
        setupCategories();
        setupSearch();
        setupAccountButtons();
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

    }

    @Override
protected void onResume() {
    super.onResume();

    updateAccountButtons();
    refreshProfileIfNeeded();
    refreshSearchHistory();

    if (isLocalCategory(category)) {
        loadLocalCategory();
    } else if (adapter != null) {
        /*
         * Online list ထဲက poster progress line ကို
         * player ပြန်လာချိန် update လုပ်ရန်။
         */
        adapter.notifyDataSetChanged();
    }
}


    private void setupRecycler() {
        int screenWidthDp =
        getResources()
                .getConfiguration()
                .screenWidthDp;

int spanCount;

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

        recycler.setLayoutManager(layoutManager);

        /*
         * Card height တည်ငြိမ်နေသောကြောင့်
         * layout calculation လျှော့နိုင်သည်။
         */
        recycler.setHasFixedSize(true);

        /*
         * Pagination တိုင်း item animation ကြောင့်
         * grid လှုပ်ခြင်းမဖြစ်စေရန်။
         */
        recycler.setItemAnimator(null);
        recycler.setItemViewCacheSize(12);

        recycler.getRecycledViewPool()
                .setMaxRecycledViews(0, 30);

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

intent.putExtra("slug", slug);
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
                                "favorites".equals(category)
                        ) {
                            return;
                        }

                        int visibleCount =
                                layoutManager.getChildCount();

                        int totalCount =
                                layoutManager.getItemCount();

                        int firstVisible =
                                layoutManager
                                        .findFirstVisibleItemPosition();

                        if (
                                firstVisible + visibleCount
                                        >= totalCount - 9
                        ) {
                            loadNextPage();
                        }
                    }
                }
        );
    }

    private void setupCategories() {
        categoryBar.removeAllViews();

        for (String[] item : categories) {
            String label = item[0];
            String value = item[1];

            Button button = new Button(this);

            button.setText(label);
            button.setAllCaps(false);
            button.setTextSize(13);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);

            button.setPadding(
        dp(6),
        dp(9),
        dp(6),
        dp(9)
);

LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
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
        local ||
        "favorites".equals(category)
                ? View.GONE
                : View.VISIBLE
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

            Button button = (Button) child;

            boolean selected =
                    category.equals(
                            String.valueOf(button.getTag())
                    );

            button.setTextColor(
                    selected
                            ? Color.WHITE
                            : Color.parseColor("#A8ADB8")
            );

            GradientDrawable background =
                    new GradientDrawable();

            background.setShape(
                    GradientDrawable.RECTANGLE
            );

            background.setCornerRadius(dp(50));

            background.setColor(
                    selected
                            ? Color.parseColor("#E50914")
                            : Color.parseColor("#1A1D24")
            );

            if (!selected) {
                background.setStroke(
                        dp(1),
                        Color.parseColor("#303540")
                );
            }

            button.setBackground(background);
        }
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





    private void updateAccountButtons() {
    boolean loggedIn =
            SessionManager.isLoggedIn();

    accountButton.setText(
            loggedIn
                    ? SessionManager.getUsername()
                    : "LOGIN"
    );

    if (loggedIn) {
        premiumButton.setText(
                SessionManager.getPremiumLabel()
        );

        premiumButton.setVisibility(
                View.VISIBLE
        );
    } else {
        premiumButton.setVisibility(
                View.GONE
        );
    }
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
} else {
    loadNextPage();
}

if (isLocalCategory(category)) {
    loadLocalCategory();
    return;
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
        10L * 60L * 1000L,
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

                                    if (item != null) {
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

                            errorText.setText(
                                    safeMessage(error) +
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

    private String safeMessage(Exception error) {
        if (
                error == null ||
                error.getMessage() == null ||
                error.getMessage().trim().isEmpty()
        ) {
            return "Request မအောင်မြင်ပါ။";
        }

        return error.getMessage();
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
    themeButton.setText(
            LocalStore.isAmoledTheme()
                    ? "AMOLED"
                    : "DARK"
    );

    themeButton.setOnClickListener(view -> {
        boolean amoled =
                LocalStore.toggleAmoledTheme();

        themeButton.setText(
                amoled
                        ? "AMOLED"
                        : "DARK"
        );

        recreate();
    });

    clearSearchHistoryButton
            .setOnClickListener(view -> {
                LocalStore.clearSearchHistory();
                refreshSearchHistory();
            });

    localClearButton.setOnClickListener(view -> {
        if (!isLocalCategory(category)) {
            return;
        }

        String message;

        if ("continue".equals(category)) {
            message =
                    "Continue Watching ကို ရှင်းမလား?";
        } else if (
                "downloads".equals(category)
        ) {
            message =
                    "Download history ကို ရှင်းမလား?";
        } else {
            message =
                    "Recently Viewed နဲ့ watch progress အားလုံးကို ရှင်းမလား?";
        }

        new AlertDialog.Builder(this)
                .setTitle("Clear local history")
                .setMessage(message)
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Clear",
                        (dialog, which) -> {
                            if (
                                    "continue".equals(
                                            category
                                    )
                            ) {
                                LocalStore
                                        .clearContinueWatching();
                            } else if (
                                    "downloads".equals(
                                            category
                                    )
                            ) {
                                LocalStore
                                        .clearDownloadHistory();
                            } else {
                                LocalStore
                                        .clearRecentlyViewed();
                            }

                            loadLocalCategory();
                        }
                )
                .show();
    });
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
            !history.isEmpty() &&
            !isLocalCategory(category) &&
            !"favorites".equals(category);

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

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
