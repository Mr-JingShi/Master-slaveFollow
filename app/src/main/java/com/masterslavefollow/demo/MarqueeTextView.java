package com.masterslavefollow.demo;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;


public class MarqueeTextView extends TextView {
    public MarqueeTextView(Context context) {
        super(context);

        init();
    }
    public MarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }
    public MarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.MARQUEE);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setMarqueeRepeatLimit(-1);
    }

    @Override
    public boolean isFocused() {
        return true;
    }
}