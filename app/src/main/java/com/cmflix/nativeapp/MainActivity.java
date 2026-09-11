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
    private Button accountButton;
    private Button premiumButton;

private boolean vipRefreshInFlight = false;

private static final long VIP_REFRESH_INTERVAL =
        5L * 60L * 1000L;


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
            {"Series", "series"},
            {"18+", "lugyi"},
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
        categoryBar = findViewById(R.id.categoryBar);
        accountButton = findViewById(R.id.accountButton);
        premiumButton = findViewById(R.id.premiumButton);

        setupRecycler();
        setupCategories();
        setupSearch();
        setupAccountButtons();

        errorText.setOnClickListener(view -> {
            if (!isLoading) {
                loadNextPage();
            }
        });

        updateAccountButtons();
        resetAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountButtons();
    }

    private void setupRecycler() {
        int spanCount =
                getResources()
                        .getConfiguration()
                        .screenWidthDp >= 600
                        ? 5
                        : 3;

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

            intent.putExtra(
                    "slug",
                    item.optString("slug")
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
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );

params.setMarginStart(dp(3));
params.setMarginEnd(dp(3));

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

                searchInput.setVisibility(
                        "favorites".equals(category)
                                ? View.GONE
                                : View.VISIBLE
                );

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

        ApiClient.get(
                path,
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

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
