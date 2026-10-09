package com.cmflix.nativeapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;

import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    


    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView errorText;
    private ImageButton gridToggleButton;
    private ShimmerFrameLayout shimmerContainer;
    private EditText searchInput;

    /*
     * Home redesign (2026-09):
     * homeMode = Home tab (sections: Continue Watching, Horror
     * latest-10, 18+ latest-10 when unlocked, Recently Viewed,
     * Downloads) — no grid below.
     * !homeMode = full-grid view for one category (content or
     * local) with back header.
     */
    private boolean homeMode = true;

    private LinearLayout localHeader;
    private TextView localHeaderTitle;

    private DrawerLayout drawerLayout;
    private ImageButton drawerButton;

    private HomeRowsAdapter headerAdapter;
    private ConcatAdapter concatAdapter;

    private View navHome;
    private View navHorror;
    private View navNosub;
    private View navMmsub;
    private View navMyanmar;
    private android.widget.ImageView navHomeIcon;
    private android.widget.ImageView navHorrorIcon;
    private android.widget.ImageView navNosubIcon;
    private android.widget.ImageView navMmsubIcon;
    private android.widget.ImageView navMyanmarIcon;
    private TextView navHomeLabel;
    private TextView navHorrorLabel;
    private TextView navNosubLabel;
    private TextView navMmsubLabel;
    private TextView navMyanmarLabel;

    /*
     * Home sections အတွက် session cache:
     * content category ("movies" / "series" / "lugyi") ရဲ့
     * page-1 item list။
     */
    private final Map<String, List<JSONObject>>
            homeSectionCache = new HashMap<>();
    private final Set<String> homeSectionLoading =
            new HashSet<>();

    /*
     * Home loading gate: section ၃ ခု အကုန် settle
     * ဖြစ်မှ (သို့မဟုတ် timeout) home ကိုပြမည်။
     * Request အသစ်မရှိ — UI reveal ကိုသာ gate လုပ်သည်။
     */
    private static final long HOME_GATE_TIMEOUT_MS =
            10000L;

    private View homeLoadingOverlay;
    private boolean homeGateOpen = false;

    private final Handler homeGateHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable homeGateTimeoutRunnable =
            this::openHomeGate;
private LinearLayout searchHistoryContainer;
/*
 * ဇာတ်ကားအသစ်တင်ပြီး ၂၀ မိနစ်အတွင်း
 * cached category မှာ မပေါ်သေးနိုင်သည်။
 *
 * User က delay အနည်းငယ်ရနိုင်သည်ဟု
 * သတ်မှတ်ထားသည့်အတွက် ဒီ TTL ကသင့်တော်သည်။
 */
private static final long CATALOG_CACHE_MS =
        20L * 60L * 1000L;

private View searchHistoryRow;

private TextView localClearButton;
private TextView clearSearchHistoryButton;

private Button accountButton;
private Button premiumButton;

/*
 * VIP price banner — home feed ရဲ့ ပထမ item
 * (VipBannerAdapter) အဖြစ် ပြသည်။
 * အရင် fixed header ထဲက ImageView ကို feed ထဲ
 * ပြောင်းထားခြင်းဖြစ်ပြီး scroll နဲ့အတူ အပေါ်ကို
 * ပါသွားစေရန်။
 */
private VipBannerAdapter vipBannerAdapter;

/*
 * Top chrome (banner / account / premium button) များ၏
 * Home ပေါ်မှာပြသင့်သော visibility။
 * Full-grid view ဝင်လျှင် နေရာကျဉ်းသဖြင့်
 * hamburger + search bar သာ ချန်ပြီး အားလုံးဝှက်မည်။
 * Home ပြန်ရောက်လျှင် ဒီ flag များအတိုင်း ပြန်ပြမည်။
 */
private boolean vipBannerWanted = true;
private boolean premiumButtonWanted = false;

/*
 * Admin မှသတ်မှတ်ထားသော banner click link။
 */
private String vipBannerLink =
        "https://t.me/" + CryptoUtil.dec("eYfWVCItMPTfAWhBVxWccg==");

/*
 * Remote banner image state — feed ထဲက banner item
 * bind လုပ်တိုင်း ဒီ state အတိုင်း render မည်
 * (scroll ပြန်တက်လာလျှင်လည်း မှန်ရမည်)။
 */
private String vipBannerImageUrl = "";
private String vipBannerVersion = "1";

/*
 * Same notification ID ကို app process တစ်ခုအတွင်း
 * ထပ်မပြစေရန်။
 *
 * Admin က notification ID ပြောင်းလိုက်လျှင် app မပိတ်ဘဲ
 * foreground polling ကနေ notification အသစ်ကိုပြနိုင်သည်။
 */
private static String lastShownNoticeIdThisLaunch =
        "";

private long lastBackPressedAt = 0L;

private static final long BACK_EXIT_INTERVAL_MS =
        2000L;


/*
 * User-specific profile/VIP data ကို 3/6 နာရီ cache
 * မလုပ်တော့ပါ။
 *
 * Main screen foreground ပြန်ဝင်တိုင်း server ကို refresh
 * လုပ်မည်။ Lifecycle callback ဆက်တိုက်ဝင်လာလျှင် request
 * duplicate မဖြစ်အောင် 15 seconds throttle သာထားမည်။
 */
private static final long
        PROFILE_REFRESH_MIN_INTERVAL_MS =
        15L * 60L * 1000L;

private boolean profileRefreshInFlight = false;
private long lastProfileRefreshAttemptAt = 0L;


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

    /*
     * Category value -> display label.
     * Chips ဖြုတ်ပြီးနောက် label များကို bottom nav /
     * drawer / section header များတွင် သုံးသည်။
     */
    private final String[][] categories = {
        {"Horror", "movies"},
        {"Nosub 18+", "series"},
        {"Mmsub 18+", "lugyi"},
        {"Myanmar", "myanmar"},
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
        result.getResultCode() ==
                RESULT_OK
) {
    /*
     * Account အသစ်နဲ့ login ဝင်ပြီးတာနဲ့
     * Continue/Recent/Downloads/Favorites ကို
     * account အသစ် data ဖြင့်ပြန်ဆွဲမည်။
     */
    refreshHomeSections();

    if (
            isLocalCategory(category) ||
            "favorites".equals(category)
    ) {
        resetAndLoad();
    }

    /*
     * Login response မှာ VIP state ပါပြီးသားဖြစ်သော်လည်း
     * foreground state ကို server နဲ့ sync ထပ်လုပ်နိုင်ရန်
     * throttle time ကို reset လုပ်မည်။
     */
    lastProfileRefreshAttemptAt = 0L;
    refreshProfileIfNeeded();
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

        drawerLayout =
                findViewById(R.id.drawerLayout);

        drawerButton =
                findViewById(R.id.drawerButton);

        drawerButton.setOnClickListener(
                view ->
                        drawerLayout.openDrawer(
                                GravityCompat.START
                        )
        );

        localHeader =
                findViewById(R.id.localHeader);

        localHeaderTitle =
                findViewById(R.id.localHeaderTitle);

        findViewById(R.id.localBackButton)
                .setOnClickListener(
                        view -> goHome()
                );

        navHome = findViewById(R.id.navHome);
        navHorror = findViewById(R.id.navHorror);
        navNosub = findViewById(R.id.navNosub);
        navMmsub = findViewById(R.id.navMmsub);
        navMyanmar = findViewById(R.id.navMyanmar);
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navHorrorIcon =
                findViewById(R.id.navHorrorIcon);
        navNosubIcon =
                findViewById(R.id.navNosubIcon);
        navMmsubIcon =
                findViewById(R.id.navMmsubIcon);
        navMyanmarIcon =
                findViewById(R.id.navMyanmarIcon);
        navHomeLabel = findViewById(R.id.navHomeLabel);
        navHorrorLabel =
                findViewById(R.id.navHorrorLabel);
        navNosubLabel =
                findViewById(R.id.navNosubLabel);
        navMmsubLabel =
                findViewById(R.id.navMmsubLabel);
        navMyanmarLabel =
                findViewById(R.id.navMyanmarLabel);

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


localClearButton =
        findViewById(R.id.localClearButton);

gridToggleButton =
        findViewById(R.id.gridToggleButton);

gridToggleButton.setOnClickListener(
        view -> toggleGridSpan()
);

shimmerContainer =
        findViewById(R.id.shimmerContainer);

        homeLoadingOverlay =
                findViewById(R.id.homeLoadingOverlay);

accountButton =
        findViewById(R.id.accountButton);

premiumButton =
        findViewById(R.id.premiumButton);



        setupRecycler();
        setupDrawer();
        setupSearch();
setupAccountButtons();
setupDoubleBackExit();
setupBottomNav();
setupHomeBackHandler();
setupLocalFeatureControls();

/*
 * Launch state = Home (sections only):
 * grid tools (CLEAR / column toggle) ကို ဖျောက်ထားမည်။
 */
gridToggleButton.setVisibility(View.GONE);

/*
 * Banner ကို local 12-hour cache မှအရင်ပြမည်။
 * Refresh လိုအပ်မှ CDN-backed /app-content ကိုခေါ်မည်။
 */
loadRemoteBanner();

refreshSearchHistory();



        errorText.setOnClickListener(view -> {
            if (!isLoading) {
                resetAndLoad();
            }
        });

        updateAccountButtons();
refreshProfileIfNeeded();
resetAndLoad();
startHomeGate();

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

    /*
     * Home mode မှာ full-grid ရဲ့ empty/error message
     * လုံးဝမပြရပါ။
     *
     * DetailActivity ကနေ Back ပြန်လာသည့်အခါ
     * category က "continue" / "recent" / "downloads"
     * အဖြစ် ကျန်နေသော်လည်း loadLocalCategory()
     * ပြန်မခေါ်စေရန် homeMode ကိုပါစစ်သည်။
     */
    if (homeMode) {
        clearListStatusUi();

        if (adapter != null) {
            adapter.refreshProgressSnapshot();
        }
    } else if (isLocalCategory(category)) {
        loadLocalCategory();
    } else if (adapter != null) {
        /*
         * Full-grid network category ဖြစ်လျှင်
         * poster အားလုံးကို rebind မလုပ်ဘဲ
         * progress bar များကိုသာ update လုပ်မည်။
         */
        adapter.refreshProgressSnapshot();
    }

    /*
     * Home mode ဖြစ်မှ section adapter ကို
     * data အသစ်ဖြင့် refresh လုပ်မည်။
     * Home မဟုတ်လျှင် method အတွင်းမှာ section
     * list ကို empty ပြောင်းထားပြီးဖြစ်သည်။
     */
    refreshHomeSections();

    /*
     * Foreground ဝင်တာနဲ့ notification ကို
     * ချက်ချင်း ETag validation လုပ်မည်။
     */
    refreshRemoteNotification();
}

@Override
protected void onPause() {
    

    super.onPause();
}
@Override
protected void onDestroy() {
    

    homeGateHandler.removeCallbacks(
            homeGateTimeoutRunnable
    );

    if (shimmerContainer != null) {
        shimmerContainer.stopShimmer();
    }

    super.onDestroy();
}



    /*
     * Grid item နှိပ်လျှင် DetailActivity ဖွင့်မည်။
     * Home rows များမှလည်း ဒီ method အတူတူကိုသုံးသည်။
     */
    private void openDetail(JSONObject item) {
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
    }

    private void setupRecycler() {
    int spanCount = computeSpanCount();

    layoutManager =
        new GridLayoutManager(
                this,
                spanCount
        );

    applySpanCount(spanCount);

    recycler.setLayoutManager(
            layoutManager
    );

    recycler.setHasFixedSize(true);
    recycler.setItemAnimator(null);

    adapter = new TitleAdapter(
            this::openDetail,
            this::removeFavoriteFromList
    );

    /*
     * Home: dynamic section list (headerAdapter) + content
     * grid (adapter) တစ်ခုတည်း scroll ဖြစ်အောင်
     * ConcatAdapter သုံးသည်။ Header က grid column
     * အပြည့်ယူမည်။ Home mode မှာ grid ကို item
     * မထည့်ဘဲ section များသာပြမည်။
     */
    headerAdapter =
            new HomeRowsAdapter(
                    new HomeRowsAdapter.Listener() {
                        @Override
                        public void onItemClick(
                                JSONObject item
                        ) {
                            openDetail(item);
                        }

                        @Override
                        public void onSectionMore(
                                HomeRowsAdapter.HomeSection
                                        section
                        ) {
                            openSectionMore(section.id);
                        }
                    }
            );

    /*
     * VIP price banner — home feed ရဲ့ ပထမ item။
     * Fixed header ထဲကမဟုတ်ဘဲ feed ထဲမှာမို့
     * scroll နဲ့အတူ အပေါ်ကို ပါသွားမည်။
     */
    vipBannerAdapter =
            new VipBannerAdapter(
                    this::onBindVipBanner
            );

    concatAdapter =
            new ConcatAdapter(
                    vipBannerAdapter,
                    headerAdapter,
                    adapter
            );

    recycler.setAdapter(concatAdapter);

    layoutManager.setSpanSizeLookup(
            new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    int bannerCount =
                            vipBannerAdapter != null
                                    ? vipBannerAdapter
                                            .getItemCount()
                                    : 0;

                    int headerCount =
                            headerAdapter != null
                                    ? headerAdapter
                                            .getItemCount()
                                    : 0;

                    if (
                            position <
                                    bannerCount + headerCount
                    ) {
                        return layoutManager
                                .getSpanCount();
                    }

                    return 1;
                }
            }
    );

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
                            homeMode ||
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


    /*
     * User ရွေးထားသော grid column (ရှိလျှင်) ကို
     * ဦးစားပေးမည်။ မရွေးထားလျှင် screen size
     * အလိုက် auto ရွေးမည်။
     */
    private int computeSpanCount() {
        int saved = LocalStore.getGridSpan();

        if (saved == 2 || saved == 3) {
            return saved;
        }

        int screenWidthDp =
                getResources()
                        .getConfiguration()
                        .screenWidthDp;

        /*
         * Phone မှာ title/meta ပါသော card ဖြစ်သောကြောင့်
         * 2 columns က ဖတ်ရလွယ်ပြီး poster size ကောင်းသည်။
         */
        if (screenWidthDp >= 840) {
            return 5;
        } else if (screenWidthDp >= 600) {
            return 3;
        } else {
            return 2;
        }
    }

    private void applySpanCount(int spanCount) {
        layoutManager.setSpanCount(spanCount);

        layoutManager.setInitialPrefetchItemCount(
                spanCount * 2
        );

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
    }

    /*
     * Grid 2 columns <-> 3 columns ပြောင်းမည်။
     * ရွေးချယ်မှုကို device မှာ မှတ်ထားမည်။
     */
    private void toggleGridSpan() {
        if (layoutManager == null) {
            return;
        }

        int next =
                layoutManager.getSpanCount() == 2
                        ? 3
                        : 2;

        LocalStore.saveGridSpan(next);
        applySpanCount(next);
    }
/*
 * Screen တစ်ခုကနေ တစ်ခုကို ပြောင်းသောအခါ
 * အရင် list screen ရဲ့ empty/error/loading state
 * Home ပေါ်တွင် overlay မဖြစ်စေရန် ရှင်းမည်။
 */
private void clearListStatusUi() {
    if (errorText != null) {
        errorText.clearAnimation();
        errorText.setText("");
        errorText.setVisibility(View.GONE);
    }

    if (progress != null) {
        progress.clearAnimation();
        progress.setVisibility(View.GONE);
    }

    if (shimmerContainer != null) {
        shimmerContainer.stopShimmer();
        shimmerContainer.clearAnimation();
        shimmerContainer.setVisibility(View.GONE);
    }

    if (recycler != null) {
        recycler.setVisibility(View.VISIBLE);
    }
}

    /*
     * Initial load မှာ shimmer placeholder ပြမည်။
     * Pagination (page 2+) မှာတော့ အောက်က
     * spinner ကိုသာ ဆက်သုံးမည်။
     */
    private void showShimmer() {
        if (shimmerContainer == null) {
            return;
        }

        recycler.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        shimmerContainer.setVisibility(View.VISIBLE);
        shimmerContainer.startShimmer();
    }

    private void hideShimmer() {
        if (shimmerContainer == null) {
            return;
        }

        shimmerContainer.stopShimmer();
        shimmerContainer.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
    }


    /*
     * Hamburger navigation drawer: menu item များကို
     * ရှိပြီးသား navigation action များနှင့် ချိတ်သည်။
     * Drawer ဖွင့်ရုံဖြင့် network request မရှိပါ။
     */
    /*
 * Hamburger drawer item များကို destination action များနှင့်
 * ချိတ်ထားသည်။
 *
 * Drawer ကို animation မသုံးဘဲ အရင်ပိတ်ပြီး action ကို
 * UI queue မှာ run သောကြောင့် category change သို့မဟုတ်
 * Activity launch ကို drawer animation က မတားနိုင်ပါ။
 */
private void setupDrawer() {
    findViewById(R.id.drawerHome)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    this::goHome
                            )
            );

    findViewById(R.id.drawerHorror)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    () ->
                                            openCategory(
                                                    "movies",
                                                    "Horror"
                                            )
                            )
            );

    findViewById(R.id.drawerNosub)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    () ->
                                            openCategory(
                                                    "series",
                                                    "Nosub 18+"
                                            )
                            )
            );

    findViewById(R.id.drawerMmsub)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    () ->
                                            openCategory(
                                                    "lugyi",
                                                    "Mmsub 18+"
                                            )
                            )
            );

    findViewById(R.id.drawerContinue)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    () ->
                                            switchCategory(
                                                    "continue",
                                                    "Continue Watching"
                                            )
                            )
            );

    findViewById(R.id.drawerRecent)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    () ->
                                            switchCategory(
                                                    "recent",
                                                    "Recently Viewed"
                                            )
                            )
            );

    findViewById(R.id.drawerDownloads)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    () ->
                                            switchCategory(
                                                    "downloads",
                                                    "Downloads"
                                            )
                            )
            );

    findViewById(R.id.drawerFavorites)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    this::openFavorites
                            )
            );

    findViewById(R.id.drawerProfile)
            .setOnClickListener(
                    view ->
                            runDrawerAction(
                                    this::openProfile
                            )
            );
}


/*
 * Drawer item နှိပ်သောအခါ drawer ကိုချက်ချင်းပိတ်ပြီးမှ
 * destination action ကို main UI queue တွင် run မည်။
 */
private void runDrawerAction(
        Runnable action
) {
    if (action == null) {
        return;
    }

    if (drawerLayout == null) {
        action.run();
        return;
    }

    if (
            drawerLayout.isDrawerOpen(
                    GravityCompat.START
            )
    ) {
        drawerLayout.closeDrawer(
                GravityCompat.START,
                false
        );
    }

    drawerLayout.post(action);
}


/*
 * Profile ကို login ဝင်ထားမှဖွင့်မည်။
 * Login မဝင်ထားသေးလျှင် Auth screen ကိုဖွင့်မည်။
 */
private void openProfile() {
    if (!SessionManager.isLoggedIn()) {
        openLogin();
        return;
    }

    Intent intent =
            new Intent(
                    MainActivity.this,
                    ProfileActivity.class
            );

    startActivity(intent);
}


/*
 * Back handler စသည်တို့က အသုံးပြုနိုင်ရန်
 * closeDrawer method ကို ဆက်ထားသည်။
 */
private void closeDrawer() {
    if (
            drawerLayout != null &&
                    drawerLayout.isDrawerOpen(
                            GravityCompat.START
                    )
    ) {
        drawerLayout.closeDrawer(
                GravityCompat.START
        );
    }
}


    private void openFavorites() {
        if (!SessionManager.isLoggedIn()) {
            openLogin();
            return;
        }

        switchCategory(
                "favorites",
                "Favorites"
        );
    }


/*
 * Category ဖွင့်ခြင်း၏ တစ်ခုတည်းသော တံခါးပေါက်။
 * Bottom nav, drawer, Home "More >" အားလုံး ဒီကနေဖြတ်သည်။
 */
private void openCategory(
        String value,
        String label
) {
    switchCategory(value, label);
}


/*
 * Full-grid view သို့ ဝင်ခြင်း။
 * homeMode=false, back header ပေါ်မည်။
 */
private void switchCategory(
        String value,
        String label
) {
    if (!homeMode && value.equals(category)) {
        return;
    }

    enterCategory(value, label);

    search = "";
    searchInput.setText("");

    recycler.scrollToPosition(0);
    resetAndLoad();
}


/*
 * Full-grid state ကို ချိန်ညှိခြင်း
 * (search text ကို မထိပါ — search submit က
 * Home မှ Horror grid သို့ ကူးရာတွင် သုံးသည်)။
 */
private void enterCategory(
        String value,
        String label
) {
    /*
     * Home သို့မဟုတ် အရင် category ရဲ့ status view
     * အသစ်ဖွင့်မည့် category ပေါ်တွင် မကျန်စေရန်။
     */
    clearListStatusUi();

    category = value;
    homeMode = false;

    /*
     * Network category full-grid (Horror / 18+ —
     * Home "More ›" ကလာသော) တွင် hamburger +
     * search bar သာ ပြမည်။ Banner / username /
     * premium ကို refreshTopChromeVisibility() က ဝှက်ပြီး၊
     * back/title header နှင့် grid toggle ကိုပါ
     * နေရာကျဉ်းသဖြင့် ဝှက်မည်။
     * Drawer ကဖွင့်သော local စာမျက်နှာများ
     * (Continue Watching / Recently Viewed /
     * Downloads / Favorites) တွင်မူ back header နှင့်
     * CLEAR ကို ဆက်ပြမည်။
     */
    boolean minimalChrome =
            isNetworkCategory(category);

    localHeader.setVisibility(
            minimalChrome ? View.GONE : View.VISIBLE
    );
    localHeaderTitle.setText(label);

    boolean local = isLocalCategory(category);

    searchInput.setVisibility(View.VISIBLE);

    localClearButton.setVisibility(
            local ? View.VISIBLE : View.GONE
    );

    gridToggleButton.setVisibility(
            minimalChrome ? View.GONE : View.VISIBLE
    );

    refreshTopChromeVisibility();
    refreshSearchHistory();
    updateBottomNav();
}


/*
 * Home "More ›" / bottom nav / search တို့မှ
 * ဖွင့်သော network category များ။
 */
private boolean isNetworkCategory(
        String value
) {
    return "movies".equals(value) ||
            "series".equals(value) ||
            "lugyi".equals(value);
}


private void goHome() {
    /*
     * Home ကိုရောက်ပြီးသားအချိန် Home ကိုထပ်နှိပ်ရင်လည်း
     * အရင် screen ကကျန်နေသော empty/error overlay ကို
     * အရင်ရှင်းမည်။
     */
    if (homeMode) {
        clearListStatusUi();
        refreshHomeSections();
        recycler.smoothScrollToPosition(0);
        return;
    }

    /*
     * လက်ရှိ full-grid request/callback အဟောင်းများ
     * နောက်ကျပြီး UI ကို ပြန်ပြင်ခြင်းမဖြစ်စေရန်
     * generation ကို invalidate လုပ်သည်။
     *
     * resetAndLoad() အတွင်း generation ထပ်တိုးထားလျှင်လည်း
     * ပြဿနာမရှိပါ။
     */
    requestGeneration++;
    isLoading = false;

    homeMode = true;

    /*
     * Home တွင် category ကို local category အဖြစ်
     * မကျန်စေရန် safe default ပြန်ထားသည်။
     *
     * ဒါကြောင့် Activity onResume ပြန်ဝင်ချိန်တွင်
     * Continue/Recent/Downloads loader ကို
     * မတော်တဆ မခေါ်တော့ပါ။
     */
    category = "movies";

    search = "";
    searchInput.setText("");

    /*
     * အရင် full-grid ရဲ့ empty/error/loading state ကို
     * Home section မပြခင်ရှင်းမည်။
     */
    clearListStatusUi();

    localHeader.setVisibility(View.GONE);
    localClearButton.setVisibility(View.GONE);
    gridToggleButton.setVisibility(View.GONE);

    refreshTopChromeVisibility();
    refreshSearchHistory();
    updateBottomNav();

    recycler.scrollToPosition(0);
    resetAndLoad();
}



/*
 * Top chrome visibility ကို လက်ရှိ view state
 * (Home / full-grid) နှင့်အညီ ချိန်ညှိသည်။
 *
 * Full-grid view တွင် banner / account (username) /
 * premium button များကို ဝှက်ပြီး hamburger +
 * search bar သာ ချန်မည် (နေရာကျဉ်းသဖြင့်)။
 * Home တွင် remote config / login state အတိုင်း ပြန်ပြမည်။
 * Request / cache / banner fetch logic ကို မထိပါ။
 */
private void refreshTopChromeVisibility() {
    boolean fullGrid = !homeMode;

    if (vipBannerAdapter != null) {
        /*
         * Banner ကို feed ရဲ့ ပထမ item အဖြစ်
         * ထည့်/ဖြုတ်မည် — scroll နဲ့အတူ ပါသွားစေရန်။
         */
        vipBannerAdapter.setVisible(
                !fullGrid && vipBannerWanted
        );
    }

    if (accountButton != null) {
        accountButton.setVisibility(
                fullGrid ? View.GONE : View.VISIBLE
        );
    }

    if (premiumButton != null) {
        premiumButton.setVisibility(
                !fullGrid && premiumButtonWanted
                        ? View.VISIBLE
                        : View.GONE
        );
    }
}


/*
 * Home "More >" နှိပ်လျှင် section နှင့် သက်ဆိုင်သော
 * full-grid view ကို ဖွင့်မည်။
 */
private void openSectionMore(String sectionId) {
    switch (sectionId) {
        case "continue":
            switchCategory(
                    "continue",
                    "Continue Watching"
            );

            break;

        case "recent":
            switchCategory(
                    "recent",
                    "Recently Viewed"
            );

            break;

        case "downloads":
            switchCategory(
                    "downloads",
                    "Downloads"
            );

            break;

        case "movies":
            openCategory("movies", "Horror");
            break;

        case "series":
            openCategory("series", "Nosub 18+");
            break;

        case "lugyi":
            openCategory("lugyi", "Mmsub 18+");
            break;

        default:
            break;
    }
}


/*
 * Home sections ကို တည်ဆောက်သည်။
 * အစဉ်:
 * 1. Continue Watching (ရှိမှသာ၊ အပေါ်ဆုံး)
 * 2. Horror latest-10
 * 3. Nosub 18+ latest-10
 * 4. Mmsub 18+ latest-10
 *
 * Recently Viewed / Downloads / Favorites များသည်
 * Home တွင်မပြဘဲ hamburger drawer မှသာ ဝင်သည်။
 *
 * - Continue Watching: LocalStore (ဖုန်းတွင်း) ကသာ — request 0
 * - Horror / 18+ latest-10: session cache; cache
 *   မရှိသေးလျှင် page-1 ကို တစ်ကြိမ်တည်း fetch
 *   (20-min disk cache ရှိ)။
 */
private void refreshHomeSections() {
    if (headerAdapter == null) {
        return;
    }

    if (!homeMode) {
        headerAdapter.setSections(
                Collections.emptyList()
        );

        return;
    }

    boolean searching =
            search != null &&
                    !search.trim().isEmpty();

    if (searching) {
        headerAdapter.setSections(
                Collections.emptyList()
        );

        return;
    }

    List<HomeRowsAdapter.HomeSection> sections =
            new ArrayList<>();

    List<JSONObject> continueItems =
            LocalStore.getContinueWatching();

    if (!continueItems.isEmpty()) {
        sections.add(
                new HomeRowsAdapter.HomeSection(
                        "continue",
                        "Continue Watching",
                        continueItems
                )
        );
    }

    List<JSONObject> horrorItems =
            firstN(homeSectionCache.get("movies"), 10);

    if (!horrorItems.isEmpty()) {
        sections.add(
                new HomeRowsAdapter.HomeSection(
                        "movies",
                        "Horror",
                        horrorItems
                )
        );
    }

    List<JSONObject> nosubItems =
            firstN(
                    homeSectionCache.get("series"),
                    10
            );

    if (!nosubItems.isEmpty()) {
        sections.add(
                new HomeRowsAdapter.HomeSection(
                        "series",
                        "Nosub 18+",
                        nosubItems
                )
        );
    }

    List<JSONObject> mmsubItems =
            firstN(
                    homeSectionCache.get("lugyi"),
                    10
            );

    if (!mmsubItems.isEmpty()) {
        sections.add(
                new HomeRowsAdapter.HomeSection(
                        "lugyi",
                        "Mmsub 18+",
                        mmsubItems
                )
        );
    }

    headerAdapter.setSections(sections);

    /*
     * လိုအပ်သော network section များ cache မရှိသေးလျှင်
     * ယခု fetch လုပ်မည် (session မှာ တစ်ကြိမ်တည်း)။
     */
    ensureHomeSectionLoaded("movies");
    ensureHomeSectionLoaded("series");
    ensureHomeSectionLoaded("lugyi");
}


private static List<JSONObject> firstN(
        List<JSONObject> items,
        int n
) {
    if (items == null || items.isEmpty()) {
        return Collections.emptyList();
    }

    if (items.size() <= n) {
        return items;
    }

    return new ArrayList<>(items.subList(0, n));
}


/*
 * Home loading gate: section ၃ ခု အကုန် settle ဖြစ်မှ
 * (သို့မဟုတ် timeout) home content ကိုပြမည်။
 */
/*
 * Home sections load စတင်သောအချိန် loading overlay ကိုပြမည်။
 */
private void startHomeGate() {
    if (homeLoadingOverlay == null) {
        homeGateOpen = true;
        return;
    }

    homeGateOpen = false;

    /*
     * အရင် animation ကျန်နေလျှင် ပယ်ဖျက်မည်။
     */
    homeLoadingOverlay.animate().cancel();

    homeLoadingOverlay.setAlpha(1f);
    homeLoadingOverlay.setClickable(true);
    homeLoadingOverlay.setFocusable(true);
    homeLoadingOverlay.setVisibility(
            View.VISIBLE
    );

    homeGateHandler.removeCallbacks(
            homeGateTimeoutRunnable
    );

    homeGateHandler.postDelayed(
            homeGateTimeoutRunnable,
            HOME_GATE_TIMEOUT_MS
    );

    /*
     * Cache ပူနေပြီး loading request မရှိလျှင်
     * overlay ကိုချက်ချင်းပိတ်နိုင်ရန်။
     */
    checkHomeGate();
}

private void checkHomeGate() {
    if (homeGateOpen) {
        return;
    }

    if (homeSectionLoading.isEmpty()) {
        openHomeGate();
    }
}

/*
 * Loading ပြီးသောအချိန် overlay ကို fade-out လုပ်ပြီး
 * GONE အဖြစ်သတ်မှတ်မည်။
 */
private void openHomeGate() {
    if (homeGateOpen) {
        return;
    }

    homeGateOpen = true;

    homeGateHandler.removeCallbacks(
            homeGateTimeoutRunnable
    );

    if (homeLoadingOverlay == null) {
        return;
    }

    /*
     * Fade animation လုပ်နေစဉ်ကတည်းက touch interception
     * ရပ်ထားမည်။
     */
    homeLoadingOverlay.setClickable(false);
    homeLoadingOverlay.setFocusable(false);

    homeLoadingOverlay.animate()
            .cancel();

    homeLoadingOverlay.animate()
            .alpha(0f)
            .setDuration(300L)
            .withEndAction(
                    () -> {
                        homeLoadingOverlay
                                .setVisibility(
                                        View.GONE
                                );

                        /*
                         * နောက်တစ်ကြိမ်ပြန်သုံးချိန်အတွက်
                         * alpha ကိုပြန်ထားမည်။
                         */
                        homeLoadingOverlay.setAlpha(1f);
                    }
            )
            .start();
}



/*
 * Home section အတွက် content category page-1 ကို
 * session မှာ တစ်ကြိမ်တည်း fetch လုပ်သည်။
 * ApiClient.getCached (20-min disk cache) သုံးသည် —
 * user က grid ဖွင့်လျှင်လည်း ဒီ cache ပဲ ပြန်သုံးသည်။
 */
private void ensureHomeSectionLoaded(String value) {
    if (
            homeSectionCache.containsKey(value) ||
                    homeSectionLoading.contains(value)
    ) {
        return;
    }

    homeSectionLoading.add(value);

    String path =
            "titles?category=" +
                    ApiClient.encode(value) +
                    "&page=1";

    ApiClient.getCached(
            path,
            CATALOG_CACHE_MS,
            new ApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject json) {
                    runOnUiThread(() -> {
                        homeSectionLoading
                                .remove(value);

                        JSONArray items =
                                json.optJSONArray(
                                        "items"
                                );

                        List<JSONObject> list =
                                new ArrayList<>();

                        if (items != null) {
                            for (
                                    int index = 0;
                                    index <
                                            items.length();
                                    index++
                            ) {
                                JSONObject item =
                                        items.optJSONObject(
                                                index
                                        );

                                if (item != null) {
                                    list.add(item);
                                }
                            }
                        }

                        homeSectionCache.put(
                                value,
                                list
                        );

                        /*
                         * Section ၃ ခု parallel fetch လုပ်သောကြောင့်
                         * တစ်ခုချင်းစီ ပြီးတိုင်း Home ကို
                         * ပြန်လည်ပြင်ဆင်မည် (homeMode ဖြစ်နေလျှင်)။
                         * Generation check မလိုပါ — homeMode
                         * စစ်ခြင်းက လုံလောက်သည်။
                         */
                        if (homeMode) {
                            refreshHomeSections();
                        }

                        checkHomeGate();
                    });
                }

                @Override
                public void onError(Exception error) {
                    runOnUiThread(
                            () -> {
                                homeSectionLoading
                                        .remove(value);

                                checkHomeGate();
                            }
                    );
                }
            }
    );
}



    private void setupBottomNav() {
        navHome.setOnClickListener(
                view -> goHome()
        );

        navHorror.setOnClickListener(
                view -> {
                    if (
                            !homeMode &&
                                    "movies".equals(category)
                    ) {
                        recycler
                                .smoothScrollToPosition(0);

                        return;
                    }

                    openCategory(
                            "movies",
                            "Horror"
                    );
                }
        );

        navNosub.setOnClickListener(
                view -> {
                    if (
                            !homeMode &&
                                    "series".equals(category)
                    ) {
                        recycler
                                .smoothScrollToPosition(0);

                        return;
                    }

                    openCategory(
                            "series",
                            "Nosub 18+"
                    );
                }
        );

        navMmsub.setOnClickListener(
                view -> {
                    if (
                            !homeMode &&
                                    "lugyi".equals(category)
                    ) {
                        recycler
                                .smoothScrollToPosition(0);

                        return;
                    }

                    openCategory(
                            "lugyi",
                            "Mmsub 18+"
                    );
                }
        );

        /*
         * မြန်မာ tab — samusar.com တိုက်ရိုက် scrape
         * ဖြစ်သောကြောင့် MainActivity ၏ backend
         * category flow (openCategory) ကို မသုံးဘဲ
         * MyanmarActivity သီးသန့် ဖွင့်မည်။
         * PIN မရှိပါ။ Kill-switch ပိတ်ထားလျှင်
         * tab ကိုယ်တိုင်က မပေါ်ပါ။
         */
        navMyanmar.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        MyanmarActivity.class
                                )
                        )
        );

        updateBottomNav();
    }

    private void updateBottomNav() {
        setBottomNavItem(
                navHomeIcon,
                navHomeLabel,
                homeMode
        );

        setBottomNavItem(
                navHorrorIcon,
                navHorrorLabel,
                !homeMode &&
                        "movies".equals(category)
        );

        setBottomNavItem(
                navNosubIcon,
                navNosubLabel,
                !homeMode &&
                        "series".equals(category)
        );

        setBottomNavItem(
                navMmsubIcon,
                navMmsubLabel,
                !homeMode &&
                        "lugyi".equals(category)
        );

        /*
         * မြန်မာ tab — MyanmarActivity သီးသန့်
         * ဖွင့်သောကြောင့် MainActivity တွင် "active"
         * ဘယ်တော့မှ မဖြစ်ပါ။ Inactive color (#8A8F9C)
         * အမြဲရမည် (label မပျောက်စေရန်)။
         */
        setBottomNavItem(
                navMyanmarIcon,
                navMyanmarLabel,
                false
        );
    }


    private void setBottomNavItem(
            android.widget.ImageView icon,
            TextView label,
            boolean active
    ) {
        int color =
                active
                        ? Color.parseColor("#E8B93E")
                        : Color.parseColor("#8A8F9C");

        icon.setColorFilter(color);
        label.setTextColor(color);

        if (active) {
            /*
             * Premium gold tile behind the active tab icon
             * (same tile as the drawer menu icons).
             */
            icon.setBackgroundResource(
                    R.drawable.drawer_icon_tile);
        } else {
            icon.setBackgroundResource(0);
        }
    }

    /*
     * Drawer ပွင့်နေလျှင် Back နှိပ်လျှင် drawer ပိတ်မည်။
     * Full-grid mode မှာ Back နှိပ်လျှင်
     * app မပိတ်ဘဲ Home ပြန်သွားမည်။
     * setupDoubleBackExit() ထက် နောက်မှ add ထားသောကြောင့်
     * ဒီ callback က အရင်အလုပ်လုပ်မည် (LIFO)။
     */
    private void setupHomeBackHandler() {
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (
                                        drawerLayout != null &&
                                                drawerLayout
                                                        .isDrawerOpen(
                                                                GravityCompat
                                                                        .START
                                                        )
                                ) {
                                    drawerLayout
                                            .closeDrawer(
                                                    GravityCompat
                                                            .START
                                            );

                                    return;
                                }

                                if (!homeMode) {
                                    goHome();
                                    return;
                                }

                                setEnabled(false);
                                getOnBackPressedDispatcher()
                                        .onBackPressed();
                                setEnabled(true);
                            }
                        }
                );
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

                    String query = searchInput
                            .getText()
                            .toString()
                            .trim();

                    /*
                     * Home မှာ grid မရှိတော့သောကြောင့်
                     * Home ကနေ search လုပ်လျှင် Horror
                     * full-grid ထဲမှာ ရှာပေးမည်။
                     */
                    if (
                            homeMode &&
                                    !query.isEmpty()
                    ) {
                        enterCategory(
                                "movies",
                                "Horror"
                        );
                    }

                    search = query;

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

/*
 * Feed ထဲက VIP banner item bind လုပ်တိုင်း
 * ခေါ်သည် — image (local fallback / remote) နှင့်
 * Telegram click listener ထားမည်။
 */
private void onBindVipBanner(
        ImageView bannerView
) {
    renderVipBannerImage(bannerView);

    bannerView.setOnClickListener(view -> {
        String link =
                vipBannerLink == null
                        ? ""
                        : vipBannerLink.trim();

        if (link.isEmpty()) {
            return;
        }

        Intent intent =
                new Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                                link
                        )
                );

        try {
            startActivity(intent);
        } catch (
                android.content.ActivityNotFoundException error
        ) {
            Toast.makeText(
                    MainActivity.this,
                    "ဒီ link ကိုဖွင့်နိုင်သော app မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();
        }
    });
}

/*
 * လက်ရှိ banner state (local fallback / remote
 * imageUrl + version) အတိုင်း ImageView ထဲ render
 * မည်။ Scroll ပြန်တက်လာတိုင်း ဒီ state အတိုင်း
 * ပြန်ဆွဲမည်။
 */
private void renderVipBannerImage(
        ImageView bannerView
) {
    /*
     * Remote config မရသေးချိန် local banner ကို
     * fallback အဖြစ်ပြထားမည်။
     */
    if (vipBannerImageUrl.isEmpty()) {
        bannerView.setImageResource(
                R.drawable.vip_plan_banner
        );

        return;
    }

    /*
     * URL တူပြီး image ပြောင်းလဲသွားပါက
     * banner.version ပြောင်းခြင်းဖြင့် Glide cache
     * invalidation ဖြစ်မည်။
     */
    com.bumptech.glide.Glide
            .with(this)
            .load(vipBannerImageUrl)
            .signature(
                    new com.bumptech.glide
                            .signature
                            .ObjectKey(
                            vipBannerVersion.isEmpty()
                                    ? vipBannerImageUrl
                                    : vipBannerVersion
                    )
            )
            .diskCacheStrategy(
                    com.bumptech.glide
                            .load
                            .engine
                            .DiskCacheStrategy
                            .ALL
            )
            .placeholder(
                    R.drawable.vip_plan_banner
            )
            .error(
                    R.drawable.vip_plan_banner
            )
            .into(bannerView);
}

private void loadRemoteBanner() {
    /*
     * မြန်မာ tab kill-switch — /app-content ရဲ့
     * myanmar.enabled flag ကို စစ်သည်။
     * Network request အသစ် မရှိပါ (banner fetch
     * နှင့် အတူတူ ပါလာသော JSON ကိုသုံးသည်)။
     */
    updateMyanmarTab(null);

    AppContentManager.loadBanner(
            this,
            new AppContentManager.Callback() {
                @Override
                public void onContent(
                        JSONObject content
                ) {
                    runOnUiThread(() -> {
                        applyRemoteBanner(
                                content
                        );

                        updateMyanmarTab(
                                content
                        );
                    });
                }

                @Override
                public void onError(
                        Exception error
                ) {
                    /*
                     * Remote banner မရလျှင် drawable
                     * fallback ကိုဆက်ပြမည်။
                     */
                }
            }
    );
}

/*
 * မြန်မာ tab visibility — kill-switch။
 * enabled=true (သို့မဟုတ် field မရှိသေးလျှင်
 * default true) → ပြမည်။ Explicitly false → ဝှက်မည်။
 */
private void updateMyanmarTab(
        JSONObject content
) {
    if (navMyanmar == null) {
        return;
    }

    boolean enabled =
            AppContentManager.isMyanmarEnabled(
                    content
            );

    navMyanmar.setVisibility(
            enabled ? View.VISIBLE : View.GONE
    );
}

private void refreshRemoteNotification() {
    AppContentManager.refreshNotification(
            this,
            new AppContentManager.Callback() {
                @Override
                public void onContent(
                        JSONObject content
                ) {
                    runOnUiThread(() -> {
                        if (
                                isFinishing() ||
                                isDestroyed()
                        ) {
                            return;
                        }

                        applyRemoteNotification(
                                content
                        );
                    });
                }

                @Override
                public void onError(
                        Exception error
                ) {
                    /*
                     * Notification validation failure ကို
                     * user-facing error မပြဘဲ နောက် periodic
                     * refresh မှာပြန်ကြိုးစားမည်။
                     */
                }
            }
    );
}

private void applyRemoteBanner(
        JSONObject content
) {
    if (content == null) {
        return;
    }

    JSONObject banner =
            content.optJSONObject(
                    "banner"
            );

    if (banner == null) {
        return;
    }

    boolean enabled =
            banner.optBoolean(
                    "enabled",
                    false
            );

    String imageUrl =
            banner.optString(
                    "url",
                    ""
            ).trim();

    String link =
            banner.optString(
                    "link",
                    ""
            ).trim();

    String version =
            banner.optString(
                    "version",
                    "1"
            ).trim();

    vipBannerLink =
            link.isEmpty()
                    ? "https://t.me/"
                            + CryptoUtil.dec("eYfWVCItMPTfAWhBVxWccg==")
                    : link;

    /*
     * Remote banner enabled flag နှင့် image state ကို
     * သိမ်းပြီး လက်ရှိ view state နဲ့အညီ visibility
     * ချိန်မည် (full-grid တွင် အမြဲဝှက်ထားမည်)။
     * Banner item ပြနေလျှင် state အသစ်နဲ့ ပြန်ဆွဲမည်။
     */
    vipBannerWanted = enabled;
    vipBannerImageUrl = imageUrl;
    vipBannerVersion = version;
    refreshTopChromeVisibility();

    if (
            !enabled ||
            vipBannerAdapter == null ||
            vipBannerAdapter.getItemCount() == 0
    ) {
        return;
    }

    vipBannerAdapter.notifyItemChanged(0);
}

private void applyRemoteNotification(
        JSONObject content
) {
    if (content == null) {
        return;
    }

    showAnnouncementIfActive(
            content.optJSONObject(
                    "notice"
            )
    );
}


private void showAnnouncementIfActive(
        JSONObject notice
) {
    if (notice == null) {
        return;
    }

    boolean enabled =
            notice.optBoolean(
                    "enabled",
                    false
            );

    if (!enabled) {
        return;
    }

    long startAt =
            notice.optLong(
                    "startAt",
                    0L
            );

    long endAt =
            notice.optLong(
                    "endAt",
                    0L
            );

    long now =
            System.currentTimeMillis();

    if (
            startAt <= 0L ||
            endAt <= 0L ||
            now < startAt ||
            now > endAt
    ) {
        return;
    }

    String noticeId =
            notice.optString(
                    "id",
                    ""
            ).trim();

    String title =
            notice.optString(
                    "title",
                    "အသိပေးချက်"
            ).trim();

    String message =
            notice.optString(
                    "message",
                    ""
            ).trim();

    if (message.isEmpty()) {
        return;
    }

    /*
     * ID မပါသော legacy response ဖြစ်လျှင်
     * title/message ကို fallback identity အဖြစ်သုံးမည်။
     */
    if (noticeId.isEmpty()) {
        noticeId =
                title + "\n" + message;
    }

    if (
            noticeId.equals(
                    lastShownNoticeIdThisLaunch
            )
    ) {
        return;
    }

    lastShownNoticeIdThisLaunch =
            noticeId;

    View anchor = recycler;

    anchor.post(() -> {
        if (
                isFinishing() ||
                isDestroyed()
        ) {
            return;
        }

        AnnouncementDialog.show(
                MainActivity.this,
                title,
                message
        );
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
        premiumButtonWanted = false;
        refreshTopChromeVisibility();

        return;
    }

    premiumButton.setText(
            SessionManager.getPremiumLabel()
    );

    premiumButtonWanted = true;
    refreshTopChromeVisibility();
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
            profileRefreshInFlight
    ) {
        return;
    }

    long now =
            System.currentTimeMillis();

    /*
     * onCreate/onResume callback များ ဆက်တိုက်ဝင်လာလျှင်
     * duplicate request မဖြစ်စေရန်သာ 15 seconds throttle။
     *
     * VIP state ကို နာရီပေါင်းများစွာ cache မလုပ်တော့ပါ။
     */
    if (
            lastProfileRefreshAttemptAt > 0L &&
            now - lastProfileRefreshAttemptAt <
                    PROFILE_REFRESH_MIN_INTERVAL_MS
    ) {
        return;
    }

    lastProfileRefreshAttemptAt = now;
    profileRefreshInFlight = true;

    ApiClient.get(
            "auth/me",
            new ApiClient.Callback() {
                @Override
                public void onSuccess(
                        JSONObject json
                ) {
                    runOnUiThread(() -> {
                        profileRefreshInFlight = false;

                        JSONObject user =
                                json.optJSONObject(
                                        "user"
                                );

                        /*
                         * Password reset/device reset က server
                         * session ဖျက်ထားလျှင် user == null
                         * ပြန်လာမည်။
                         */
                        if (user == null) {
                            SessionManager.clear();

                            updateAccountButtons();
                            refreshHomeSections();

                            /*
                             * Account session မရှိတော့သောကြောင့်
                             * လက်ရှိ Continue/Recent/Download screen
                             * ကို guest namespace ဖြင့်ပြန်ဆွဲမည်။
                             */
                            if (isLocalCategory(category)) {
                                resetAndLoad();
                            }

                            return;
                        }

                        String previousUserId =
                                SessionManager.getUserId();

                        String currentUserId =
                                user.optString(
                                        "id",
                                        ""
                                ).trim();

                        long vipUntil =
                                user.optLong(
                                        "vipUntil",
                                        0L
                                );

                        String planType =
                                user.optString(
                                        "planType",
                                        vipUntil >
                                                System.currentTimeMillis()
                                                ? "premium"
                                                : "free"
                                );

                        SessionManager.saveAuth(
                                currentUserId,
                                json.optString(
                                        "csrf",
                                        SessionManager.getCsrf()
                                ),
                                user.optString(
                                        "username",
                                        SessionManager.getUsername()
                                ),
                                user.optString(
                                        "email",
                                        SessionManager.getEmail()
                                ),
                                vipUntil,
                                user.optInt(
                                        "planMonths",
                                        0
                                ),
                                planType
                        );

                        updateAccountButtons();

                        /*
                         * Account ပြောင်းသွားလျှင် Continue/Recent
                         * list ကို account အသစ် namespace နဲ့
                         * ချက်ချင်းပြန်ဆွဲမည်။
                         */
                        if (
                                !previousUserId.equals(
                                        currentUserId
                                )
                        ) {
                            refreshHomeSections();

                            if (isLocalCategory(category)) {
                                resetAndLoad();
                            }
                        }
                    });
                }

                @Override
                public void onError(
                        Exception error
                ) {
                    /*
                     * Internet မရှိရုံနဲ့ local session ကို
                     * မဖျက်ပါ။
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

    /*
     * Home (sections view): grid မ�load ဘဲ section
     * များသာ ပြမည်။
     */
    if (homeMode) {
        hideShimmer();
        progress.setVisibility(View.GONE);
        refreshHomeSections();
        return;
    }

    headerAdapter.setSections(
            Collections.emptyList()
    );

    if (isLocalCategory(category)) {
        hideShimmer();
        loadLocalCategory();
        return;
    }

    showShimmer();
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
            hideShimmer();
            openLogin();
            return;
        }

        isLoading = true;

        final int generation =
                requestGeneration;

        final int requestedPage =
                currentPage + 1;

        /*
         * Page 1 (initial load) မှာ shimmer ပြနေပြီမို့
         * အောက်က spinner မလိုပါ။ Pagination (page 2+)
         * မှာသာ spinner ပြမည်။
         */
        if (requestedPage > 1) {
            progress.setVisibility(View.VISIBLE);
        }

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

        /*
 * Online title lists တွေကို persistent cache မသုံးပါ။
 *
 * Admin က title အသစ်ထည့်သောအခါ page 1 အဟောင်းနှင့်
 * page 2 အသစ် ရောသွားခြင်းကို ကာကွယ်ရန်
 * server ကိုတိုက်ရိုက် request လုပ်မည်။
 *
 * Movie detail caching ကို ApiClient.getCached()
 * ဖြင့် အခြားနေရာတွင် ဆက်သုံးနိုင်သည်။
 */
requestTitlePage(
        path,
        requestedPage,
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
                            hideShimmer();

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
            /*
             * Pagination အတွင်း page boundary
             * ပြောင်းသွားလျှင် title တစ်ခုတည်း
             * နှစ်ကြိမ်ပါလာနိုင်သည်။
             *
             * id သို့မဟုတ် slug တူလျှင်
             * duplicate ထပ်မထည့်ဘဲ
             * server ကပို့လာသော နောက်ဆုံး data ဖြင့်
             * အဟောင်းကို update လုပ်မည်။
             */
            addOrReplaceOnlineItem(item);
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
                                                : "ရှာဖွေခြင်း မတွေ့ရှိပါ။"
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
        hideShimmer();

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
private void requestTitlePage(
        String path,
        int requestedPage,
        ApiClient.Callback callback
) {
    boolean cacheEligible =
            requestedPage == 1 &&
            !"favorites".equals(category) &&
            search != null &&
            search.trim().isEmpty();

    if (cacheEligible) {
        /*
         * ApiClient က hasMore=false ဖြစ်သော
         * single-page category response ကိုသာ
         * အမှန်တကယ်သိမ်းပေးမည်။
         *
         * hasMore=true ဖြစ်လျှင် network response ကို
         * ပြပေးမည်၊ cache ထဲမသိမ်းပါ။
         */
        ApiClient.getCached(
                path,
                CATALOG_CACHE_MS,
                callback
        );

        return;
    }

    /*
     * Pagination page 2+၊ search နှင့် favorites
     * အားလုံးကို server မှတိုက်ရိုက်ယူမည်။
     */
    ApiClient.get(
            path,
            callback
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
/*
 * Local full-grid မဟုတ်တော့သည့်အချိန်
 * lifecycle callback သို့မဟုတ် queued action အဟောင်းက
 * ဒီ method ကိုခေါ်လာလျှင် UI ကို မပြောင်းစေရန်။
 */
if (homeMode || !isLocalCategory(category)) {
    return;
}

    progress.setVisibility(View.GONE);
    hideShimmer();
    isLoading = false;
    hasMore = false;

    List<JSONObject> items;

    switch (category) {
        case "continue":
            items =
                    LocalStore
                            .getContinueWatching();

            break;

        case "downloads":
            items =
                    LocalStore
                            .getDownloadHistory();

            break;

        case "recent":
        default:
            items =
                    LocalStore
                            .getRecentlyViewed();

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
/*
 * Online pagination response များကို merge လုပ်သောအခါ
 * title တစ်ခုတည်း ထပ်မပါစေရန် စစ်ပေးမည်။
 *
 * ID တူလျှင် title တစ်ခုတည်းဟုသတ်မှတ်မည်။
 * ID မရှိသော response အတွက် slug ကို fallback
 * identity အဖြစ်အသုံးပြုမည်။
 *
 * Duplicate တွေ့လျှင် အဟောင်းကို server ကပို့သော
 * နောက်ဆုံး JSONObject ဖြင့် အစားထိုးမည်။
 */
private void addOrReplaceOnlineItem(
        JSONObject incomingItem
) {
    if (incomingItem == null) {
        return;
    }

    String incomingId =
            incomingItem.optString(
                    "id",
                    ""
            ).trim();

    String incomingSlug =
            incomingItem.optString(
                    "slug",
                    ""
            ).trim();

    for (
            int index = 0;
            index < allItems.size();
            index++
    ) {
        JSONObject currentItem =
                allItems.get(index);

        if (currentItem == null) {
            continue;
        }

        String currentId =
                currentItem.optString(
                        "id",
                        ""
                ).trim();

        String currentSlug =
                currentItem.optString(
                        "slug",
                        ""
                ).trim();

        boolean sameId =
                !incomingId.isEmpty() &&
                !currentId.isEmpty() &&
                incomingId.equals(currentId);

        boolean sameSlug =
                !incomingSlug.isEmpty() &&
                !currentSlug.isEmpty() &&
                incomingSlug.equals(currentSlug);

        if (sameId || sameSlug) {
            /*
             * Existing position ကိုမပြောင်းဘဲ
             * title data ကို အသစ်ဖြင့် update လုပ်မည်။
             */
            allItems.set(
                    index,
                    incomingItem
            );

            return;
        }
    }

    /*
     * id/slug တူသော item မရှိမှသာ
     * list အဆုံးတွင်အသစ်ထည့်မည်။
     */
    allItems.add(incomingItem);
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
                                    java.util.Locale.ROOT
                            );

    if (query.isEmpty()) {
        return true;
    }

    String[] keys = {
            "title",
            "original_title",
            "actress",
            "year",
            "release_date"
    };

    for (String key : keys) {
        if (!item.has(key) || item.isNull(key)) {
            continue;
        }

        String value =
                item.optString(key, "")
                        .trim()
                        .toLowerCase(
                                java.util.Locale.ROOT
                        );

        if (
                !value.isEmpty() &&
                !"null".equals(value) &&
                value.contains(query)
        ) {
            return true;
        }
    }

    return false;
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