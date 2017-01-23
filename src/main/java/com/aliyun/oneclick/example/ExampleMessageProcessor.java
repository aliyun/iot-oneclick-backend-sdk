package com.aliyun.oneclick.example;

import com.aliyun.oneclick.lib.Button;
import com.aliyun.oneclick.lib.Light;
import com.aliyun.oneclick.lib.MessageProcessor;

/**
 * Created by zhirui.rzr on 2017/1/23.
 */
public class ExampleMessageProcessor implements MessageProcessor {
    @Override
    public Light process(int seq, Button status, String serial) {
        switch (status) {
            case CLICK:
                System.out.println(String.format("user clicked the button %s", serial));
                return Light.GREEN;
            case DOUBLE_CLICK:
                System.out.println(String.format("user double click the button %s", serial));
                return Light.MIX_BLINK;
            case LONG_CLICK:
                System.out.println(String.format("user long clicked the button %s", serial));
                return Light.GREEN_BLINK;
        }
        System.out.println("something wrong happened");
        return Light.RED;
    }

    @Override
    public Light errorLight() {
        return Light.RED;
    }
}
