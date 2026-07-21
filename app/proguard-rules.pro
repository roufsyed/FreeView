# Add project specific ProGuard / R8 rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
#
# With minifyEnabled=true, R8 shrinks, optimizes, and obfuscates by default.
# These rules enable a little more optimization and keep the few things that are
# used reflectively or from XML, so the release build behaves like debug.

# --- Optimization ---
# Let R8 widen access modifiers so it can inline and merge more aggressively.
-allowaccessmodification

# --- Readable release crash reports ---
# Keep line numbers in stack traces, but hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Custom WebView referenced by name in res/layout/activity_main.xml ---
-keep class com.rouf.freeview.NestedScrollWebView {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# --- ViewModels are instantiated reflectively by ViewModelProvider ---
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# --- Fragments are re-instantiated reflectively by the FragmentManager ---
-keepclassmembers class * extends androidx.fragment.app.Fragment {
    <init>();
}

# --- WebView JavaScript bridge: none today, but safe if one is ever added ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
