package com.aliyun.oneclick.example;

import com.aliyun.oneclick.lib.MessageLoop;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import java.io.IOException;

/**
 * Created by zhirui.rzr on 2017/1/23.
 */
public class ExampleBiz {
    public static void main(final String[] args) throws IOException {
        // 阿里云开发者账号配置
        String region = "cn-hangzhou"; // 阿里云服务区域
        String accessKeyId = "<输入你的access key id>";
        String accessKeySecret = "<输入你的access key secret>";
        // 消息队列产品配置
        String endpoint = "<消息队列endpoint,可以从消息队列控制台获得>";
        String queueName = "<消息队列名称>";
        // IoT平台配置
        Long productKey = 12345L; // 在IoT平台上注册的产品号
        // redis配置
        JedisConnectionFactory redis = new JedisConnectionFactory();
        redis.setHostName("<redis主机地址>");
        redis.setPort(6379); // redis端口
        redis.setPassword("<redis密码>");

        // 初始化OneClickSDK
        MessageLoop messageLoop = new MessageLoop(region, accessKeyId, accessKeySecret, endpoint, queueName, productKey, redis, new ExampleMessageProcessor());
        messageLoop.start(); // 启动消息循环

        // 挂起主线程（实际应用中可以是主服务循环）
        System.out.println("Message service started. Press Enter to exit.");
        System.in.read();
        System.out.println("Exiting...");

        messageLoop.stop(); // 停止消息循环
    }
}
