package com.logicxie.keuangan;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/index.html";
    private static final int REQ_STORAGE = 1001;

    private WebView webView;

    // JS yang disuntikkan agar simpan file (xlsx dsb.) lewat AndroidBridge
    private static final String INJECT_JS =
            "(function(){if(window.__androidSaveReady)return;window.__androidSaveReady=true;"
          + "window.simpanLewatLink=function(blob,namaFile){try{var reader=new FileReader();"
          + "reader.onloadend=function(){try{var res=String(reader.result||'');"
          + "var b64=res.substring(res.indexOf(',')+1);"
          + "AndroidBridge.saveFile(namaFile,blob.type||'application/octet-stream',b64);}"
          + "catch(e){alert('Gagal menyimpan file: '+e.message);}};"
          + "reader.onerror=function(){alert('Gagal membaca file.');};"
          + "reader.readAsDataURL(blob);}catch(e){alert('Gagal menyimpan file: '+e.message);}};})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("file://") || url.startsWith("about:")
                        || url.startsWith("data:") || url.startsWith("javascript:")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Tidak bisa membuka tautan", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(INJECT_JS, null);
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
        }
        super.onDestroy();
    }

    private static String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) return "Catatan-Keuangan.xlsx";
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
    }

    private String writeToDownloads(String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("tidak bisa membuat file di folder Download");
            OutputStream os = resolver.openOutputStream(uri);
            if (os == null) throw new Exception("tidak bisa membuka file tujuan");
            try {
                os.write(data);
                os.flush();
            } finally {
                os.close();
            }
            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, v, null, null);
            return "Download/" + name;
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(f);
            try {
                fos.write(data);
                fos.flush();
            } finally {
                fos.close();
            }
            MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()},
                    new String[]{mime}, null);
            return "Download/" + name;
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void saveFile(final String fileName, final String mimeType, final String base64) {
            final String safeName = sanitizeName(fileName);
            final String safeMime = (mimeType == null || mimeType.isEmpty())
                    ? "application/octet-stream" : mimeType;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] data = Base64.decode(base64, Base64.DEFAULT);
                        final String location = writeToDownloads(safeName, safeMime, data);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        "File tersimpan di " + location, Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        "Gagal menyimpan file: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void openExternal(String url) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
        }
    }
}
