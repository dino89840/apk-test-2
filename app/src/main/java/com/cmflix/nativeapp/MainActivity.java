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


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /*
     * Splash ကို အနည်းဆုံးပြထားမည့်အချိန်။
     * 900L = 0.9 second
     */
    private static final long MIN_SPLASH_MS = 900L;

    /*
     * Splash ပျောက်သွားချိန် fade/zoom animation ကြာချိန်။
     */
    private static final long SPLASH_EXIT_MS = 320L;

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
    SplashScreen splashScreen =
            SplashScreen.installSplashScreen(this);

    super.onCreate(savedInstanceState);

    /*
     * Screen rotation ဖြစ်ချိန် splash ကို ထပ်မစောင့်စေရန်
     * savedInstanceState ရှိရင် delay မပေးပါ။
     */
    final long keepSplashUntil =
            SystemClock.uptimeMillis() +
                    (
                            savedInstanceState == null
                                    ? MIN_SPLASH_MS
                                    : 0L
                    );

    splashScreen.setKeepOnScreenCondition(
            () -> SystemClock.uptimeMillis()
                    < keepSplashUntil
    );

    /*
     * မည်သည့် PNG/vector logo ကိုသုံးထားသည်ဖြစ်စေ
     * splash ပျောက်ချိန်မှာ logo zoom-out နှင့်
     * screen cross-fade animation ရပါမည်။
     */
    splashScreen.setOnExitAnimationListener(
            splashScreenView -> {
                View splashView =
                        splashScreenView.getView();

                View iconView =
                        splashScreenView.getIconView();

                View contentView =
                        findViewById(android.R.id.content);

                DecelerateInterpolator interpolator =
                        new DecelerateInterpolator();

                /*
                 * Main screen ကို ဖြည်းဖြည်းပေါ်လာစေမည်။
                 */
                contentView.setAlpha(0f);
                contentView.setScaleX(0.985f);
                contentView.setScaleY(0.985f);

                contentView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(SPLASH_EXIT_MS + 80L)
                        .setInterpolator(interpolator)
                        .start();

                /*
                 * Splash logo ကို အနည်းငယ်ချဲ့ပြီး
                 * ပျောက်သွားစေမည်။
                 */
                iconView.animate()
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .alpha(0f)
                        .setDuration(SPLASH_EXIT_MS)
                        .setInterpolator(interpolator)
                        .start();

                /*
                 * Splash background ကို fade-out လုပ်ပြီး
                 * animation ပြီးသွားလျှင် splash view ဖယ်မည်။
                 */
                splashView.animate()
                        .alpha(0f)
                        .setDuration(SPLASH_EXIT_MS)
                        .setInterpolator(interpolator)
                        .withEndAction(
                                () -> splashScreenView.remove()
                        )
                        .start();
            }
    );

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

/*
 * Search bar ကို online, local နှင့် Favorites
 * category အားလုံးမှာ အမြဲပြမည်။
 */
searchInput.setVisibility(View.VISIBLE);

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
