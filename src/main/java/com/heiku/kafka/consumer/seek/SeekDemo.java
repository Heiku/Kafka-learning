package com.heiku.kafka.consumer.seek;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.getConsumer;
import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.topic;

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

        // 获取消费者所分配到的分区信息
        consumer.poll(Duration.ofMillis(10000));
        Set<TopicPartition> assignment = consumer.assignment();

        // 不断拉取，直到获取到分区信息
        while (assignment.size() == 0){
            consumer.poll(Duration.ofMillis(1000));
            assignment = consumer.assignment();
        }
        // 设置每个分区的消费位置为10
        assignment.forEach(topicPartition -> {
            consumer.seek(topicPartition, 10);
        });


        // 定位到末尾消费
        Map<TopicPartition, Long> offsets  = consumer.endOffsets(assignment);
        assignment.forEach(topicPartition -> {
            consumer.seek(topicPartition, offsets.get(topicPartition));
        });
        /// 直接定位
        consumer.seekToEnd(assignment);


        // 根据时间点确定消费位置
        Map<TopicPartition, Long> timestampToSearch = new HashMap<>();
        assignment.forEach(tp -> {
            timestampToSearch.put(tp, System.currentTimeMillis() - 1 * 24 * 3600 * 1000);
        });
        // 找到一天前的分区offset
        Map<TopicPartition, OffsetAndTimestamp> timeOffsets = consumer.offsetsForTimes(timestampToSearch);
        assignment.forEach(tp -> {
            OffsetAndTimestamp offsetAndTimestamp = timeOffsets.get(tp);
            // 如果存在 offset，直接 seek 定位
            if (offsetAndTimestamp != null){
                consumer.seek(tp, offsetAndTimestamp.offset());
            }
        });


        while (true){
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            // consume
        }
    }
}
