package com.otaupdater.utils;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.TextUtils;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.otaupdater.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public abstract class BaseStaticTextActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        BufferedReader in = null;
        StringBuilder data = null;
        try {
            data = new StringBuilder();
            char[] buf = new char[4096];
            int numRead;
            in = new BufferedReader(new InputStreamReader(getAssets().open(getSourceFileName())));
            while ((numRead = in.read(buf)) >= 0) {
                data.append(buf, 0, numRead);
            }
        } catch (IOException e) {
            showErrorAndFinish();
            return;
        } finally {
            if (in != null) {
                try { in.close(); }
                catch (IOException ignored) { }
            }
        }

        if (TextUtils.isEmpty(data)) {
            showErrorAndFinish();
            return;
        }

        WebView webView = new WebView(this);

        // Begin the loading.  This will be done in a separate thread in WebView.
        webView.loadDataWithBaseURL(null, data.toString(), "text/plain", "utf-8", null);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                setContentView(view);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return false;
    }

    private void showErrorAndFinish() {
        Toast.makeText(this, R.string.toast_file_error, Toast.LENGTH_LONG).show();
        finish();
    }

    protected abstract String getSourceFileName();
}
