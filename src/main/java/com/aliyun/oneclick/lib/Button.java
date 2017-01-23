package com.aliyun.oneclick.lib;

/**
 * Created by zhirui.rzr on 2017/1/22.
 */
public enum Button {
    CLICK,          // 单击按钮
    DOUBLE_CLICK,   // 双击按钮
    LONG_CLICK;     // 长按按钮

    public static Button fromInt(int value) {
        switch(value) {
            case 0: return CLICK;
            case 2: return DOUBLE_CLICK;
            case 1: return LONG_CLICK;
            default: return null;
        }
    }
}
