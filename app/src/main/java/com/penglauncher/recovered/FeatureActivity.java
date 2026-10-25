package com.penglauncher.recovered;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.Enumeration;

public final class FeatureActivity extends BasePageActivity {
    public static final String SETTINGS = "settings";
    public static final String ABOUT = "about";
    public static final String WALLPAPER_UPLOAD = "wallpaper_upload";
    public static final String EXTRA_PAGE = "page";
    public static final String WALLPAPER_PREFERENCES = "home_wallpaper";
    public static final String WALLPAPER_PATH_KEY = "path";
    private static final int SELECT_WALLPAPER_REQUEST = 100;
    private static final long MAX_WALLPAPER_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_REQUEST_HEADER_BYTES = 8192;
    private WallpaperUploadServer uploadServer;

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
        } else if (WALLPAPER_UPLOAD.equals(getIntent().getStringExtra(EXTRA_PAGE))) {
            showWallpaperUpload();
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
        addAction("手机上传壁纸", "临时开放局域网上传页面", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FeatureActivity.open(FeatureActivity.this, WALLPAPER_UPLOAD);
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

    private void showWallpaperUpload() {
        try {
            uploadServer = new WallpaperUploadServer();
            uploadServer.start();
        } catch (IOException exception) {
            Toast.makeText(this, "无法启动上传服务", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        showPage("手机上传壁纸", "离开此页面后，上传服务将自动关闭。");
        addInfo("手机连接同一局域网后，用浏览器访问：\n" + uploadAddresses());
        addInfo("此服务不设密码，请仅在可信局域网内短暂使用。图片最大 8MB。");
        addAction("关闭上传服务", "停止服务并返回桌面", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
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
        try (InputStream source = getContentResolver().openInputStream(sourceUri)) {
            if (source == null) {
                Toast.makeText(this, "无法读取所选图片", Toast.LENGTH_SHORT).show();
                return;
            }
            saveWallpaper(source);
            Toast.makeText(this, "首页壁纸已更新", Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException exception) {
            Toast.makeText(this, "保存壁纸失败", Toast.LENGTH_SHORT).show();
        }
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

    private void stopUploadServer() {
        if (uploadServer != null) {
            uploadServer.stop();
            uploadServer = null;
        }
    }

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
            serverSocket = new ServerSocket(0);
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
                String line;
                while ((line = readLine(input)) != null && line.length() > 0) {
                    int separator = line.indexOf(':');
                    if (separator > 0 && "content-length".equalsIgnoreCase(
                            line.substring(0, separator).trim())) {
                        contentLength = Long.parseLong(line.substring(separator + 1).trim());
                    }
                }
                if (requestLine.startsWith("GET / ")) {
                    writeResponse(output, 200, "text/html; charset=utf-8", uploadPage());
                } else if (requestLine.startsWith("POST /upload ")) {
                    handleUpload(input, output, contentLength);
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

        private void handleUpload(InputStream input, OutputStream output, long contentLength)
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
                        "Wallpaper updated. Return to the TV home screen to view it.");
            } catch (IOException exception) {
                writeResponse(output, 400, "text/plain; charset=utf-8", "Unable to save image");
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
            return "<!doctype html><html><head><meta name=\"viewport\" "
                    + "content=\"width=device-width,initial-scale=1\"><title>PENG Wallpaper</title>"
                    + "</head><body><h2>Upload wallpaper</h2><input id=\"image\" type=\"file\" "
                    + "accept=\"image/*\"><button onclick=\"upload()\">Upload</button><p id=\"status\"></p>"
                    + "<script>function upload(){var f=document.getElementById('image').files[0];"
                    + "if(!f){return;}document.getElementById('status').textContent='Uploading...';"
                    + "var r=new XMLHttpRequest();r.open('POST','/upload');"
                    + "r.onload=function(){document.getElementById('status').textContent=r.responseText;};"
                    + "r.onerror=function(){document.getElementById('status').textContent='Upload failed';};"
                    + "r.send(f);}</script></body></html>";
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
