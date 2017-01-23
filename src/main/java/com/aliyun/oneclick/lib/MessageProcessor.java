package com.aliyun.oneclick.lib;

/**
 * Created by zhirui.rzr on 2017/1/22.
 */
public interface MessageProcessor {
    /**
     * 收到有效按键消息时调用
     * 开发者可实现此方法，在其中处理业务逻辑
     * @param seq 消息序列号（SDK已做去重处理，开发者可以忽略）
     * @param button 按键值（Button枚举型）
     * @param serial 按键序列号
     * @return 期望亮的灯（Light枚举型）
     */
    Light process(int seq, Button button, String serial);

    /**
     * SDK处理出错时，开发者期望亮的灯
     * @return 亮灯值，Light枚举型
     */
    Light errorLight();
}
