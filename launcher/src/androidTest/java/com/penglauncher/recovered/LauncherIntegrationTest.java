package com.penglauncher.recovered;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class LauncherIntegrationTest {
    private static final String CARD_PREFERENCES = "launcher_cards";
    private static final String WALLPAPER_PREFERENCES = "home_wallpaper";
    private static final String WALLPAPER_PATH_KEY = "path";
    private static final String UNINSTALL_FIXTURE_PACKAGE = "com.peng.live.show";
    private static final byte[] PNG = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/"
                    + "ScL6pQAAAABJRU5ErkJggg==",
            Base64.DEFAULT);

    private Context targetContext;
    private UiDevice device;

    @Before
    public void setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        clearLauncherState();
    }

    @After
    public void tearDown() {
        clearLauncherState();
        device.pressBack();
    }

    @Test
    public void firstEntryPopulatesUniqueCardsAndBackKeepsHomeOpen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SharedPreferences preferences = activity.getSharedPreferences(
                        CARD_PREFERENCES, Context.MODE_PRIVATE);
                assertTrue(preferences.getBoolean("card_state_migrated", false));

                Set<String> components = new HashSet<>();
                for (int index = 0; index < 5; index++) {
                    assertEquals("auto", preferences.getString("card_state_" + index, null));
                    String packageName = preferences.getString("card_package_" + index, null);
                    String activityName = preferences.getString("card_activity_" + index, null);
                    assertNotNull(packageName);
                    assertNotNull(activityName);
                    assertTrue(components.add(packageName + "/" + activityName));
                }
            });

            device.pressBack();
            assertTrue(device.wait(Until.hasObject(By.pkg(targetContext.getPackageName())), 2_000));
        }
    }

    @Test
    public void manuallyPinnedCardSurvivesActivityReentry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final String[] component = new String[2];
            scenario.onActivity(activity -> {
                Object app = availableApps(activity).get(0);
                saveChoice(activity, 4, app);
                component[0] = preference(activity, "card_package_4");
                component[1] = preference(activity, "card_activity_4");
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("user", preference(activity, "card_state_4"));
                assertEquals(component[0], preference(activity, "card_package_4"));
                assertEquals(component[1], preference(activity, "card_activity_4"));
            });
        }
    }

    @Test
    public void missingPinnedAppRendersAddCardAfterReentry() {
        targetContext.getSharedPreferences(CARD_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("card_state_migrated", true)
                .putString("card_state_0", "user")
                .putString("card_package_0", "com.penglauncher.missing")
                .putString("card_activity_0", "MissingActivity")
                .commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertTrue(containsText(activity.getWindow().getDecorView(),
                    "添加应用")));
        }
    }

    @Test
    public void uninstallMenuOpensSystemConfirmationForAvailableApp() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                List<?> apps = availableApps(activity);
                assertFalse(apps.isEmpty());
                Object app = apps.get(0);
                Intent uninstall = (Intent) invoke(activity, "uninstallIntent",
                        new Class<?>[]{app.getClass()}, app);
                assertEquals(Intent.ACTION_UNINSTALL_PACKAGE, uninstall.getAction());
                assertTrue(uninstall.getData().toString().startsWith("package:"));
                showAllAppsMenu(activity, app);
            });

            assertTrue(device.wait(Until.hasObject(By.text("卸载")), 2_000));
        }
    }

    @Test
    public void uninstallingPinnedFixtureReplacesCardWithAddAction() throws Exception {
        Assume.assumeTrue(isPackageInstalled(UNINSTALL_FIXTURE_PACKAGE));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Object fixture = findAppByPackage(availableApps(activity), UNINSTALL_FIXTURE_PACKAGE);
                assertNotNull(fixture);
                saveChoice(activity, 0, fixture);
            });

            try (ParcelFileDescriptor result =
                         InstrumentationRegistry.getInstrumentation().getUiAutomation()
                                 .executeShellCommand("pm uninstall " + UNINSTALL_FIXTURE_PACKAGE);
                 InputStream input = new java.io.FileInputStream(result.getFileDescriptor())) {
                assertTrue(new String(readAll(input), StandardCharsets.UTF_8).contains("Success"));
            }

            for (int attempt = 0; attempt < 20; attempt++) {
                final boolean[] replaced = new boolean[1];
                scenario.onActivity(activity -> replaced[0] = containsText(
                        activity.getWindow().getDecorView(), "添加应用"));
                if (replaced[0]) {
                    return;
                }
                sleep(250);
            }
            throw new AssertionError("卸载后桌面卡片未刷新为添加应用");
        }
    }

    @Test
    public void transferServerAcceptsWallpaperAndFileAndRejectsBadRequests() throws Exception {
        try (ActivityScenario<MainActivity> scenario = startTransferPage()) {
            assertEquals(200, request("GET", "/", null, null).status);

            HttpResponse invalidWallpaper = request("POST", "/wallpaper",
                    "not an image".getBytes(StandardCharsets.UTF_8), null);
            assertEquals(400, invalidWallpaper.status);

            HttpResponse wallpaper = request("POST", "/wallpaper", PNG, null);
            assertEquals(200, wallpaper.status);
            scenario.onActivity(activity -> {
                String wallpaperPath = activity.getSharedPreferences(WALLPAPER_PREFERENCES,
                        Context.MODE_PRIVATE).getString(WALLPAPER_PATH_KEY, null);
                assertNotNull(wallpaperPath);
                assertTrue(new File(wallpaperPath).isFile());
            });

            byte[] fileContents = "PENG launcher transfer".getBytes(StandardCharsets.UTF_8);
            HttpResponse file = request("POST", "/file", fileContents, "launcher-test.txt");
            assertEquals(200, file.status);
            scenario.onActivity(activity -> {
                File directory = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "PENG");
                File saved = new File(directory, "launcher-test.txt");
                assertTrue(saved.isFile());
                assertArrayEquals(fileContents, readFile(saved));
                saved.delete();
            });

            assertEquals(400, request("POST", "/file",
                    "missing name".getBytes(StandardCharsets.UTF_8), null).status);
            assertEquals(404, request("GET", "/unknown", null, null).status);
        }
    }

    @Test
    public void resetWallpaperFromSettingsClearsStoredWallpaper() throws Exception {
        File wallpaper = new File(targetContext.getFilesDir(), "home_wallpaper");
        try (FileOutputStream stream = new FileOutputStream(wallpaper)) {
            stream.write(PNG);
        }
        targetContext.getSharedPreferences(WALLPAPER_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(WALLPAPER_PATH_KEY, wallpaper.getAbsolutePath())
                .commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> invoke(activity, "showFeaturePage",
                    new Class<?>[]{String.class}, FeatureActivity.SETTINGS));
            assertTrue(device.wait(Until.hasObject(By.text("重置首页壁纸")), 2_000));
            device.findObject(By.text("重置首页壁纸")).click();
            assertTrue(device.wait(Until.hasObject(By.text("重置")), 2_000));
            device.findObject(By.text("重置")).click();
            scenario.onActivity(activity -> {
                assertNull(activity.getSharedPreferences(WALLPAPER_PREFERENCES,
                        Context.MODE_PRIVATE).getString(WALLPAPER_PATH_KEY, null));
                assertFalse(wallpaper.exists());
            });
        }
    }

    private ActivityScenario<MainActivity> startTransferPage() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> invoke(activity, "showFeaturePage",
                new Class<?>[]{String.class}, FeatureActivity.WALLPAPER_UPLOAD));
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                if (request("GET", "/", null, null).status == 200) {
                    return scenario;
                }
            } catch (Exception ignored) {
            }
            sleep(100);
        }
        scenario.close();
        throw new AssertionError("传输服务未在端口 8080 启动");
    }

    private HttpResponse request(String method, String path, byte[] body, String fileName)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:8080" + path)
                .openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            if (fileName != null) {
                connection.setRequestProperty("X-File-Name", fileName);
            }
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = input == null ? "" : new String(readAll(input), StandardCharsets.UTF_8);
        connection.disconnect();
        return new HttpResponse(status, response);
    }

    private List<?> availableApps(MainActivity activity) {
        return (List<?>) invoke(activity, "loadAvailableApps");
    }

    private void saveChoice(MainActivity activity, int index, Object app) {
        invoke(activity, "saveChoice", new Class<?>[]{int.class, app.getClass()}, index, app);
    }

    private void showAllAppsMenu(MainActivity activity, Object app) {
        invoke(activity, "showAllAppsMenu", new Class<?>[]{app.getClass()}, app);
    }

    private Object findAppByPackage(List<?> apps, String packageName) {
        for (Object app : apps) {
            try {
                java.lang.reflect.Field resolveInfo = app.getClass().getDeclaredField("resolveInfo");
                resolveInfo.setAccessible(true);
                android.content.pm.ResolveInfo info =
                        (android.content.pm.ResolveInfo) resolveInfo.get(app);
                if (packageName.equals(info.activityInfo.packageName)) {
                    return app;
                }
            } catch (Exception exception) {
                throw new AssertionError("无法读取应用信息", exception);
            }
        }
        return null;
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            targetContext.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private Object invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (Exception exception) {
            throw new AssertionError("无法调用 " + name, exception);
        }
    }

    private Object invoke(Object target, String name) {
        return invoke(target, name, new Class<?>[0]);
    }

    private String preference(MainActivity activity, String key) {
        return activity.getSharedPreferences(CARD_PREFERENCES, Context.MODE_PRIVATE)
                .getString(key, null);
    }

    private boolean containsText(android.view.View view, String text) {
        if (view instanceof android.widget.TextView
                && text.contentEquals(((android.widget.TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof android.view.ViewGroup)) {
            return false;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (containsText(group.getChildAt(index), text)) {
                return true;
            }
        }
        return false;
    }

    private byte[] readFile(File file) {
        try (InputStream input = new java.io.FileInputStream(file)) {
            return readAll(input);
        } catch (Exception exception) {
            throw new AssertionError("无法读取传输文件", exception);
        }
    }

    private byte[] readAll(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = source.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void clearLauncherState() {
        targetContext.getSharedPreferences(CARD_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();
        targetContext.getSharedPreferences(WALLPAPER_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        targetContext.deleteFile("home_wallpaper");
        targetContext.deleteFile("home_wallpaper.tmp");
        File directory = new File(targetContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "PENG");
        File testFile = new File(directory, "launcher-test.txt");
        testFile.delete();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待被中断", exception);
        }
    }

    private static final class HttpResponse {
        private final int status;
        private final String body;

        private HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
