package com.penglauncher.recovered;

import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
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

public class FeatureActivity extends BasePageActivity {
    public static final String SETTINGS = "settings";
    public static final String ABOUT = "about";
    public static final String WALLPAPER_UPLOAD = "wallpaper_upload";
    public static final String EXTRA_PAGE = "page";
    public static final String WALLPAPER_PREFERENCES = "home_wallpaper";
    public static final String WALLPAPER_PATH_KEY = "path";
    private static final int WALLPAPER_UPLOAD_PORT = 8080;
    private static final long MAX_WALLPAPER_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_FILE_BYTES = 1024L * 1024L * 1024L;
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
        if (getClass() != FeatureActivity.class) {
            return;
        }
        if (ABOUT.equals(getIntent().getStringExtra(EXTRA_PAGE))) {
            about();
        } else if (WALLPAPER_UPLOAD.equals(getIntent().getStringExtra(EXTRA_PAGE))) {
            showWallpaperUpload();
        } else {
            settings();
        }
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

    protected void openFeaturePage(String page) {
        FeatureActivity.open(this, page);
    }

    protected void closeFeaturePage() {
        finish();
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
