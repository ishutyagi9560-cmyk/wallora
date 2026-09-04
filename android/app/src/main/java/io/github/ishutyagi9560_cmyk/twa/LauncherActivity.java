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
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsSession;

public class LauncherActivity
        extends com.google.androidbrowserhelper.trusted.LauncherActivity {

    private static final String TAG = "WalloraBridge";

    private static final Uri WALLORA_ORIGIN =
            Uri.parse("https://ishutyagi9560-cmyk.github.io");

    private CustomTabsClient mClient;
    private CustomTabsSession mSession;
    private boolean mValidated = false;
    private boolean mNavigationFinished = false;
    private boolean mPostMessageRequested = false;

    private void requestPostMessageChannelIfReady() {
        if (!mValidated || !mNavigationFinished || mSession == null || mPostMessageRequested) {
            return;
        }

        boolean requested = mSession.requestPostMessageChannel(
                WALLORA_ORIGIN,
                WALLORA_ORIGIN,
                new Bundle());

        Log.d(TAG, "PostMessage channel requested: " + requested);

        if (requested) {
            mPostMessageRequested = true;
        }
    }

    private final CustomTabsCallback mCustomTabsCallback =
            new CustomTabsCallback() {

        @Override
        public void onRelationshipValidationResult(
                int relation,
                @NonNull Uri requestedOrigin,
                boolean result,
                @Nullable Bundle extras) {

            mValidated = result;

            Log.d(TAG, "Origin validation: " + result);
            requestPostMessageChannelIfReady();
        }

        @Override
        public void onNavigationEvent(
                int navigationEvent,
                @Nullable Bundle extras) {

            if (navigationEvent != NAVIGATION_FINISHED) {
                return;
            }

            mNavigationFinished = true;
            requestPostMessageChannelIfReady();
        }

        @Override
        public void onMessageChannelReady(@Nullable Bundle extras) {
            Log.d(TAG, "PostMessage channel ready.");


            if (mSession != null) {
                mSession.postMessage("WALLORA_READY", null);
            }
        }

        @Override
        public void onPostMessage(
                @NonNull String message,
                @Nullable Bundle extras) {

            super.onPostMessage(message, extras);

            Log.d(TAG, "Web message: " + message);

            if (message.startsWith("SET_WALLPAPER:")) {

                String imageUrl =
                        message.substring("SET_WALLPAPER:".length());

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
                            } else {
                                mSession.postMessage(
                                        "WALLPAPER_SET_FAILED",
                                        null);
                            }
                        }));
            }
        }
    };

    private final CustomTabsServiceConnection mConnection =
            new CustomTabsServiceConnection() {

        @Override
        public void onCustomTabsServiceConnected(
                @NonNull ComponentName name,
                @NonNull CustomTabsClient client) {

            mClient = client;

            mClient.warmup(0);

            mSession =
                    mClient.newSession(mCustomTabsCallback);

            mNavigationFinished = false;
            mPostMessageRequested = false;

            if (mSession != null) {
                mSession.validateRelationship(
                        CustomTabsService.RELATION_USE_AS_ORIGIN,
                        WALLORA_ORIGIN,
                        null);
            }
        }

        @Override
        public void onServiceDisconnected(
                @NonNull ComponentName name) {

            mClient = null;
            mSession = null;
            mValidated = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        } else {
            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        bindCustomTabs();
    }

    private void bindCustomTabs() {

        String packageName =
                CustomTabsClient.getPackageName(this, null);

        if (packageName == null) {
            Log.d(TAG, "No Custom Tabs provider found.");
            return;
        }

        CustomTabsClient.bindCustomTabsService(
                this,
                packageName,
                mConnection);
    }

    @Override
    protected void onDestroy() {

        try {
            unbindService(mConnection);
        } catch (Exception ignored) {
        }

        mSession = null;
        mClient = null;

        super.onDestroy();
    }

    @Override
    protected Uri getLaunchingUrl() {
        return super.getLaunchingUrl();
    }
}
