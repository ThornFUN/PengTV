package com.penglauncher.recovered;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract class FeatureActivity extends Activity {
    public static final String SETTINGS = "settings";
    public static final String ABOUT = "about";
    public static final String WALLPAPER_UPLOAD = "wallpaper_upload";
    public static final String FILE_MANAGER = "file_manager";
    public static final String WALLPAPER_PREFERENCES = "home_wallpaper";
    public static final String WALLPAPER_PATH_KEY = "path";
    private static final int WALLPAPER_UPLOAD_PORT = 8080;
    private static final long MAX_WALLPAPER_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_FILE_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_REQUEST_HEADER_BYTES = 8192;
    private static final int REQUEST_STORAGE_ACCESS = 1;
    private WallpaperUploadServer uploadServer;
    private LinearLayout content;
    private View firstAction;
    private List<File> storageRoots;
    private File currentDirectory;
    private File activeStorageRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.rgb(16, 25, 42));
        }
    }

    protected void showPage(String title, String subtitle) {
        firstAction = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(20, 30, 49));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(title, 28, Color.WHITE));
        root.addView(header);

        if (subtitle != null) {
            TextView detail = text(subtitle, 15, Color.rgb(185, 203, 225));
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(8);
            root.addView(detail, detailParams);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(18), 0, 0);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);
    }

    protected void addAction(String title, String detail, View.OnClickListener listener) {
        int icon = 0;
        if (title.startsWith("📁 ")) {
            icon = R.drawable.ic_file_manager_folder;
            title = title.substring("📁 ".length());
        } else if (title.startsWith("📄 ")) {
            icon = R.drawable.ic_file_manager_file;
            title = title.substring("📄 ".length());
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(18), dp(10));
        setBackgroundCompat(row, interactiveBackground());
        applyTvFocusFeedback(row);
        row.setOnClickListener(listener);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textBlock, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView name = text(title, 17, Color.WHITE);
        if (icon != 0) {
            name.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            name.setCompoundDrawablePadding(dp(10));
        }
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(name);

        TextView description = text(detail == null ? "" : detail, 14, Color.rgb(174, 188, 208));
        description.setSingleLine(true);
        description.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(3);
        textBlock.addView(description, descriptionParams);

        TextView arrow = text("›", 34, Color.rgb(205, 220, 241));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(52)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        content.addView(row, params);
        if (firstAction == null) {
            firstAction = row;
            row.post(new Runnable() {
                @Override
                public void run() {
                    if (firstAction == row) {
                        row.requestFocus();
                    }
                }
            });
        }
    }

    protected void addInfo(String value) {
        TextView view = text(value, 16, Color.rgb(217, 228, 243));
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(view);
    }

    protected void addHighlightInfo(String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(20), dp(28), dp(20));
        setBackgroundCompat(card, roundedRect(Color.rgb(42, 92, 151), dp(14)));

        card.addView(text(label, 18, Color.WHITE));
        TextView detail = text(value, 30, Color.WHITE);
        detail.setTypeface(Typeface.DEFAULT_BOLD);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(10);
        card.addView(detail, detailParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(14);
        content.addView(card, cardParams);
    }

    protected TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setLineSpacing(0.0f, 1.2f);
        return view;
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected StateListDrawable interactiveBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = roundedRect(Color.rgb(73, 126, 193), dp(12));
        focused.setStroke(dp(2), Color.WHITE);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{android.R.attr.state_selected}, focused);
        drawable.addState(new int[]{android.R.attr.state_pressed},
                roundedRect(Color.rgb(56, 101, 162), dp(12)));
        drawable.addState(new int[]{}, roundedRect(Color.rgb(40, 57, 86), dp(12)));
        return drawable;
    }

    protected void applyTvFocusFeedback(View view) {
        view.setFocusable(true);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View focusedView, boolean focused) {
                focusedView.animate().cancel();
                focusedView.animate()
                        .scaleX(focused ? 1.02f : 1.0f)
                        .scaleY(focused ? 1.02f : 1.0f)
                        .setDuration(140)
                        .start();
            }
        });
    }

    protected void setBackgroundCompat(View view, android.graphics.drawable.Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(drawable);
        } else {
            view.setBackgroundDrawable(drawable);
        }
    }

    protected void applyDialogFocusStyle(final AlertDialog dialog) {
        dialog.setOnShowListener(dialogInterface -> {
            ListView list = dialog.getListView();
            if (list != null) {
                list.setSelector(interactiveBackground());
                if (list.getAdapter() != null && list.getAdapter().getCount() > 0) {
                    list.setSelection(0);
                    list.requestFocus();
                }
            }
        });
    }

    private GradientDrawable roundedRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    protected void settings() {
        showPage("设置", null);
        addAction("Wi-Fi", "打开系统 Wi-Fi 设置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        addAction("手机传输", "通过局域网传递壁纸或文件", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFeaturePage(WALLPAPER_UPLOAD);
            }
        });
        addAction("文件管理", "浏览设备存储空间中的文件", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFeaturePage(FILE_MANAGER);
            }
        });
        addAction("重置首页壁纸", "恢复内置默认背景图", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmWallpaperReset();
            }
        });
        addAction("关于 PENG 桌面", "应用背景、目的与作者", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFeaturePage(ABOUT);
            }
        });
    }

    protected void about() {
        showPage("关于 PENG 桌面", null);
        addInfo("应用背景\nPENG 桌面是一款为 Android TV 设备打造的轻量级自定义桌面。");
        addInfo("应用目的\n提供简洁、可自定义且适合遥控器操作的应用入口。");
        addInfo("Android 兼容性\n支持 Android 4.0（API 14）及以上；当前目标版本为 Android 15（API 35）。");
        addInfo("作者\nPENG");
    }

    protected void showFileManager() {
        if (requiresStoragePermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_ACCESS);
            return;
        }
        storageRoots = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        addStorageRoot(Environment.getExternalStorageDirectory(), paths);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            for (File appDirectory : getExternalFilesDirs(null)) {
                File root = appDirectory;
                for (int level = 0; level < 4 && root != null; level++) root = root.getParentFile();
                addStorageRoot(root, paths);
            }
        }
        showStorageRoots();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_ACCESS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showFileManager();
            } else {
                storageRoots = new ArrayList<>();
                storageRoots.add(transferDirectory());
                showStorageRoots();
            }
        }
    }

    private boolean requiresStoragePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void addStorageRoot(File root, Set<String> paths) {
        if (root == null || !root.isDirectory()) return;
        try {
            if (paths.add(root.getCanonicalPath())) storageRoots.add(root);
        } catch (IOException ignored) {
        }
    }

    private File transferDirectory() {
        File downloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloads == null ? getFilesDir() : downloads, "PENG");
    }

    private void showStorageRoots() {
        showPage("文件管理", "选择存储空间");
        for (final File root : storageRoots) {
            addAction("存储空间", root.getAbsolutePath(), v -> {
                activeStorageRoot = root;
                showDirectory(root);
            });
        }
    }

    private void showDirectory(File directory) {
        currentDirectory = directory;
        showPage("文件管理", directory.getAbsolutePath());
        addAction(directory.equals(activeStorageRoot) ? "返回存储空间" : "返回上一级",
                "", v -> {
                    if (directory.equals(activeStorageRoot)) showStorageRoots();
                    else showDirectory(directory.getParentFile());
                });
        File[] children = directory.listFiles();
        if (children == null) { addInfo("无法读取此目录"); return; }
        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        for (final File child : children) addAction(
                (child.isDirectory() ? "📁 " : "📄 ") + child.getName(),
                child.isDirectory() ? "目录" : child.length() / 1024 + " KB",
                v -> { if (child.isDirectory()) showDirectory(child); else openFile(child); });
    }

    private void openFile(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ignored) {
            Toast.makeText(this, "未找到可打开此文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    protected void showWallpaperUpload() {
        try {
            uploadServer = new WallpaperUploadServer();
            uploadServer.start();
        } catch (BindException exception) {
            Toast.makeText(this, "端口 8080 已被占用，无法启动上传服务", Toast.LENGTH_SHORT).show();
            closeFeaturePage();
            return;
        } catch (IOException exception) {
            Toast.makeText(this, "无法启动上传服务", Toast.LENGTH_SHORT).show();
            closeFeaturePage();
            return;
        }
        showPage("手机传输", "离开此页面后，传输服务将自动关闭。");
        addHighlightInfo("请在手机浏览器中打开以下局域网地址", uploadAddresses());
        addInfo("可上传裁剪后的首页壁纸，或传输普通文件到电视。此服务不设密码，请仅在可信局域网内短暂使用。");
        addAction("关闭传输服务", "停止服务并返回桌面", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeFeaturePage();
            }
        });
    }

    private void confirmWallpaperReset() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重置首页壁纸")
                .setMessage("将删除当前自定义壁纸并恢复默认背景图。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", (ignoredDialog, which) -> resetWallpaper())
                .create();
        applyDialogFocusStyle(dialog);
        dialog.show();
    }

    private void resetWallpaper() {
        File wallpaper = new File(getFilesDir(), "home_wallpaper");
        File temporary = new File(getFilesDir(), "home_wallpaper.tmp");
        if ((wallpaper.exists() && !wallpaper.delete())
                || (temporary.exists() && !temporary.delete())) {
            Toast.makeText(this, "重置壁纸失败", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(WALLPAPER_PREFERENCES, MODE_PRIVATE)
                .edit()
                .remove(WALLPAPER_PATH_KEY)
                .apply();
        Toast.makeText(this, "已恢复默认壁纸", Toast.LENGTH_SHORT).show();
    }

    private void saveWallpaper(InputStream source) throws IOException {
        File target = new File(getFilesDir(), "home_wallpaper");
        File temporary = new File(getFilesDir(), "home_wallpaper.tmp");
        try (FileOutputStream destination = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long bytesCopied = 0;
            while ((bytesRead = source.read(buffer)) != -1) {
                bytesCopied += bytesRead;
                if (bytesCopied > MAX_WALLPAPER_BYTES) {
                    throw new IOException("wallpaper is too large");
                }
                destination.write(buffer, 0, bytesRead);
            }
        } catch (IOException exception) {
            temporary.delete();
            throw exception;
        }
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            temporary.delete();
            throw new IOException("invalid wallpaper image");
        }
        try {
            if (target.exists() && !target.delete()) {
                throw new IOException("cannot replace previous wallpaper");
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("cannot save wallpaper");
            }
            getSharedPreferences(WALLPAPER_PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(WALLPAPER_PATH_KEY, target.getAbsolutePath())
                    .apply();
        } catch (IOException exception) {
            temporary.delete();
            throw exception;
        }
    }

    @Override
    protected void onStop() {
        stopUploadServer();
        super.onStop();
    }

    protected void stopUploadServer() {
        if (uploadServer != null) {
            uploadServer.stop();
            uploadServer = null;
        }
    }

    protected abstract void openFeaturePage(String page);

    protected abstract void closeFeaturePage();

    private String uploadAddresses() {
        StringBuilder addresses = new StringBuilder();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addressesForInterface =
                        networkInterface.getInetAddresses();
                while (addressesForInterface.hasMoreElements()) {
                    InetAddress address = addressesForInterface.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    if (addresses.length() > 0) {
                        addresses.append('\n');
                    }
                    addresses.append("http://")
                            .append(address.getHostAddress())
                            .append(':')
                            .append(uploadServer.port());
                }
            }
        } catch (SocketException exception) {
            return "未能读取电视的局域网地址";
        }
        return addresses.length() == 0 ? "未检测到局域网地址" : addresses.toString();
    }

    private final class WallpaperUploadServer {
        private volatile boolean running;
        private ServerSocket serverSocket;
        private Thread worker;

        private void start() throws IOException {
            serverSocket = new ServerSocket(WALLPAPER_UPLOAD_PORT);
            running = true;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    acceptConnections();
                }
            }, "wallpaper-upload");
            worker.start();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void stop() {
            running = false;
            if (serverSocket == null) {
                return;
            }
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        private void acceptConnections() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    handleConnection(socket);
                } catch (IOException exception) {
                    if (running) {
                        android.util.Log.w("PENG", "Wallpaper upload server stopped", exception);
                    }
                    return;
                }
            }
        }

        private void handleConnection(Socket socket) {
            try {
                socket.setSoTimeout(20000);
                BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                OutputStream output = socket.getOutputStream();
                String requestLine = readLine(input);
                if (requestLine == null) {
                    return;
                }
                long contentLength = -1L;
                String encodedFileName = null;
                String line;
                while ((line = readLine(input)) != null && line.length() > 0) {
                    int separator = line.indexOf(':');
                    if (separator > 0) {
                        String headerName = line.substring(0, separator).trim();
                        String headerValue = line.substring(separator + 1).trim();
                        if ("content-length".equalsIgnoreCase(headerName)) {
                            contentLength = Long.parseLong(headerValue);
                        } else if ("x-file-name".equalsIgnoreCase(headerName)) {
                            encodedFileName = headerValue;
                        }
                    }
                }
                if (requestLine.startsWith("GET / ")) {
                    writeResponse(output, 200, "text/html; charset=utf-8", uploadPage());
                } else if (requestLine.startsWith("POST /wallpaper ")) {
                    handleWallpaperUpload(input, output, contentLength);
                } else if (requestLine.startsWith("POST /file ")) {
                    handleFileUpload(input, output, contentLength, encodedFileName);
                } else {
                    writeResponse(output, 404, "text/plain; charset=utf-8", "Not found");
                }
            } catch (IOException | NumberFormatException exception) {
                android.util.Log.w("PENG", "Wallpaper upload failed", exception);
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handleWallpaperUpload(InputStream input, OutputStream output, long contentLength)
                throws IOException {
            if (contentLength <= 0 || contentLength > MAX_WALLPAPER_BYTES) {
                writeResponse(output, 413, "text/plain; charset=utf-8", "Image must be below 8MB");
                return;
            }
            try {
                saveWallpaper(new LimitedInputStream(input, contentLength));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(FeatureActivity.this, "首页壁纸已更新", Toast.LENGTH_SHORT).show();
                    }
                });
                writeResponse(output, 200, "text/plain; charset=utf-8",
                        "壁纸已更新，返回电视首页即可查看效果。");
            } catch (IOException exception) {
                writeResponse(output, 400, "text/plain; charset=utf-8", "无法保存图片，请重试。");
            }
        }

        private void handleFileUpload(InputStream input, OutputStream output, long contentLength,
                String encodedFileName) throws IOException {
            if (contentLength <= 0 || contentLength > MAX_FILE_BYTES) {
                writeResponse(output, 413, "text/plain; charset=utf-8", "File must be below 1GB");
                return;
            }
            try {
                File savedFile = saveReceivedFile(
                        new LimitedInputStream(input, contentLength), encodedFileName);
                writeResponse(output, 200, "text/plain; charset=utf-8",
                        "文件已保存到电视：" + savedFile.getAbsolutePath());
            } catch (IOException | IllegalArgumentException exception) {
                writeResponse(output, 400, "text/plain; charset=utf-8", "文件保存失败，请重试。");
            }
        }

        private File saveReceivedFile(InputStream source, String encodedFileName) throws IOException {
            String fileName = sanitizeFileName(encodedFileName);
            File downloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File directory = new File(downloads == null ? getFilesDir() : downloads, "PENG");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("cannot create transfer directory");
            }
            File target = uniqueFile(directory, fileName);
            File temporary = new File(directory, "." + target.getName() + ".part");
            boolean saved = false;
            try (FileOutputStream destination = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = source.read(buffer)) != -1) {
                    destination.write(buffer, 0, bytesRead);
                }
                saved = true;
            } finally {
                if (!saved) {
                    temporary.delete();
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.delete();
                throw new IOException("cannot save transfer");
            }
            return target;
        }

        private String sanitizeFileName(String encodedFileName) throws IOException {
            if (encodedFileName == null) {
                throw new IllegalArgumentException("missing file name");
            }
            String name = URLDecoder.decode(encodedFileName, "UTF-8")
                    .replace('\\', '/');
            name = new File(name).getName();
            if (name.length() == 0 || name.length() > 128 || ".".equals(name)
                    || "..".equals(name)) {
                throw new IllegalArgumentException("invalid file name");
            }
            return name;
        }

        private File uniqueFile(File directory, String fileName) {
            File candidate = new File(directory, fileName);
            if (!candidate.exists()) {
                return candidate;
            }
            int extension = fileName.lastIndexOf('.');
            String base = extension > 0 ? fileName.substring(0, extension) : fileName;
            String suffix = extension > 0 ? fileName.substring(extension) : "";
            for (int index = 1; ; index++) {
                candidate = new File(directory, base + " (" + index + ")" + suffix);
                if (!candidate.exists()) {
                    return candidate;
                }
            }
        }

        private String readLine(InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int next;
            while ((next = input.read()) != -1) {
                if (next == '\n') {
                    break;
                }
                if (next != '\r') {
                    line.write(next);
                }
                if (line.size() > MAX_REQUEST_HEADER_BYTES) {
                    throw new IOException("request header too large");
                }
            }
            return next == -1 && line.size() == 0 ? null
                    : new String(line.toByteArray(), Charset.forName("US-ASCII"));
        }

        private void writeResponse(OutputStream output, int status, String contentType, String body)
                throws IOException {
            byte[] bodyBytes = body.getBytes("UTF-8");
            String response = "HTTP/1.1 " + status + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(response.getBytes("US-ASCII"));
            output.write(bodyBytes);
            output.flush();
        }

        private String uploadPage() {
            return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<title>PENG 手机传输</title><style>"
                    + "*{box-sizing:border-box}body{margin:0;min-height:100vh;display:flex;"
                    + "align-items:center;justify-content:center;padding:24px;font-family:-apple-system,"
                    + "BlinkMacSystemFont,'Segoe UI',sans-serif;color:#edf4ff;background:linear-gradient("
                    + "135deg,#0f1d32,#1b3d66)}.card{width:min(100%,480px);padding:32px 28px;"
                    + "border:1px solid rgba(173,214,255,.28);border-radius:24px;background:rgba(17,35,59,"
                    + ".94);box-shadow:0 20px 50px rgba(0,0,0,.3)}.badge{display:inline-block;padding:6px "
                    + "10px;border-radius:999px;color:#b9dbff;background:#274c75;font-size:13px}.title{margin:"
                    + "16px 0 8px;font-size:28px}.hint{margin:0 0 24px;color:#b8cbe3;line-height:1.6}.picker{"
                    + "display:block;padding:18px;border:1px dashed #6faee8;border-radius:16px;text-align:center;"
                    + "color:#dbeeff;background:#1a3353;cursor:pointer}.picker strong{display:block;font-size:16px}"
                    + ".picker span{display:block;margin-top:6px;color:#9fc3e8;font-size:13px}input{display:none}"
                    + "button{width:100%;margin-top:16px;padding:15px;border:0;border-radius:14px;color:#fff;"
                    + "font-size:16px;font-weight:600;background:#347fc4;cursor:pointer}button:disabled{opacity:.45;"
                    + "cursor:not-allowed}.status{min-height:22px;margin:16px 0 0;text-align:center;color:#b8cbe3;"
                    + "font-size:14px}.success{color:#79e4ad}.error{color:#ff9e9e}.editor{margin-top:20px}"
                    + ".editor[hidden]{display:none}.crop{display:block;width:100%;aspect-ratio:16/9;border-radius:"
                    + "14px;background:#091522;touch-action:none}.controls{margin-top:14px}.controls label{display:block;"
                    + "margin-bottom:8px;color:#b8cbe3;font-size:14px}.controls input{display:block;width:100%;accent-color:"
                    + "#5ba8ed}.tip{margin:10px 0 0;color:#9fc3e8;font-size:13px;line-height:1.5}.transfer{margin-top:"
                    + "28px;padding-top:22px;border-top:1px solid rgba(173,214,255,.28)}.transfer h2{margin:0 0 8px;"
                    + "font-size:20px}.transfer p{margin:0 0 14px;color:#b8cbe3;line-height:1.5}</style></head><body>"
                    + "<main class=\"card\"><span class=\"badge\">PENG 桌面</span><h1 class=\"title\">手机传输</h1>"
                    + "<h2>上传首页壁纸</h2>"
                    + "<p class=\"hint\">选择图片后可拖动和缩放，以适配电视的 16:9 屏幕比例。</p>"
                    + "<label class=\"picker\" for=\"image\"><strong id=\"fileTitle\">选择图片</strong>"
                    + "<span id=\"fileHint\">支持手机相册中的常见图片格式</span></label>"
                    + "<input id=\"image\" type=\"file\" accept=\"image/*\" onchange=\"selectImage()\">"
                    + "<section id=\"editor\" class=\"editor\" hidden><canvas id=\"crop\" class=\"crop\" width=\"960\" "
                    + "height=\"540\"></canvas><div class=\"controls\"><label for=\"zoom\">缩放画面</label>"
                    + "<input id=\"zoom\" type=\"range\" min=\"1\" max=\"3\" step=\"0.01\" value=\"1\" "
                    + "oninput=\"changeZoom()\"></div><p class=\"tip\">拖动图片调整显示区域，上传后会生成 1920×1080 的壁纸。</p>"
                    + "</section><button id=\"submit\" onclick=\"upload()\" disabled>裁剪并上传壁纸</button>"
                    + "<p id=\"status\" class=\"status\"></p><section class=\"transfer\"><h2>传输普通文件</h2>"
                    + "<p>文件会保存到电视的 PENG 下载目录，单个文件最大 1GB。</p><label class=\"picker\" for=\"transferFile\">"
                    + "<strong id=\"transferTitle\">选择文件</strong><span id=\"transferHint\">支持任意文件类型</span></label>"
                    + "<input id=\"transferFile\" type=\"file\" onchange=\"chooseFile()\"><button id=\"transferSubmit\" "
                    + "onclick=\"transferFile()\" disabled>传输文件到电视</button><p id=\"transferStatus\" class=\"status\"></p>"
                    + "</section></main><script>"
                    + "var image,view,drag;function selectImage(){var f=document.getElementById('image').files[0],b="
                    + "document.getElementById('submit');if(!f){return;}document.getElementById('fileTitle').textContent=f.name;"
                    + "if(f.size>8*1024*1024){document.getElementById('fileHint').textContent='图片超过 8MB，请重新选择';"
                    + "document.getElementById('editor').hidden=true;b.disabled=true;return;}image=new Image();"
                    + "image.onload=function(){initCrop();document.getElementById('editor').hidden=false;b.disabled=false;};"
                    + "image.src=URL.createObjectURL(f);document.getElementById('fileHint').textContent='请拖动和缩放后上传';"
                    + "document.getElementById('status').textContent='';}"
                    + "function initCrop(){var c=document.getElementById('crop'),base=Math.max(c.width/image.width,c.height/image.height);"
                    + "view={scale:base,x:(c.width-image.width*base)/2,y:(c.height-image.height*base)/2,base:base};"
                    + "document.getElementById('zoom').value=1;draw();}function changeZoom(){view.scale=view.base*"
                    + "parseFloat(document.getElementById('zoom').value);limit();draw();}function limit(){var c=document.getElementById('crop'),"
                    + "w=image.width*view.scale,h=image.height*view.scale;view.x=Math.min(0,Math.max(c.width-w,view.x));"
                    + "view.y=Math.min(0,Math.max(c.height-h,view.y));}function draw(){var c=document.getElementById('crop'),g=c.getContext('2d');"
                    + "g.clearRect(0,0,c.width,c.height);g.drawImage(image,view.x,view.y,image.width*view.scale,image.height*view.scale);}"
                    + "function point(t){var c=document.getElementById('crop'),r=c.getBoundingClientRect();return{x:(t.clientX-r.left)*c.width/r.width,"
                    + "y:(t.clientY-r.top)*c.height/r.height};}function begin(t){var p=point(t);drag={x:p.x,y:p.y,left:view.x,top:view.y};}"
                    + "function move(t){if(!drag){return;}var p=point(t);view.x=drag.left+p.x-drag.x;view.y=drag.top+p.y-drag.y;limit();draw();}"
                    + "var crop=document.getElementById('crop');crop.addEventListener('touchstart',function(e){begin(e.touches[0]);e.preventDefault();},false);"
                    + "crop.addEventListener('touchmove',function(e){move(e.touches[0]);e.preventDefault();},false);"
                    + "crop.addEventListener('touchend',function(){drag=null;},false);crop.addEventListener('mousedown',function(e){begin(e);},false);"
                    + "window.addEventListener('mousemove',function(e){move(e);},false);window.addEventListener('mouseup',function(){drag=null;},false);"
                    + "function upload(){var b=document.getElementById('submit'),s=document.getElementById('status'),c=document.getElementById('crop');"
                    + "if(!image||!c.toBlob){s.textContent='当前浏览器不支持图片裁剪';s.className='status error';return;}b.disabled=true;"
                    + "s.className='status';s.textContent='正在裁剪图片...';var out=document.createElement('canvas');out.width=1920;out.height=1080;"
                    + "var g=out.getContext('2d'),sx=-view.x/view.scale,sy=-view.y/view.scale,sw=c.width/view.scale,sh=c.height/view.scale;"
                    + "g.drawImage(image,sx,sy,sw,sh,0,0,out.width,out.height);out.toBlob(function(blob){if(!blob||blob.size>8*1024*1024){"
                    + "s.textContent='裁剪后的图片超过 8MB，请降低图片细节后重试';s.className='status error';b.disabled=false;return;}"
                    + "s.textContent='正在上传，请稍候...';var r=new XMLHttpRequest();r.open('POST','/wallpaper');r.onload=function(){s.textContent="
                    + "r.responseText;s.className='status '+(r.status===200?'success':'error');b.disabled=false;};r.onerror=function(){"
                    + "s.textContent='上传失败，请确认电视和手机仍在同一局域网';s.className='status error';b.disabled=false;};r.send(blob);},'image/jpeg',.92);}"
                    + "function chooseFile(){var f=document.getElementById('transferFile').files[0],b=document.getElementById('transferSubmit');"
                    + "if(!f){return;}document.getElementById('transferTitle').textContent=f.name;document.getElementById('transferHint').textContent="
                    + "(f.size/1024/1024).toFixed(2)+' MB';b.disabled=f.size>1024*1024*1024;}"
                    + "function transferFile(){var f=document.getElementById('transferFile').files[0],b=document.getElementById('transferSubmit'),"
                    + "s=document.getElementById('transferStatus');if(!f){return;}b.disabled=true;s.className='status';s.textContent='正在传输，请稍候...';"
                    + "var r=new XMLHttpRequest();r.open('POST','/file');r.setRequestHeader('X-File-Name',encodeURIComponent(f.name));r.onload=function(){"
                    + "s.textContent=r.responseText;s.className='status '+(r.status===200?'success':'error');b.disabled=false;};r.onerror=function(){"
                    + "s.textContent='传输失败，请确认电视和手机仍在同一局域网';s.className='status error';b.disabled=false;};r.send(f);}"
                    + "</script></body></html>";
        }
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream source;
        private long remaining;

        private LimitedInputStream(InputStream source, long remaining) {
            this.source = source;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int value = source.read();
            if (value == -1) {
                throw new IOException("incomplete upload");
            }
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int bytesRead = source.read(buffer, offset, (int) Math.min(length, remaining));
            if (bytesRead == -1) {
                throw new IOException("incomplete upload");
            }
            remaining -= bytesRead;
            return bytesRead;
        }
    }
}
