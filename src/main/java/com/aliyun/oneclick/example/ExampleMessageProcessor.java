package com.aliyun.oneclick.example;

import com.aliyun.oneclick.lib.Button;
import com.aliyun.oneclick.lib.Light;
import com.aliyun.oneclick.lib.MessageProcessor;

/**
 * Created by zhirui.rzr on 2017/1/23.
 */
public class ExampleMessageProcessor implements MessageProcessor {
    /**
     * 收到有效按键消息时调用
     * 开发者可实现此方法，在其中处理业务逻辑
     * @param seq 消息序列号（SDK已做去重处理，开发者可以忽略）
     * @param button 按键值（Button枚举型）
     * @param serial 按键序列号
     * @return 期望亮的灯（Light枚举型）
     */
    @Override
    public Light process(int seq, Button button, String serial) {
        switch (button) { // 判断按键值
            case CLICK: // 收到单击按键
                System.out.println(String.format("user clicked the button %s", serial));
                return Light.GREEN; // 亮绿灯
            case DOUBLE_CLICK: // 收到双击按键
                System.out.println(String.format("user double click the button %s", serial));
                return Light.MIX_BLINK; // 红绿混合闪烁
            case LONG_CLICK:  // 收到长按
                System.out.println(String.format("user long clicked the button %s", serial));
                return Light.GREEN_BLINK; // 绿灯闪烁
        }
        System.out.println("something wrong happened");
        return Light.RED; // 其它情况亮红灯
    }

    /**
     * 出错时亮红灯
     * @return Light.RED
     */
    @Override
    public Light errorLight() {
        return Light.RED; // 红灯
    }
}
