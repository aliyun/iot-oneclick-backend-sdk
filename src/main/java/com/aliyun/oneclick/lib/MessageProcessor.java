package com.aliyun.oneclick.lib;

/**
 * Created by zhirui.rzr on 2017/1/22.
 */
public interface MessageProcessor {
    int process(int seq, int status, String serial);
    int errorLight();
}
