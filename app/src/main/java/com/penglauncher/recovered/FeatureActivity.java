package com.penglauncher.recovered;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class FeatureActivity extends BasePageActivity {
    public static final String SETTINGS = "settings";
    public static final String ABOUT = "about";
    public static final String EXTRA_PAGE = "page";
    public static final String WALLPAPER_PREFERENCES = "home_wallpaper";
    public static final String WALLPAPER_PATH_KEY = "path";
    private static final int SELECT_WALLPAPER_REQUEST = 100;

    public static void open(Context context, String page) {
        Intent intent = new Intent(context, FeatureActivity.class);
        intent.putExtra(EXTRA_PAGE, page);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ABOUT.equals(getIntent().getStringExtra(EXTRA_PAGE))) {
            about();
        } else {
            settings();
        }
    }

    private void settings() {
        showPage("设置", null);
        addAction("Wi-Fi", "打开系统 Wi-Fi 设置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        addAction("首页壁纸", "选择本地图片作为首页背景", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectWallpaper();
            }
        });
        addAction("关于 PENG 桌面", "应用背景、目的与作者", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FeatureActivity.open(FeatureActivity.this, ABOUT);
            }
        });
    }

    private void about() {
        showPage("关于 PENG 桌面", null);
        addInfo("应用背景\nPENG 桌面是一款为 Android TV 设备打造的轻量级自定义桌面。");
        addInfo("应用目的\n提供简洁、可自定义且适合遥控器操作的应用入口。");
        addInfo("Android 兼容性\n支持 Android 4.0（API 14）及以上；当前目标版本为 Android 15（API 35）。");
        addInfo("作者\nPENG");
    }

    private void selectWallpaper() {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, SELECT_WALLPAPER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SELECT_WALLPAPER_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        copyWallpaper(data.getData());
    }

    private void copyWallpaper(Uri sourceUri) {
        File target = new File(getFilesDir(), "home_wallpaper");
        try (InputStream source = getContentResolver().openInputStream(sourceUri);
                FileOutputStream destination = new FileOutputStream(target)) {
            if (source == null) {
                Toast.makeText(this, "无法读取所选图片", Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                destination.write(buffer, 0, bytesRead);
            }
            getSharedPreferences(WALLPAPER_PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(WALLPAPER_PATH_KEY, target.getAbsolutePath())
                    .apply();
            Toast.makeText(this, "首页壁纸已更新", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            Toast.makeText(this, "保存壁纸失败", Toast.LENGTH_SHORT).show();
        }
    }
}
