package vn.dongthap.vkskv5;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;
    private static final String HOME = "file:///android_asset/index.html";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // Needed only so the packaged local HTML can call HTTPS APIs/CDNs.
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);

        // Isolated native bridge used by the new "Sơ đồ vụ việc" PDF export only.
        webView.addJavascriptInterface(new AndroidFileBridge(), "AndroidFile");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equalsIgnoreCase(uri.getScheme())) return false;
                if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                    openExternal(uri);
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallbackNew,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = filePathCallbackNew;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Không mở được trình chọn file.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        openExternal(request.getUrl());
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        if (savedInstanceState == null) webView.loadUrl(HOME);
        else webView.restoreState(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) { }
    }

    private class AndroidFileBridge {
        @JavascriptInterface
        public void printHtmlToPdf(String html, String requestedName) {
            runOnUiThread(() -> {
                try {
                    final WebView printView = new WebView(MainActivity.this);
                    printView.setBackgroundColor(Color.WHITE);
                    WebSettings ps = printView.getSettings();
                    ps.setJavaScriptEnabled(false);
                    ps.setLoadsImagesAutomatically(true);
                    ps.setDefaultTextEncodingName("UTF-8");
                    ps.setUseWideViewPort(true);
                    ps.setLoadWithOverviewMode(false);

                    // IMPORTANT: the WebView used for printing must be attached and laid out.
                    // An unattached WebView can produce a completely blank PDF on Android.
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    root.addView(printView, lp);
                    printView.setTranslationX(-10000f); // keep it off-screen but still attached/renderable

                    printView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            view.postDelayed(() -> {
                                try {
                                    int w = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
                                    int h = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
                                    printView.measure(
                                            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                                            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
                                    printView.layout(0, 0, w, h);

                                    String jobName = requestedName == null ? "So do vu an" : requestedName;
                                    jobName = jobName.replaceAll("[\\\\/:*?\"<>|]", "")
                                                     .replaceAll("(?i)\\.pdf$", "")
                                                     .trim();
                                    if (jobName.isEmpty()) jobName = "So do vu an";

                                    PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
                                    PrintDocumentAdapter adapter = printView.createPrintDocumentAdapter(jobName);
                                    PrintAttributes attrs = new PrintAttributes.Builder()
                                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                                            .setMinMargins(new PrintAttributes.Margins(300, 300, 300, 300))
                                            .build();

                                    printManager.print(jobName, adapter, attrs);
                                    // Do not remove/destroy printView here: Android's print service
                                    // continues reading it asynchronously after print() returns.
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this,
                                            "Xuất PDF thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }, 700);
                        }
                    });

                    printView.loadDataWithBaseURL(
                            "https://local.vks/",
                            html,
                            "text/html",
                            "UTF-8",
                            null);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Xuất PDF thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void savePdfBase64(String dataUri, String requestedName) {
            runOnUiThread(() -> {
                try {
                    String base64 = dataUri;
                    int comma = base64.indexOf(',');
                    if (comma >= 0) base64 = base64.substring(comma + 1);
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

                    String name = requestedName == null ? "so-do-vu-an.pdf" : requestedName;
                    name = name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
                    if (name.isEmpty()) name = "so-do-vu-an.pdf";
                    if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VKS_KV5");
                        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new Exception("Không tạo được file PDF.");

                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os == null) throw new Exception("Không mở được file PDF.");
                            os.write(bytes);
                            os.flush();
                        }

                        values.clear();
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);
                        Toast.makeText(MainActivity.this,
                                "Đã lưu PDF vào Tải xuống/VKS_KV5", Toast.LENGTH_LONG).show();
                    } else {
                        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (dir == null) throw new Exception("Không truy cập được thư mục lưu.");
                        File out = new File(dir, name);
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            fos.write(bytes);
                            fos.flush();
                        }
                        Toast.makeText(MainActivity.this,
                                "Đã lưu PDF: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Lưu PDF thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        // index.html is a single-page app; its own “Trang chủ” buttons handle internal navigation.
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.removeJavascriptInterface("AndroidFile");
            webView.destroy();
        }
        super.onDestroy();
    }
}
