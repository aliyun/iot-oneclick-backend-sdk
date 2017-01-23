package com.aliyun.oneclick.lib;

/**
 * Created by zhirui.rzr on 2017/1/22.
 */
public enum Button {
    CLICK(0),          // 单击按钮
    DOUBLE_CLICK(2),   // 双击按钮
    LONG_CLICK(1);     // 长按按钮

    int value;

    Button(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
