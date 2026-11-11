package br.com.peskar.juiz;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.json.JSONArray;

/**
 * PESKAR Juiz — recebe os pacotes das capturas (WhatsApp/Telegram: Compartilhar ou Abrir com),
 * guarda em files/inbox e entrega ao app web pela origem https local (/inbox/<nome>).
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 21;
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0a3f20"));
        getWindow().setNavigationBarColor(Color.parseColor("#0a3f20"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#f3f7f1"));
        setContentView(webView);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/inbox/", new WebViewAssetLoader.InternalStoragePathHandler(this, inboxDir()))
                .build();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        webView.addJavascriptInterface(new Bridge(), "PeskarJuiz");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.setType("*/*");
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(Intent.createChooser(pick, "Escolha o pacote da captura"), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private File inboxDir() {
        File d = new File(getFilesDir(), "inbox");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File exportDir() {
        File d = new File(getCacheDir(), "export");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Copia os arquivos recebidos para files/inbox e avisa o app web. */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        final ArrayList<Uri> uris = new ArrayList<>();
        String action = intent.getAction();
        try {
            if (Intent.ACTION_SEND.equals(action)) {
                Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (u != null) uris.add(u);
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (list != null) uris.addAll(list);
            } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
                uris.add(intent.getData());
            }
            ClipData clip = intent.getClipData();
            if (uris.isEmpty() && clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) if (clip.getItemAt(i).getUri() != null) uris.add(clip.getItemAt(i).getUri());
            }
        } catch (Exception ignored) { }
        if (uris.isEmpty()) return;
        intent.setAction(null);
        new Thread(() -> {
            for (Uri u : uris) copyToInbox(u);
            runOnUiThread(() -> {
                if (webView != null) webView.evaluateJavascript("window.onPacotes && window.onPacotes()", null);
            });
        }).start();
    }

    private String displayName(Uri u) {
        String name = null;
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        if (name == null) name = u.getLastPathSegment();
        if (name == null) name = "pacote_" + System.currentTimeMillis();
        name = new File(name).getName().replaceAll("[^A-Za-z0-9._-]", "_");
        String type = null;
        try { type = getContentResolver().getType(u); } catch (Exception ignored) { }
        if (!name.contains(".") && type != null && type.contains("zip")) name = name + ".zip";
        return name;
    }

    private void copyToInbox(Uri u) {
        String name = displayName(u);
        File out = new File(inboxDir(), name);
        try (InputStream in = getContentResolver().openInputStream(u); OutputStream os = new FileOutputStream(out, false)) {
            if (in == null) return;
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } catch (Exception e) {
            out.delete();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    ClipData c = data.getClipData();
                    result = new Uri[c.getItemCount()];
                    for (int i = 0; i < c.getItemCount(); i++) result[i] = c.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    /** Ponte JS -> Android. */
    private class Bridge {
        private FileOutputStream out;

        @JavascriptInterface
        public String pending() {
            JSONArray a = new JSONArray();
            File[] fs = inboxDir().listFiles();
            if (fs != null) for (File f : fs) if (f.isFile() && f.length() > 0) a.put(f.getName());
            return a.toString();
        }

        @JavascriptInterface
        public void remove(String name) {
            File f = new File(inboxDir(), new File(name).getName());
            if (f.exists()) f.delete();
        }

        @JavascriptInterface
        public void reset() {
            closeQuietly();
            File[] old = exportDir().listFiles();
            if (old != null) for (File f : old) f.delete();
        }

        @JavascriptInterface
        public void begin(String name) {
            closeQuietly();
            try {
                out = new FileOutputStream(new File(exportDir(), new File(name).getName()), false);
            } catch (Exception e) {
                out = null;
            }
        }

        @JavascriptInterface
        public void append(String base64) {
            if (out == null) return;
            try {
                out.write(Base64.decode(base64, Base64.DEFAULT));
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void end() {
            closeQuietly();
        }

        /** Compartilha arquivos de export/ (pode ser lista vazia: só texto). */
        @JavascriptInterface
        public void shareText(String namesJson, String title, String text) {
            try {
                JSONArray names = new JSONArray(namesJson == null ? "[]" : namesJson);
                final ArrayList<Uri> uris = new ArrayList<>();
                for (int i = 0; i < names.length(); i++) {
                    File f = new File(exportDir(), new File(names.getString(i)).getName());
                    if (f.exists()) uris.add(FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".arquivos", f));
                }
                final String t = title;
                final String body = text;
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    if (uris.isEmpty()) {
                        send.setType("text/plain");
                    } else {
                        send.setType("text/csv");
                        send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    if (t != null) send.putExtra(Intent.EXTRA_SUBJECT, t);
                    if (body != null) send.putExtra(Intent.EXTRA_TEXT, body);
                    try { startActivity(Intent.createChooser(send, t)); } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
        }

        private void closeQuietly() {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) { }
                out = null;
            }
        }
    }
}
