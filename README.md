# OneClick Backend SDK

在一键触发场景下，组合阿里云物联网相关产品，处理物联网平台消息的SDK。

## 主要流程

1. 按下按键
1. 按键消息进入IoT网关
1. 按键消息通过规则引擎转发到消息队列
1. SDK程序从队列中拉取消息
1. 验证、去重后将按键消息push给开发者自定义处理程序
1. 开发者自定义消息处理程序开始业务流程，并返回结果
1. SDK程序将结果返回给IoT网关
1. 物联网平台将结果发送给按键
1. 按键亮灯

## 依赖

1. 物联网套件
1. 消息服务
1. Redis服务

## 接入步骤

详见文档：[产品配置步骤 for OneClick](Setup_For_OneClick.pdf)

## 接入代码

1. 实现业务消息处理接口`MessageProcessor`。参考example目录下的`ExampleMessageProcessor`
1. 初始化并启动`MessageLoop`。参考example目录下的`ExampleBiz`
