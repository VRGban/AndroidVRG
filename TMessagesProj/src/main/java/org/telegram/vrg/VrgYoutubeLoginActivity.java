package org.telegram.vrg;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.vrg.music.VrgYoutube;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class VrgYoutubeLoginActivity extends BaseFragment {

    private WebView webView;
    private boolean finished;

    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Вход в YouTube");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();

        webView = new WebView(context);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString(UA);
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                checkLoggedIn(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
        });
        frame.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frame;
        webView.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F");
        return fragmentView;
    }

    private void checkLoggedIn(String url) {
        if (finished || url == null || !url.contains("youtube.com")) {
            return;
        }
        CookieManager cm = CookieManager.getInstance();
        String yt = cm.getCookie("https://www.youtube.com");
        String goog = cm.getCookie("https://www.google.com");
        String accounts = cm.getCookie("https://accounts.google.com");
        String all = (yt == null ? "" : yt) + ";" + (goog == null ? "" : goog) + ";" + (accounts == null ? "" : accounts);
        if (!all.contains("SAPISID") && !all.contains("__Secure-3PSID") && !all.contains("__Secure-1PSID")) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder("# Netscape HTTP Cookie File\n");
            appendCookies(sb, ".youtube.com", yt);
            appendCookies(sb, ".google.com", goog);
            appendCookies(sb, "accounts.google.com", accounts);
            File f = VrgYoutube.cookiesFile();
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            finished = true;
            cm.removeAllCookies(null);
            AndroidUtilities.runOnUIThread(() -> {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Вход в YouTube выполнен").show();
                finishFragment();
            }, 400);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void appendCookies(StringBuilder sb, String domain, String cookieString) {
        if (cookieString == null) {
            return;
        }
        for (String part : cookieString.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = p.substring(0, eq);
            String value = p.substring(eq + 1);
            sb.append(domain).append('\t').append(domain.startsWith(".") ? "TRUE" : "FALSE").append('\t').append('/').append('\t')
                .append("TRUE").append('\t').append("2147483647").append('\t').append(name).append('\t').append(value).append('\n');
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (webView != null) {
            try {
                webView.destroy();
            } catch (Throwable ignore) {
            }
            webView = null;
        }
    }
}
