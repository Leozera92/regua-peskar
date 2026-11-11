package br.com.peskar.regua;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.WindowManager;
import android.provider.MediaStore;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
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
import java.util.ArrayList;

import org.json.JSONArray;

/**
 * PESKAR Régua 2.0 — casca Android.
 * Carrega o app web de assets/index.html por uma origem https local
 * (necessária para a câmera ao vivo via getUserMedia).
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 10;
    private static final int REQ_FILE = 11;
    private static final int REQ_CAMERA_PHOTO = 12;
    private static final int REQ_LOCATION = 13;
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";

    private WebView webView;
    private PermissionRequest pendingPermission;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private boolean pendingCapture;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private volatile String pendingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0f5a2e"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f5a2e"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#f3f7f1"));
        setContentView(webView);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setGeolocationEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "PeskarNative");

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
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermission = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                boolean capture = params != null && params.isCaptureEnabled();
                if (capture && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    pendingCapture = true;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PHOTO);
                    return true;
                }
                return openChooser(capture);
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

    /** Mensagem do juiz compartilhada pelo WhatsApp/Telegram (texto com o código PESKAR1...). */
    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type = intent.getType();
        if (type == null || !type.startsWith("text/")) return;
        CharSequence t = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (t == null) return;
        pendingText = t.toString();
        if (webView != null) webView.evaluateJavascript("window.onTextoRecebido && window.onTextoRecebido()", null);
    }

    /** Câmera nativa (foto em resolução máxima) e, se não for captura direta, também a galeria. */
    private Intent cameraIntent() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            File dir = new File(getCacheDir(), "fotos");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "captura_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fotos", f);
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return cam;
        } catch (Exception e) {
            cameraUri = null;
            return null;
        }
    }

    private boolean openChooser(boolean capture) {
        try {
            Intent cam = cameraIntent();
            if (capture && cam != null) {
                startActivityForResult(cam, REQ_FILE);
                return true;
            }
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            Intent chooser = Intent.createChooser(pick, "Escolha a foto");
            if (cam != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
            startActivityForResult(chooser, REQ_FILE);
            return true;
        } catch (Exception e) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_CAMERA && pendingPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermission.grant(pendingPermission.getResources());
            } else {
                pendingPermission.deny();
            }
            pendingPermission = null;
        }
        if (requestCode == REQ_LOCATION && pendingGeoCallback != null) {
            boolean ok = false;
            for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
            pendingGeoCallback.invoke(pendingGeoOrigin, ok, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
        if (requestCode == REQ_CAMERA_PHOTO && pendingCapture) {
            pendingCapture = false;
            openChooser(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
                else if (cameraUri != null) result = new Uri[]{cameraUri};
            }
            cameraUri = null;
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

    /** Ponte JS -> Android: monta os arquivos do pacote de revisão e abre o compartilhamento. */
    private class Bridge {
        private FileOutputStream out;

        private File exportDir() {
            File dir = new File(getCacheDir(), "export");
            if (!dir.exists()) dir.mkdirs();
            return dir;
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

        @JavascriptInterface
        public void share(String namesJson, String title) {
            shareText(namesJson, title, null);
        }

        @JavascriptInterface
        public void shareText(String namesJson, String title, String text) {
            try {
                JSONArray names = new JSONArray(namesJson);
                final ArrayList<Uri> uris = new ArrayList<>();
                boolean allZip = names.length() > 0;
                for (int i = 0; i < names.length(); i++) {
                    String n = new File(names.getString(i)).getName();
                    if (!n.toLowerCase().endsWith(".zip")) allZip = false;
                    File f = new File(exportDir(), n);
                    if (f.exists()) uris.add(FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fotos", f));
                }
                final String t = title;
                final String body = text;
                final String mime = allZip ? "application/zip" : "*/*";
                runOnUiThread(() -> {
                    Intent send;
                    if (uris.size() == 1) {
                        send = new Intent(Intent.ACTION_SEND);
                        send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                    } else {
                        send = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                    }
                    send.setType(mime);
                    send.putExtra(Intent.EXTRA_SUBJECT, t);
                    if (body != null) send.putExtra(Intent.EXTRA_TEXT, body);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(Intent.createChooser(send, t)); } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public String pendingText() {
            String t = pendingText;
            pendingText = null;
            return t;
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        private void closeQuietly() {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) { }
                out = null;
            }
        }
    }
}
