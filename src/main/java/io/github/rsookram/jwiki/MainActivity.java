package io.github.rsookram.jwiki;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.InflaterInputStream;

public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSION = 1;

    private WebView webView;
    private EditText searchBar;
    private ListView list;

    private Adapter adapter;

    private final OnBackInvokedCallback onBackInvokedCallback = new OnBackInvokedCallback() {
        @Override
        public void onBackInvoked() {
            webView.goBack();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        searchBar = findViewById(R.id.search);
        webView = findViewById(R.id.web_view);
        list = findViewById(R.id.list);

        adapter = new Adapter(this, new ArrayList<>(), searchResult -> {
            searchBar.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);

            searchBar.setText("");
            loadEntry(searchResult.offset);
        });
        list.setAdapter(adapter);

        applySystemUiVisibility();

        if (Environment.isExternalStorageManager()) {
            setup();
        } else {
            startActivityForResult(
                    new Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.fromParts("package", getPackageName(), null)
                    ),
                    REQUEST_PERMISSION
            );
        }
    }

    private void setup() {
        Wiki wiki = Wiki.getInstance();

        AssetManager assets = getAssets();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    if ("/-/style.css".equals(request.getUrl().getPath())) {
                        try {
                            InputStream is = assets.open("style.css");
                            return new WebResourceResponse("text/css", "UTF-8", is);
                        } catch (IOException ignored) {
                            // Fallthrough
                        }
                    }

                    // This handles ignoring other requests for CSS, JS, and images.
                    return new WebResourceResponse("text/plain", "UTF-8", null);
                }

                boolean isOffset = request.getUrl().getBooleanQueryParameter("offset", false);
                String path = request.getUrl().getPath().substring("/".length());

                byte[] entry;
                if (isOffset) {
                    entry = wiki.getEntry(Long.parseLong(path));
                } else {
                    long offset = wiki.getEntryOffset(path);
                    if (offset < 0) {
                        offset = wiki.getEntryOffset("HTTP_404");
                    }

                    entry = wiki.getEntry(offset);
                }

                InputStream is = new InflaterInputStream(new ByteArrayInputStream(entry));
                return new WebResourceResponse("text/html", "gzip", is);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                if (!view.canGoBack()) {
                    getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(onBackInvokedCallback);
                    return;
                }

                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        onBackInvokedCallback
                );
            }
        });

        loadEntry("ウィキペディア");

        searchBar.addTextChangedListener(new TextWatcher() {
            private String lastQuery = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = searchBar.getText().toString();
                if (query.isEmpty()) {
                    list.setVisibility(View.GONE);
                    adapter.setResults(new ArrayList<>());
                    lastQuery = "";
                    return;
                }

                list.setVisibility(View.VISIBLE);

                if (query.equals(lastQuery)) {
                    return;
                }
                lastQuery = query;

                List<SearchResult> results = wiki.query(query);
                adapter.setResults(results);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    void loadEntry(String name) {
        webView.loadUrl("wiki:///" + name);
    }

    void loadEntry(long offset) {
        webView.loadUrl("wiki:///" + offset + "?offset=true");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PERMISSION && Environment.isExternalStorageManager()) {
            setup();
        }
    }

    private void applySystemUiVisibility() {
        getWindow().setDecorFitsSystemWindows(false);

        list.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(systemInsets.left, 0, systemInsets.right, systemInsets.bottom);

            return insets;
        });

        searchBar.setOnApplyWindowInsetsListener((v, insets) -> {
            int sidePadding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8.0f, getResources().getDisplayMetrics()
            );

            Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(systemInsets.left + sidePadding, systemInsets.top, systemInsets.right + sidePadding, 0);
            return insets;
        });
    }

    private static class Adapter extends ArrayAdapter<SearchResult> {

        private final Consumer<SearchResult> onClick;

        Adapter(Context context, List<SearchResult> items, Consumer<SearchResult> onClick) {
            super(context, -1 /* unused */, items);
            this.onClick = onClick;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_result, parent, false);
            }

            SearchResult item = getItem(position);
            ((TextView) view.findViewById(R.id.name)).setText(item.key, 0, item.key.length);

            view.setOnClickListener(v -> onClick.accept(item));

            return view;
        }

        public void setResults(List<SearchResult> results) {
            clear();
            addAll(results);
        }
    }
}
