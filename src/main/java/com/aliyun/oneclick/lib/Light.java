package com.aliyun.oneclick.lib;

/**
 * Created by zhirui.rzr on 2017/1/22.
 */
public enum Light {
    GREEN(0),       // 绿灯亮
    BLINK(1),       // 红灯闪
    GREEN_BLINK(2), // 绿灯闪
    MIX_BLINK(3),   // 红绿灯交替闪
    RED(4);         // 红灯亮

    int value;

    Light(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
