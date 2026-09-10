package com.cmflix.nativeapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
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

    private TitleAdapter adapter;
    private GridLayoutManager layoutManager;

    private final List<JSONObject> allItems = new ArrayList<>();

    private String category = "movies";
    private String search = "";

    private int currentPage = 0;
    private boolean hasMore = true;
    private boolean isLoading = false;

    /*
     * Category/search ပြောင်းသွားပြီးနောက် request အဟောင်းက
     * response နောက်ကျရောက်လာရင် list ကို မဖုံးနိုင်အောင်သုံးသည်။
     */
    private int requestGeneration = 0;

    private final String[][] categories = {
            {"Movies", "movies"},
            {"Series", "series"},
            {"18+", "lugyi"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.errorText);
        searchInput = findViewById(R.id.searchInput);
        categoryBar = findViewById(R.id.categoryBar);

        setupRecycler();
        setupCategories();
        setupSearch();

        errorText.setOnClickListener(view -> {
            if (!isLoading) {
                loadNextPage();
            }
        });

        resetAndLoad();
    }

    private void setupRecycler() {
        int spanCount = getResources()
                .getConfiguration()
                .screenWidthDp >= 600 ? 5 : 3;

        layoutManager = new GridLayoutManager(this, spanCount);
        recycler.setLayoutManager(layoutManager);

        adapter = new TitleAdapter(item -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    DetailActivity.class
            );

            intent.putExtra("slug", item.optString("slug"));
            startActivity(intent);
        });

        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(
                            RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {
                        super.onScrolled(recyclerView, dx, dy);

                        if (dy <= 0 || isLoading || !hasMore) {
                            return;
                        }

                        int visibleCount =
                                layoutManager.getChildCount();

                        int totalCount =
                                layoutManager.getItemCount();

                        int firstVisible =
                                layoutManager
                                        .findFirstVisibleItemPosition();

                        /*
                         * List အောက်ဆုံးမရောက်ခင် item 6 ခုအလိုကတည်းက
                         * နောက် page ကို ကြိုတင် load လုပ်မယ်။
                         */
                        if (
                                firstVisible
                                        + visibleCount
                                        >= totalCount - 6
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
            button.setTextSize(14);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setPadding(
                    dp(18),
                    dp(9),
                    dp(18),
                    dp(9)
            );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            params.setMarginEnd(dp(8));
            button.setLayoutParams(params);

            button.setTag(value);

            button.setOnClickListener(view -> {
                if (value.equals(category)) {
                    return;
                }

                category = value;
                search = "";
                searchInput.setText("");

                updateCategoryButtons();
                recycler.scrollToPosition(0);
                resetAndLoad();
            });

            categoryBar.addView(button);
        }

        updateCategoryButtons();
    }

    private void updateCategoryButtons() {
        for (int i = 0; i < categoryBar.getChildCount(); i++) {
            View child = categoryBar.getChildAt(i);

            if (!(child instanceof Button)) {
                continue;
            }

            Button button = (Button) child;
            boolean selected =
                    category.equals(String.valueOf(button.getTag()));

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
                    boolean isSearch =
                            actionId
                                    == EditorInfo.IME_ACTION_SEARCH;

                    boolean isEnter =
                            event != null
                                    && event.getAction()
                                    == KeyEvent.ACTION_DOWN
                                    && event.getKeyCode()
                                    == KeyEvent.KEYCODE_ENTER;

                    if (!isSearch && !isEnter) {
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

    private void resetAndLoad() {
        requestGeneration++;

        currentPage = 0;
        hasMore = true;
        isLoading = false;

        allItems.clear();
        adapter.setItems(allItems);

        errorText.setVisibility(View.GONE);

        loadNextPage();
    }

    private void loadNextPage() {
        if (isLoading || !hasMore) {
            return;
        }

        isLoading = true;

        final int generation = requestGeneration;
        final int requestedPage = currentPage + 1;

        progress.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);

        String path =
                "titles?category="
                        + ApiClient.encode(category)
                        + "&page="
                        + requestedPage;

        if (!search.isEmpty()) {
            path += "&q=" + ApiClient.encode(search);
        }

        ApiClient.get(
                path,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            /*
                             * Request စတင်ပြီးနောက် category/search
                             * ပြောင်းသွားခဲ့ရင် response အဟောင်းကို ပယ်မယ်။
                             */
                            if (generation != requestGeneration) {
                                return;
                            }

                            isLoading = false;
                            progress.setVisibility(View.GONE);

                            JSONArray items =
                                    json.optJSONArray("items");

                            List<JSONObject> newItems =
                                    new ArrayList<>();

                            if (items != null) {
                                for (
                                        int i = 0;
                                        i < items.length();
                                        i++
                                ) {
                                    JSONObject item =
                                            items.optJSONObject(i);

                                    if (item != null) {
                                        newItems.add(item);
                                    }
                                }
                            }

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

                            allItems.addAll(newItems);
                            adapter.setItems(allItems);

                            if (allItems.isEmpty()) {
                                errorText.setText(
                                        search.isEmpty()
                                                ? "ဇာတ်ကား မရှိသေးပါ။"
                                                : "ရှာဖွေထားသော ဇာတ်ကား မတွေ့ပါ။"
                                );

                                errorText.setVisibility(View.VISIBLE);
                            } else {
                                errorText.setVisibility(View.GONE);
                            }
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            if (generation != requestGeneration) {
                                return;
                            }

                            isLoading = false;
                            progress.setVisibility(View.GONE);

                            errorText.setText(
                                    "ဇာတ်ကားများ ရယူ၍မရပါ။\n"
                                            + "ပြန်စမ်းရန် ဒီနေရာကိုနှိပ်ပါ။\n\n"
                                            + safeMessage(error)
                            );

                            errorText.setVisibility(View.VISIBLE);
                        });
                    }
                }
        );
    }

    private String safeMessage(Exception error) {
        if (
                error == null
                        || error.getMessage() == null
                        || error.getMessage().trim().isEmpty()
        ) {
            return "Unknown error";
        }

        return error.getMessage();
    }

    private void hideKeyboard() {
        View current = getCurrentFocus();

        if (current == null) {
            return;
        }

        InputMethodManager manager =
                (InputMethodManager) getSystemService(
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
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
