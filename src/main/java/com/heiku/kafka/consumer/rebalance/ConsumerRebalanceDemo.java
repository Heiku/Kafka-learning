package com.heiku.kafka.consumer.rebalance;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.getConsumer;
import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.isRunning;

/**
 * (消费组 / 消息)
 * 如果所有的消费者都属于同一个消费组，那么所有的消息都会被均衡地投递给每一个消费者，即每条消息只会被一个消费者处理，
 * 那就相当于点对点模式
 * 如果所有的消费者都不属于同一个消费组，那么所有的消息都会被广播给所有的消费者，即每条消息会被所有的消费者处理，
 * 这就相当于发布/订阅模式
 *
 *
 * 再均衡：指分区的所属权从一个消费者转移到另一个消费者的行为。
 *
 * 再均衡为消费者具备高可用性和伸缩性提供保障，使我们可以既方便又安全地删除消费组内的消费者或往消费组内添加消费者
 * (因为我们每次加入消费者或者删除消费者都会影响到 这个group 下的 consumer 所分配的分区区间)
 *
 * 注意：当一个分区被重新分配给另一个消费者时，消费者当前的状态也会丢失，
 *      （比如未提交消费位移之前发生 reBalance 操作，导致这个分区下的消息被另一个消费者重复消费）
 *
 * @Author: Heiku
 * @Date: 2020/4/8
 */
public class ConsumerRebalanceDemo {

    public static void main(String[] args) {
        KafkaConsumer<String, String> consumer = getConsumer();
        Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
        consumer.subscribe(Arrays.asList("topic"), new ConsumerRebalanceListener() {

            // 再均衡开始之前和消费者停止读取消息之后被调用
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                consumer.commitSync(currentOffsets);
                currentOffsets.clear();

                // 或者在发生再均衡的时候 store offset in DB
            }

            // 重新分配分区之后和消费者开始读取消息之前
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                // 在恢复读取之后，consumer.seek(partition, getOffsetFromDB(partition))
            }
        });

        try {
            // 每次消费的时候都在本地保存最新的map[partition]offsets，当发生再均衡的时候，直接提交本地记录
            while (isRunning.get()){
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                records.forEach(record -> {
                    currentOffsets.put(new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1));
                });
                consumer.commitAsync(currentOffsets, null);
            }
        }finally {
            consumer.close();
        }
    }
}
