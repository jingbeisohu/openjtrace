package org.openjtrace.example.dubbo.impl;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderPayServiceImpl {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void payOrder(String orderId) {
        // 静态分析器将提取 payment-topic 这个常量作为 MQ 依赖端点
        rabbitTemplate.convertAndSend("payment-topic", orderId);
    }
}
