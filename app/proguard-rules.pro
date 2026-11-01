# Keep WebView callback signatures available to Android framework reflection.
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void onShowCustomView(...);
    public void onHideCustomView();
}
