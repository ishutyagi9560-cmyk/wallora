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

import java.lang.reflect.Field;

public class LauncherActivity
        extends com.google.androidbrowserhelper.trusted.LauncherActivity {

    private static final String TAG = "WalloraBridge";

    private static final Uri WALLORA_ORIGIN =
            Uri.parse("https://ishutyagi9560-cmyk.github.io");

    private CustomTabsSession mActualSession;
    private final CustomTabsServiceConnection mServiceConnection = new CustomTabsServiceConnection() {
        @Override
        public void onCustomTabsServiceConnected(@NonNull ComponentName name, @NonNull CustomTabsClient client) {
            client.warmup(0);
            Log.d(TAG, "Custom Tabs service warmed up.");
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            Log.d(TAG, "Custom Tabs service disconnected.");
        }
    };
    private boolean mChannelRequested = false;

    private CustomTabsSession getActualTwaSession() {
        try {
            Field launcherField =
                    com.google.androidbrowserhelper.trusted.LauncherActivity.class
                            .getDeclaredField("mTwaLauncher");

            launcherField.setAccessible(true);

            Object twaLauncher = launcherField.get(this);

            if (twaLauncher == null) {
                Log.d(TAG, "TwaLauncher is null.");
                return null;
            }

            Field sessionField =
                    twaLauncher.getClass().getDeclaredField("mSession");

            sessionField.setAccessible(true);

            Object session = sessionField.get(twaLauncher);

            if (session instanceof CustomTabsSession) {
                return (CustomTabsSession) session;
            }

        } catch (Exception e) {
            Log.e(TAG, "Unable to get actual TWA session.", e);
        }

        return null;
    }

    private void requestBridgeChannel() {
        if (mChannelRequested) {
            return;
        }

        CustomTabsSession session = getActualTwaSession();

        if (session == null) {
            Log.d(TAG, "Actual TWA session not ready.");
            return;
        }

        mActualSession = session;

        boolean requested = session.requestPostMessageChannel(
                WALLORA_ORIGIN,
                WALLORA_ORIGIN,
                new Bundle());

        Log.d(TAG, "PostMessage channel requested: " + requested);

        if (requested) {
            mChannelRequested = true;
        }
    }

    private final CustomTabsCallback mCustomTabsCallback =
            new CustomTabsCallback() {

        @Override
        public void onNavigationEvent(
                int navigationEvent,
                @Nullable Bundle extras) {

            super.onNavigationEvent(navigationEvent, extras);

            if (navigationEvent == NAVIGATION_FINISHED) {
                Log.d(TAG, "TWA navigation finished.");
                requestBridgeChannel();
            }
        }

        @Override
        public void onMessageChannelReady(
                @Nullable Bundle extras) {

            super.onMessageChannelReady(extras);

            Log.d(TAG, "PostMessage channel ready.");

            if (mActualSession != null) {
                mActualSession.postMessage(
                        "WALLORA_READY",
                        null);
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
                return;
            }

            WallpaperHelper.setWallpaperAsync(
                    LauncherActivity.this,
                    imageUrl,
                    success -> runOnUiThread(() -> {

                        if (mActualSession == null) {
                            return;
                        }

                        if (success) {
                            mActualSession.postMessage(
                                    "WALLPAPER_SET_SUCCESS",
                                    null);
                        } else {
                            mActualSession.postMessage(
                                    "WALLPAPER_SET_FAILED",
                                    null);
                        }
                    }));
        }
    };

    @Override
    protected CustomTabsCallback getCustomTabsCallback() {
        return mCustomTabsCallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String packageName = CustomTabsClient.getPackageName(this, null);
        if (packageName != null) {
            CustomTabsClient.bindCustomTabsService(this, packageName, mServiceConnection);
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unbindService(mServiceConnection);
        } catch (Exception ignored) {
        }
        mActualSession = null;
        super.onDestroy();
    }

    @Override
    protected Uri getLaunchingUrl() {
        return super.getLaunchingUrl();
    }
}
