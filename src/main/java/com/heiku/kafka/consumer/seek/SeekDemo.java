package com.heiku.kafka.consumer.seek;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.getConsumer;

/**
 * 在 Kafka 中每当消费者查找不到所记录的消费位移时，会根据配置 auto.offset.reset 选择合适的 offset
 *
 * [0, 9]
 * latest: 从分区末尾开始消费消息 9
 * earliest: 从分区的起始开始消费 0
 *
 * @author Heiku
 * @date 2020/4/6
 **/
public class SeekDemo {
    public static void main(String[] args) {

        // 使用 seek() 获取之前的消息位置
        KafkaConsumer<String, String> consumer = getConsumer();
        consumer.subscribe(Arrays.asList("topic-demo"));
        consumer.poll(Duration.ofMillis(10000));

        // 获取消费者所分配到的分区信息
        Set<TopicPartition> assignment = consumer.assignment();
        assignment.forEach(topicPartition -> {
            consumer.seek(topicPartition, 10);
        });
        while (true){
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            // consume
        }
    }
}
