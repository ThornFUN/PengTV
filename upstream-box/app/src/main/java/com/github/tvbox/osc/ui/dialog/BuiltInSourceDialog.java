package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.BuiltInSourceAdapter;
import com.github.tvbox.osc.util.BuiltInSourceRepository;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import java.util.List;

public class BuiltInSourceDialog extends BaseDialog {
    public BuiltInSourceDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_api_history);
    }

    public void setSources(String title, List<BuiltInSourceRepository.Source> sources, String selectedUrl, BuiltInSourceAdapter.OnSourceClickListener listener) {
        ((TextView) findViewById(R.id.title)).setText(title);
        int selectedPosition = 0;
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).url.equals(selectedUrl)) {
                selectedPosition = i;
                break;
            }
        }
        TvRecyclerView list = findViewById(R.id.list);
        list.setAdapter(new BuiltInSourceAdapter(sources, selectedUrl, listener));
        list.setSelectedPosition(selectedPosition);
        int finalSelectedPosition = selectedPosition;
        list.post(() -> list.scrollToPosition(finalSelectedPosition));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
