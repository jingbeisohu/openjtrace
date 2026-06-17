package org.openjtrace.example.dubbo.impl;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

@Service
@RocketMQMessageListener(topic = "payment-topic", consumerGroup = "payment-group")
public class PaymentListener implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        // 静态分析器将把本类方法关联到 MQ 依赖树上
        System.out.println("收到支付消息: " + message);
    }
}
