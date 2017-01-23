package com.aliyun.oneclick.lib;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mns.client.CloudAccount;
import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.iot.model.v20160530.PubRequest;
import com.aliyuncs.iot.model.v20160530.PubResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.JedisPool;

import java.util.LinkedList;
import java.util.List;


public class MessageLoop {
    String region;
    String accessKeyId;
    String accessKeySecret;
    String endpoint;
    String queueName;
    Long productKey;
    RedisService redisService;
    MessageProcessor messageProcessor;
    int seqTimeout = 60000;
    String redisPrefix = "";
    int version = 1;
    int batchSize = 16;
    int batchTimeout = 10;

    CloudAccount account;
    MNSClient mnsClient;
    CloudQueue queue;
    IClientProfile profile;
    DefaultAcsClient acsClient;
    Logger logger = LogManager.getLogger(MessageLoop.class);
    Thread thread = null;
    volatile boolean started = false;

    /**
     * 初始化实例
     * @param region 区域
     * @param accessKeyId 阿里云OpenAPI Access Key ID
     * @param accessKeySecret 阿里云OpenAPI Access Key Secret
     * @param endpoint 消息队列的endpoint(可从消息队列控制台获得)
     * @param queueName 消息队列名称
     * @param productKey IoT平台上注册的产品编号
     * @param jedisPool redis连接池
     * @param messageProcessor 自定义的设备消息处理器
     */
    public MessageLoop(String region, String accessKeyId, String accessKeySecret, String endpoint, String queueName, Long productKey, JedisPool jedisPool, MessageProcessor messageProcessor) {
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.endpoint = endpoint;
        this.queueName = queueName;
        this.productKey = productKey;
        this.messageProcessor = messageProcessor;

        // 初始化队列使用
        account = new CloudAccount(accessKeyId, accessKeySecret, endpoint); // ak和endpoint
        mnsClient = account.getMNSClient(); // 初始化mns客户端
        queue = mnsClient.getQueueRef(queueName); // queueName为队列名称，如：order-ops
        profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret); // 用ak和region组成profile
        acsClient = new DefaultAcsClient(profile); // 创建acs client
        this.redisService = new RedisService(jedisPool);
    }

    /**
     * 停止消息处理循环线程
     */
    public void stop() {
        if (thread != null) {
            started = false;
            // wait for exit
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 启动处理消息循环的线程，主程序退出时会自动退出。
     */
    public void start() {
        start(true); // 默认为daemon线程
    }

    /**
     * 启动处理消息循环的线程
     * @param daemon 是否daemon线程（如果为true，主线程退出时会自动退出）
     */
    public void start(boolean daemon) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 读取队列循环
                loop();
            }
        });
        thread.setDaemon(daemon);
        thread.start();
        logger.info("Message loop started.");
    }

    /**
     * 进入消息队列读取循环
     */
    public void loop() {
        started = true;
        while (started) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<Message> messages = queue.batchPopMessage(batchSize, batchTimeout); // 阻塞式批量读取消息
            List<String> messageHandles = new LinkedList<>(); // 记录处理过的消息，以便在处理完后从消息队列中删除
            if (messages == null) continue;
            for (Message msg : messages) {
                processMessage(msg.getMessageBodyAsRawString()); // 处理单条消息
                messageHandles.add(msg.getReceiptHandle()); // 记录处理过的消息
            }
            queue.batchDeleteMessage(messageHandles); // 从消息队列中删除已经处理完的消息
        }
        mnsClient.close();
        redisService.close();
    }

    /**
     * 处理设备发送过来的消息
     * 消息为JSON格式字符串，包含消息序列号id（保证幂等性），设备号serial，按钮状态btn和校验码
     * 消息结构为：{"serial":"设备序列号","data":{"id":消息序列号,"btn":按钮状态},"cksum":校验码}
     * 校验方法见verifyChecksum
     * @param data JSON格式的设备消息
     */
    private void processMessage(String data) {
        logger.info("process message: {}", data);
        String deviceSerial = null;
        int seq = 0;
        try {
            JSONObject obj = JSON.parseObject(data);
            JSONObject dataObj = obj.getJSONObject("data");
            seq = dataObj.getInteger("id");
            deviceSerial = obj.getString("serial");
            int status = dataObj.getInteger("btn");
            if(!verifyChecksum(obj)){ // 检查校验值失败
                logger.error("verify checksum failed: %s", obj.toJSONString());
                sendMessage(deviceSerial, seq, messageProcessor.errorLight());
                return;
            }
            if (processDupMsg(deviceSerial, seq)) return; // 如果是重复消息，直接返回
            Light result = messageProcessor.process(seq, Button.fromInt(status), deviceSerial); // 调用自定义业务逻辑
            sendMessage(deviceSerial, seq, result); // 返回处理结果
        } catch (Exception e) {
            logger.error("error process message: {}", e);
            e.printStackTrace();
            if (deviceSerial != null) sendMessage(deviceSerial, seq, messageProcessor.errorLight()); // 意外出错，返回错误灯
        }
    }

    /**
     * 检查并处理重复消息
     * 通过消息序列号seq判断是否重复消息，如果是重复消息，向设备返回上次发送过的结果
     * @param deviceSerial 设备序列号
     * @param seq 消息序列号
     * @return 如果是重复消息，返回true
     */
    private boolean processDupMsg(String deviceSerial, int seq) {
        Integer dupVal = getSeqVal(deviceSerial, seq);
        if (dupVal != null) {
            sendMessage(deviceSerial, seq, dupVal); // 返回上次结果
            return true;
        }
        return false;
    }

    /**
     * 判断是否重复消息并取得重复的按钮值
     * @param serial 设备序列号
     * @param seq 消息序列号
     * @return 如果是重复消息返回重复值，否则返回null
     */
    private Integer getSeqVal(String serial, int seq) {
        try {
            String val = redisService.get(getDeviceSeqKey(serial));
            if (val == null) return null;
            String[] chunks = val.split(":");
            if (Integer.parseInt(chunks[0]) == seq) return Integer.parseInt(chunks[1]);
        } catch (Exception e) {
            logger.error("check seq error: {}", e);
        }
        return null;
    }

    private String getDeviceSeqKey(String serial) {
        return String.format("SEQ-%s", serial);
    }

    /**
     * 校验设备发送过来的数据
     * 校验方法：data字段各字符(单字节)值相加取反，得到cksum
     * @param obj 已解析成JSONObject的数据
     * @return 校验通过为true,否则false
     */
    private boolean verifyChecksum(JSONObject obj) {
        JSONObject dataObj = obj.getJSONObject("data");
        byte sum = 0;
        for (byte b: dataObj.toJSONString().getBytes()) {
            sum += b;
        }
        return sum == ~obj.getByte("cksum");
    }

    /**
     * 向设备发送消息
     * @param serial 设备序列号
     * @param seq 消息序号，取自设备发送的消息（用以保证幂等性）
     * @param light 亮点状态
     */
    public void sendMessage(String serial, int seq, Light light) {
        sendMessage(serial, seq, light.value);
    }

    /**
     *  向设备发送消息
     *  @param serial 设备序列号
     *  @param seq 消息序号，取自设备发送的消息(用以保证幂等性)
     *  @param value 亮灯状态
     */
    public void sendMessage(String serial, int seq, int value) {
        logger.info("send serial:{} seq:{} value:{}", serial, seq, value);
        String msg = encodeLightMessage(seq, value);
        logger.debug("send {}", msg);
        PubRequest pub = new PubRequest();
        pub.setProductKey(productKey);
        pub.setMessageContent(Base64.encodeBase64String(msg.getBytes()));
        pub.setTopicFullName(String.format("/%s/%s/response", productKey, serial));
        pub.setQos(0);
        try {
            PubResponse response = acsClient.getAcsResponse(pub);
            if (response.getSuccess()) {
                setSeqVal(serial, seq, value); // 记录发送结果
            } else {
                logger.error("send failed, reason: {}", response.getErrorMessage());
            }
        } catch (ClientException e) {
            logger.error("send failed: {}", e);
            e.printStackTrace();
        }
    }

    /**
     * 记录发送过的结果
     * @param serial 设备序列号
     * @param seq 消息序列号
     * @param value 消息值
     */
    private void setSeqVal(String serial, int seq, int value) {
        try {
            redisService.set(getDeviceSeqKey(serial), String.format("%d:%d", seq, value), seqTimeout);
        } catch (Exception e) {
            logger.error("set seq error: {}", e);
        }
    }

    /**
     * 把亮灯状态编码成包括校验码的JSON字符串
     * @param seq 消息序列号
     * @param status 亮灯状态(LT_GREEN, LT_RED, LT_BLINK_GREEN, LT_BLINK_RED)
     * @return 编码后的JSON字符串
     */
    private String encodeLightMessage(int seq, int status) {
        JSONObject dataObj = new JSONObject();
        dataObj.put("id", seq);
        dataObj.put("lt", status);
        dataObj.put("v", version);
        byte sum = 0;
        for (byte b: dataObj.toJSONString().getBytes()) {
            sum += b;
        }
        JSONObject obj = new JSONObject();
        obj.put("data", dataObj);
        obj.put("cksum", ~sum);
        return obj.toJSONString();
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public Long getProductKey() {
        return productKey;
    }

    public void setProductKey(Long productKey) {
        this.productKey = productKey;
    }

    public RedisService getRedisService() {
        return redisService;
    }

    public void setRedisService(RedisService redisService) {
        this.redisService = redisService;
    }

    public MessageProcessor getMessageProcessor() {
        return messageProcessor;
    }

    public void setMessageProcessor(MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    public int getSeqTimeout() {
        return seqTimeout;
    }

    public void setSeqTimeout(int seqTimeout) {
        this.seqTimeout = seqTimeout;
    }

    public String getRedisPrefix() {
        return redisPrefix;
    }

    public void setRedisPrefix(String redisPrefix) {
        this.redisPrefix = redisPrefix;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(int batchTimeout) {
        this.batchTimeout = batchTimeout;
    }
}
