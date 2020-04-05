package com.heiku.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @author Heiku
 * @date 2020/4/5
 **/
public class KafkaConsumerAnalysis {
    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo";
    public static final AtomicBoolean isRunning = new AtomicBoolean(true);

    public static Properties initConfig(){
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "consumer.client.id.demo");

        // 配置消费组的名称
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return props;
    }

    public static void main(String[] args) {
        Properties props = initConfig();
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);

        // 在订阅前可以通过 partitionInfo 获取想要的订阅信息
        List<TopicPartition> partitions = new ArrayList<>();
        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
        if (partitionInfos != null){
            partitionInfos.forEach(info -> partitions.add(new TopicPartition(info.topic(), info.partition())));
        }

        consumer.subscribe(Arrays.asList(topic));
        // 可以通过正则表达式的方式订阅多个topic
        consumer.subscribe(Pattern.compile("topic-.*"));
        // 也可以直接指定 topic partition (topic-demo 下的 0 分区)
        consumer.assign(Arrays.asList(new TopicPartition("topic-demo", 0)));


        try {
            while (isRunning.get()){
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));
                records.forEach(record -> {
                    System.out.println("topic = " + record.topic() + ", partition = " + record.partition() +
                            ", offset = " + record.offset());
                    System.out.println("key = " + record.key() + ", value = " + record.value());
                });
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            consumer.close();
        }
    }
}
