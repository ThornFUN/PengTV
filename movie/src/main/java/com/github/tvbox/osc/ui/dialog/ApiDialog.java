package com.github.tvbox.osc.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
public class ApiDialog extends BaseDialog {
    private ImageView ivQRCode;
    private TextView tvAddress;
    private EditText inputApi;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            inputApi.setText((String) event.obj);
        }
    }

    public ApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_api);
        setCanceledOnTouchOutside(false);
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        inputApi = findViewById(R.id.input);
        inputApi.setText(Hawk.get(HawkConfig.API_URL, ""));
        findViewById(R.id.builtInConfigs).setOnClickListener(v -> showBuiltInConfigs());
        findViewById(R.id.storagePermission).setOnClickListener(v -> {
            if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(getContext())
                        .setTitle("需要存储权限")
                        .setMessage("用于读取本地配置文件，以及通过手机浏览器上传配置文件。不会访问或上传其他文件。")
                        .setNegativeButton("暂不授权", null)
                        .setPositiveButton("去授权", (dialog, which) -> XXPermissions.with(getContext())
                                .permission(Permission.Group.STORAGE)
                                .request((permissions, all) -> {
                                }))
                        .show();
            }
        });
        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newApi = inputApi.getText().toString().trim();
                if (!newApi.isEmpty() && (newApi.startsWith("http") || newApi.startsWith("clan"))) {
                    ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                    if (!history.contains(newApi))
                        history.add(0, newApi);
                    if (history.size() > 10)
                        history.remove(10);
                    Hawk.put(HawkConfig.API_HISTORY, history);
                    listener.onchange(newApi);
                    dismiss();
                }
            }
        });
        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                if (history.isEmpty()) {
                    Toast.makeText(getContext(), "暂无配置历史，请先保存一个配置地址", Toast.LENGTH_LONG).show();
                    return;
                }
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                if (history.contains(current))
                    idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip("历史配置列表");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        inputApi.setText(value);
                        listener.onchange(value);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.API_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });
        refreshQRCode();
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("外部导入：手机/电脑扫描二维码或浏览器访问\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300)));
    }

    private void showBuiltInConfigs() {
        List<BuiltInConfig> configs;
        try {
            configs = loadBuiltInConfigs();
        } catch (Exception e) {
            Toast.makeText(getContext(), "读取内置配置地址失败", Toast.LENGTH_LONG).show();
            return;
        }
        SelectDialog<BuiltInConfig> dialog = new SelectDialog<>(getContext());
        dialog.setTip("选择内置测试地址");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<BuiltInConfig>() {
            @Override
            public void click(BuiltInConfig value, int pos) {
                inputApi.setText(value.url);
                listener.onchange(value.url);
                dialog.dismiss();
                dismiss();
            }

            @Override
            public String getDisplay(BuiltInConfig value) {
                return value.name + "\n" + value.url;
            }
        }, new androidx.recyclerview.widget.DiffUtil.ItemCallback<BuiltInConfig>() {
            @Override
            public boolean areItemsTheSame(@NonNull BuiltInConfig oldItem, @NonNull BuiltInConfig newItem) {
                return oldItem.url.equals(newItem.url);
            }

            @Override
            public boolean areContentsTheSame(@NonNull BuiltInConfig oldItem, @NonNull BuiltInConfig newItem) {
                return oldItem.name.equals(newItem.name) && oldItem.url.equals(newItem.url);
            }
        }, configs, getBuiltInConfigPosition(configs));
        dialog.show();
    }

    private int getBuiltInConfigPosition(List<BuiltInConfig> configs) {
        String currentUrl = Hawk.get(HawkConfig.API_URL, "");
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).url.equals(currentUrl)) {
                return i;
            }
        }
        return 0;
    }

    private List<BuiltInConfig> loadBuiltInConfigs() throws Exception {
        InputStream input = getContext().getAssets().open("default-configs.json");
        byte[] bytes = new byte[input.available()];
        int read = input.read(bytes);
        input.close();
        if (read <= 0) throw new Exception("empty config list");
        JSONArray values = new JSONArray(new String(bytes, "UTF-8"));
        List<BuiltInConfig> configs = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            configs.add(new BuiltInConfig(item.getString("name"), item.getString("url")));
        }
        return configs;
    }

    private static class BuiltInConfig {
        final String name;
        final String url;

        BuiltInConfig(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    OnListener listener = null;

    public interface OnListener {
        void onchange(String api);
    }
}
