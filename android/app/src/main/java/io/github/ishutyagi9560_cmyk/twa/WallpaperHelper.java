package io.github.ishutyagi9560_cmyk.twa;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WallpaperHelper {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();


    public static void setWallpaperAsync(Context context, String imageUrl, WallpaperCallback callback) {
        EXECUTOR.execute(() -> {
            boolean success = setWallpaper(context, imageUrl);
            callback.onResult(success);
        });
    }

    public interface WallpaperCallback {
        void onResult(boolean success);
    }

    public static boolean setWallpaper(Context context, String imageUrl) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }

            try (InputStream input = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);

                if (bitmap == null) {
                    return false;
                }

                WallpaperManager manager =
                        WallpaperManager.getInstance(context);

                manager.setBitmap(bitmap);
                bitmap.recycle();

                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
