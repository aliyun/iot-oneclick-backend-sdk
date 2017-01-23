package com.aliyun.oneclick.lib;

/**
 * Created by zhirui.rzr on 2017/1/22.
 */
public interface MessageProcessor {
    Light process(int seq, Button status, String serial);
    Light errorLight();
}
