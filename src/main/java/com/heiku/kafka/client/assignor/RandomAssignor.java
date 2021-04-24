package com.heiku.kafka.client.assignor;

import org.apache.kafka.clients.consumer.internals.AbstractPartitionAssignor;
import org.apache.kafka.common.TopicPartition;

import java.util.*;

/**
 * 随机分配的分配策略
 *
 * properties.put(ConsumerConfig.PARTITION_ASSIGMENT_STRATEGY_CONFIG, RandomAssignor.class.getName())
 *
 * @Author: Heiku
 * @Date: 2020/4/22
 */
public class RandomAssignor extends AbstractPartitionAssignor {

    @Override
    public String name() {
        return "random";
    }

    /**
     * 获取每个主题对应的消费者列表 map[topic] list<Consumer>
     */
    private Map<String, List<String>> consumerPerTopic(Map<String, Subscription> consumerMetadata){
        Map<String, List<String>> res = new HashMap<>();
        consumerMetadata.forEach((consumerId, subscription) -> subscription.topics().forEach(topic -> {
            put(res, topic, consumerId);
        }));
        return res;
    }

    /**
     * 每个消费者被分配的分区列表
     */
    @Override
    public Map<String, List<TopicPartition>> assign(Map<String, Integer> partitionsPerTopic, Map<String, Subscription> subscriptions) {
        Map<String, List<String>> consumersPerTopic = consumerPerTopic(subscriptions);

        Map<String, List<TopicPartition>> assignment = new HashMap<>();
        subscriptions.keySet().forEach(memberId -> {
            assignment.put(memberId, new ArrayList<>());
        });

        // 对每个主题进行分区分配
        consumersPerTopic.forEach((topic, consumerList) -> {
            int consumerSize = consumerList.size();

            // 获取每个主题下的分区数量
            Integer numPartitionsForTopic = partitionsPerTopic.get(topic);
            if (numPartitionsForTopic == null) {
                return;
            }
            // 获取当前主题下的所有分区
            List<TopicPartition> partitions = AbstractPartitionAssignor.partitions(topic, numPartitionsForTopic);
            // 每一个分区随机分配一个消费者
            partitions.forEach(topicPartition -> {
                int rand = new Random().nextInt(consumerSize);
                String randomConsumer = consumerList.get(rand);
                assignment.get(randomConsumer).add(topicPartition);
            });
        });
        return assignment;
    }
}
