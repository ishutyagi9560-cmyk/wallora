package io.github.ishutyagi9560_cmyk.twa;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.trusted.TrustedWebActivityIntent;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;

import com.google.androidbrowserhelper.trusted.TwaLauncher;
import com.google.androidbrowserhelper.trusted.splashscreens.SplashScreenStrategy;

public class LauncherActivity
        extends com.google.androidbrowserhelper.trusted.LauncherActivity {

    private static final String TAG = "WalloraBridge";

    private static final Uri WALLORA_ORIGIN =
            Uri.parse("https://ishutyagi9560-cmyk.github.io");

    private CustomTabsClient mClient;
    private CustomTabsSession mSession;
    private boolean mChannelRequested = false;

    private final CustomTabsServiceConnection mServiceConnection =
            new CustomTabsServiceConnection() {

                @Override
                public void onCustomTabsServiceConnected(
                        @NonNull ComponentName name,
                        @NonNull CustomTabsClient client) {

                    mClient = client;

                    client.warmup(0);

                    mSession = client.newSession(mCustomTabsCallback);

                    if (mSession == null) {
                        Log.e(TAG, "Unable to create Custom Tabs session.");
                        return;
                    }

                    Log.d(TAG, "Wallora Custom Tabs session created.");

                    launchTwa();
                }

                @Override
                public void onServiceDisconnected(
                        @NonNull ComponentName name) {

                    mClient = null;
                    mSession = null;

                    Log.d(TAG, "Custom Tabs service disconnected.");
                }
            };

    private final CustomTabsCallback mCustomTabsCallback =
            new CustomTabsCallback() {

                @Override
                public void onNavigationEvent(
                        int navigationEvent,
                        @Nullable Bundle extras) {

                    super.onNavigationEvent(navigationEvent, extras);

                    if (navigationEvent != NAVIGATION_FINISHED) {
                        return;
                    }

                    Log.d(TAG, "TWA navigation finished.");

                    requestBridgeChannel();
                }

                @Override
                public void onMessageChannelReady(
                        @Nullable Bundle extras) {

                    super.onMessageChannelReady(extras);

                    Log.d(TAG, "PostMessage channel ready.");

                    if (mSession != null) {
                        int result =
                                mSession.postMessage("WALLORA_READY", null);

                        Log.d(TAG,
                                "WALLORA_READY postMessage result: "
                                        + result);
                    }
                }

                @Override
                public void onPostMessage(
                        @NonNull String message,
                        @Nullable Bundle extras) {

                    super.onPostMessage(message, extras);

                    Log.d(TAG, "Web message: " + message);

                    if (!message.startsWith("SET_WALLPAPER:")) {
                        return;
                    }

                    String imageUrl =
                            message.substring("SET_WALLPAPER:".length());

                    if (imageUrl.isEmpty()) {
                        Log.e(TAG, "Empty wallpaper URL.");
                        return;
                    }

                    WallpaperHelper.setWallpaperAsync(
                            LauncherActivity.this,
                            imageUrl,
                            success -> runOnUiThread(() -> {

                                if (mSession == null) {
                                    return;
                                }

                                if (success) {
                                    mSession.postMessage(
                                            "WALLPAPER_SET_SUCCESS",
                                            null);

                                    Log.d(TAG,
                                            "Wallpaper set successfully.");
                                } else {
                                    mSession.postMessage(
                                            "WALLPAPER_SET_FAILED",
                                            null);

                                    Log.e(TAG,
                                            "Wallpaper set failed.");
                                }
                            }));
                }
            };

    private void requestBridgeChannel() {

        if (mChannelRequested) {
            return;
        }

        if (mSession == null) {
            Log.e(TAG, "Cannot request channel: session is null.");
            return;
        }

        boolean requested =
                mSession.requestPostMessageChannel(
                        WALLORA_ORIGIN,
                        WALLORA_ORIGIN,
                        new Bundle());

        Log.d(TAG,
                "PostMessage channel requested: " + requested);

        if (requested) {
            mChannelRequested = true;
        }
    }

    @Override
    protected boolean shouldLaunchImmediately() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String packageName =
                CustomTabsClient.getPackageName(this, null);

        if (packageName == null) {
            Log.e(TAG, "No Custom Tabs provider found.");
            return;
        }

        boolean bound =
                CustomTabsClient.bindCustomTabsService(
                        this,
                        packageName,
                        mServiceConnection);

        Log.d(TAG,
                "Custom Tabs service bind result: " + bound);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        } else {
            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    protected TwaLauncher createTwaLauncher() {
        return new WalloraTwaLauncher();
    }

    private class WalloraTwaLauncher extends TwaLauncher {

        WalloraTwaLauncher() {
            super(LauncherActivity.this);
        }

        @Override
        public void launch(
                TrustedWebActivityIntentBuilder twaBuilder,
                CustomTabsCallback customTabsCallback,
                @Nullable SplashScreenStrategy splashScreenStrategy,
                @Nullable Runnable completionCallback,
                FallbackStrategy fallbackStrategy) {

            if (mSession == null) {
                Log.e(TAG,
                        "Cannot launch TWA: session is null.");

                fallbackStrategy.launch(
                        LauncherActivity.this,
                        twaBuilder,
                        getProviderPackage(),
                        completionCallback);

                return;
            }

            Log.d(TAG,
                    "Launching TWA with Wallora-owned session.");

            TrustedWebActivityIntent intent =
                    twaBuilder.build(mSession);

            intent.launchTrustedWebActivity(
                    LauncherActivity.this);

            if (completionCallback != null) {
                completionCallback.run();
            }
        }
    }

    @Override
    protected void onDestroy() {

        try {
            unbindService(mServiceConnection);
        } catch (Exception ignored) {
        }

        mSession = null;
        mClient = null;

        super.onDestroy();
    }
}
