package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;

import com.github.tvbox.osc.util.ScreenUtils;

/**
 *  用于光标总是文本最后
 */
public class CustomEditText extends AppCompatEditText {

    public CustomEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CustomEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomEditText(Context context) {
        super(context);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        // TV 遥控器输入始终追加；手机保留光标定位和文本选择。
        if (ScreenUtils.isTv(getContext()) && selStart == selEnd) {
            if(!TextUtils.isEmpty(getText())){
                setSelection(getText().length());
            }
        }
    }
}