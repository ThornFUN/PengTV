package com.peng.video;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public final class SourceImportActivity extends Activity {
    private static final int IMPORT_PORT = 8081;
    private static final int MAX_REQUEST_BODY_BYTES = 16384;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText m3uInput;
    private EditText epgInput;
    private volatile boolean serverRunning;
    private ServerSocket serverSocket;
    private boolean showingPhoneImport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSourceForm();
    }

    @Override
    protected void onStop() {
        stopServer();
        super.onStop();
    }

    private void showSourceForm() {
        showingPhoneImport = false;
        LinearLayout root = pageRoot();
        root.addView(title("直播源设置"));
        root.addView(description("默认提供公开频道。可在此追加您有权使用的 M3U 和 XMLTV 地址。"));

        root.addView(label("追加 M3U 播放列表地址（可选）"));
        m3uInput = input("https://example.com/playlist.m3u");
        m3uInput.setText(VideoPreferences.m3uUrl(this));
        root.addView(m3uInput, fieldParams());

        root.addView(label("XMLTV 节目单地址（可选）"));
        epgInput = input("https://example.com/guide.xml");
        epgInput.setText(VideoPreferences.epgUrl(this));
        root.addView(epgInput, fieldParams());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView save = action("保存并加载");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveFromTv();
            }
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(56), 1.0f));
        TextView phone = action("手机导入");
        phone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPhoneImport();
            }
        });
        LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        phoneParams.leftMargin = dp(16);
        actions.addView(phone, phoneParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(26);
        root.addView(actions, actionsParams);
        setContentView(root);
        m3uInput.requestFocus();
    }

    private void saveFromTv() {
        String m3u = m3uInput.getText().toString().trim();
        String epg = epgInput.getText().toString().trim();
        if (m3u.length() > 0 && !VideoPreferences.isHttpUrl(m3u)) {
            m3uInput.setError("请输入 http:// 或 https:// M3U 地址");
            m3uInput.requestFocus();
            return;
        }
        if (epg.length() > 0 && m3u.length() == 0) {
            epgInput.setError("请先填写需要追加的 M3U 地址");
            epgInput.requestFocus();
            return;
        }
        if (epg.length() > 0 && !VideoPreferences.isHttpUrl(epg)) {
            epgInput.setError("节目单地址必须是 http:// 或 https://");
            epgInput.requestFocus();
            return;
        }
        VideoPreferences.save(this, m3u, epg);
        Toast.makeText(this, "直播源已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showPhoneImport() {
        showingPhoneImport = true;
        LinearLayout root = pageRoot();
        root.addView(title("手机导入直播源"));
        TextView instruction = description("请在手机浏览器中打开以下局域网地址");
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(20);
        LinearLayout.LayoutParams instructionParams =
                (LinearLayout.LayoutParams) instruction.getLayoutParams();
        instructionParams.topMargin = dp(32);
        root.addView(instruction);
        TextView address = importAddress("正在启动导入服务...");
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addressParams.topMargin = dp(12);
        root.addView(address, addressParams);
        TextView security = description("只在本页面打开期间可访问。请仅在可信局域网中使用，离开此页将自动关闭。");
        LinearLayout.LayoutParams securityParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        securityParams.topMargin = dp(28);
        root.addView(security, securityParams);
        TextView back = action("返回源设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopServer();
                showSourceForm();
            }
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                dp(190), dp(56));
        backParams.topMargin = dp(30);
        root.addView(back, backParams);
        setContentView(root);
        back.requestFocus();
        startServer(address);
    }

    private void startServer(final TextView address) {
        stopServer();
        serverRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(IMPORT_PORT);
                    final String endpoint = "http://" + localIpAddress() + ":" + IMPORT_PORT;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (showingPhoneImport) {
                                address.setText(endpoint);
                            }
                        }
                    });
                    while (serverRunning) {
                        Socket socket = serverSocket.accept();
                        handleRequest(socket);
                    }
                } catch (final IOException exception) {
                    if (serverRunning) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (showingPhoneImport) {
                                    address.setText("无法启动导入服务：端口 8081 可能正被占用。");
                                    address.setTextSize(20);
                                }
                            }
                        });
                    }
                } finally {
                    closeServerSocket();
                }
            }
        }, "iptv-import-server").start();
    }

    private void stopServer() {
        serverRunning = false;
        closeServerSocket();
    }

    private synchronized void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
    }

    private void handleRequest(Socket socket) {
        try {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            String requestLine = readLine(input);
            if (requestLine == null) {
                return;
            }
            Map<String, String> headers = readHeaders(input);
            String body = "";
            boolean saved = false;
            String message = "";
            if (requestLine.startsWith("POST ")) {
                int length = contentLength(headers);
                if (length < 0 || length > MAX_REQUEST_BODY_BYTES) {
                    message = "提交内容过大或格式不正确。";
                } else {
                    body = readBody(input, length);
                    Map<String, String> values = formValues(body);
                    String m3u = value(values, "m3u");
                    String epg = value(values, "epg");
                    if (m3u.length() > 0 && !VideoPreferences.isHttpUrl(m3u)) {
                        message = "请输入有效的 http:// 或 https:// M3U 地址。";
                    } else if (epg.length() > 0 && m3u.length() == 0) {
                        message = "请先填写需要追加的 M3U 地址。";
                    } else if (epg.length() > 0 && !VideoPreferences.isHttpUrl(epg)) {
                        message = "XMLTV 地址必须是 http:// 或 https://。";
                    } else {
                        VideoPreferences.save(this, m3u, epg);
                        saved = true;
                        message = "直播源已保存。请返回电视首页，频道将自动刷新。";
                    }
                }
            }
            String response = page(saved, message);
            OutputStream output = socket.getOutputStream();
            byte[] bytes = response.getBytes("UTF-8");
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            output.write(bytes);
            output.flush();
        } catch (IOException ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String page(boolean saved, String message) {
        String currentM3u = html(VideoPreferences.m3uUrl(this));
        String currentEpg = html(VideoPreferences.epgUrl(this));
        String notice = message.length() == 0 ? "" : "<p class=\"" + (saved ? "success" : "error")
                + "\">" + html(message) + "</p>";
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>PENG电视 - 导入直播源</title><style>"
                + "body{margin:0;background:#101a2a;color:#eef5ff;font:16px -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}"
                + ".box{max-width:560px;margin:32px auto;padding:28px;background:#233958;border-radius:18px}"
                + "h1{margin-top:0}label{display:block;margin:20px 0 8px}input{box-sizing:border-box;width:100%;padding:14px;border:0;border-radius:10px;font-size:16px}"
                + "button{margin-top:24px;width:100%;padding:15px;border:0;border-radius:10px;background:#4a9ae8;color:white;font-size:17px;font-weight:bold}"
                + ".success{color:#b8f5c8}.error{color:#ffb9b9}.tip{color:#c2d5eb;font-size:14px;line-height:1.6}</style>"
                + "<main class=\"box\"><h1>PENG电视</h1><p class=\"tip\">电视已提供公开默认频道。可在此追加您有权使用的直播源；离开此页后，导入网页会自动关闭。</p>"
                + notice + "<form method=\"post\"><label>追加 M3U 播放列表地址（可选）</label>"
                + "<input name=\"m3u\" type=\"url\" value=\"" + currentM3u + "\" placeholder=\"https://example.com/playlist.m3u\">"
                + "<label>XMLTV 节目单地址（可选）</label><input name=\"epg\" type=\"url\" value=\"" + currentEpg
                + "\" placeholder=\"https://example.com/guide.xml\"><button type=\"submit\">保存到电视</button></form></main></html>";
    }

    private String readLine(BufferedInputStream input) throws IOException {
        StringBuilder result = new StringBuilder();
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') {
                return result.toString().replace("\r", "");
            }
            if (result.length() > 4096) {
                throw new IOException("header too long");
            }
            result.append((char) next);
        }
        return result.length() == 0 ? null : result.toString();
    }

    private Map<String, String> readHeaders(BufferedInputStream input) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(input)) != null && line.length() > 0) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                headers.put(line.substring(0, separator).trim().toLowerCase(),
                        line.substring(separator + 1).trim());
            }
        }
        return headers;
    }

    private int contentLength(Map<String, String> headers) {
        try {
            return Integer.parseInt(headers.get("content-length"));
        } catch (Exception exception) {
            return -1;
        }
    }

    private String readBody(BufferedInputStream input, int length) throws IOException {
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(body, offset, length - offset);
            if (count < 0) {
                throw new IOException("incomplete request");
            }
            offset += count;
        }
        return new String(body, "UTF-8");
    }

    private Map<String, String> formValues(String body) {
        if (body.length() == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            if (separator >= 0) {
                try {
                    values.put(URLDecoder.decode(pair.substring(0, separator), "UTF-8"),
                            URLDecoder.decode(pair.substring(separator + 1), "UTF-8"));
                } catch (Exception ignored) {
                    return Collections.emptyMap();
                }
            }
        }
        return values;
    }

    private String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }

    private String localIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address
                            && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "电视局域网 IP";
    }

    private String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(64), dp(40), dp(64), dp(40));
        root.setBackgroundColor(Color.rgb(16, 26, 42));
        return root;
    }

    private TextView title(String value) {
        TextView title = new TextView(this);
        title.setText(value);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        return title;
    }

    private TextView description(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.rgb(194, 213, 235));
        text.setTextSize(16);
        text.setLineSpacing(dp(4), 1.0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        text.setLayoutParams(params);
        return text;
    }

    private TextView label(String value) {
        TextView text = description(value);
        text.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) text.getLayoutParams();
        params.topMargin = dp(24);
        return text;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(150, 178, 210));
        input.setTextSize(17);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackground(focusBackground());
        return input;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        params.topMargin = dp(8);
        return params;
    }

    private TextView action(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(18);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(focusBackground());
        return view;
    }

    private TextView importAddress(String value) {
        TextView address = new TextView(this);
        address.setText(value);
        address.setTextColor(Color.WHITE);
        address.setTextSize(30);
        address.setTypeface(Typeface.DEFAULT_BOLD);
        address.setGravity(Gravity.CENTER);
        address.setPadding(dp(28), dp(24), dp(28), dp(24));
        address.setBackground(rounded(Color.rgb(42, 92, 151)));
        return address;
    }

    private StateListDrawable focusBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = rounded(Color.rgb(62, 119, 184));
        focused.setStroke(dp(2), Color.WHITE);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, rounded(Color.rgb(35, 57, 88)));
        return drawable;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
