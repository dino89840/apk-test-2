package com.cmflix.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
    private TitleAdapter adapter;
    private String category = "movies";
    private String search = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.errorText);
        searchInput = findViewById(R.id.searchInput);

        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new TitleAdapter(item -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("slug", item.optString("slug"));
            startActivity(intent);
        });
        recycler.setAdapter(adapter);

        setupCategories();
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                search = searchInput.getText().toString().trim();
                loadTitles();
                return true;
            }
            return false;
        });

        loadTitles();
    }

    private void setupCategories() {
        LinearLayout bar = findViewById(R.id.categoryBar);
        String[][] categories = {
                {"Movies", "movies"},
                {"Series", "series"},
                {"Lugyi", "lugyi"}
        };

        for (String[] c : categories) {
            Button button = new Button(this);
            button.setText(c[0]);
            button.setOnClickListener(v -> {
                category = c[1];
                search = "";
                searchInput.setText("");
                loadTitles();
            });
            bar.addView(button);
        }
    }

    private void loadTitles() {
        progress.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);

        String path = "titles?category=" + ApiClient.encode(category)
                + "&page=1";
        if (!search.isEmpty()) {
            path += "&q=" + ApiClient.encode(search);
        }

        ApiClient.get(path, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                runOnUiThread(() -> {
                    List<JSONObject> list = new ArrayList<>();
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item != null) list.add(item);
                        }
                    }
                    adapter.setItems(list);
                    progress.setVisibility(View.GONE);
                    if (list.isEmpty()) {
                        errorText.setText("No movies found.");
                        errorText.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    errorText.setText("Load failed: " + error.getMessage());
                    errorText.setVisibility(View.VISIBLE);
                });
            }
        });
    }
}
