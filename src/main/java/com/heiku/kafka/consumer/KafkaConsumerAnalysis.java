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

    // for default
    public static KafkaConsumer<String, String> consumer;

    public static Properties initConfig(){
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "consumer.client.id.demo");

        // fetch.min.bytes
        // 一次 poll() 中能从 Kafka 中拉取的最小数据量，（1B）
        // 如果拉取的过程中，发现小于该配置，则需要等待直至数据量满足并返回。
        // 适当调大可以提高一定的吞吐量，但会造成额外的延迟
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);

        // fetch.max.bytes
        // 一次 poll() 中能够拉取的最大数据量
        // 为了能让消费者正常工作，如果分区中的第一条消息大于 maxBytes，仍能被消费者拉取
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 52428800);

        // fetch.max.wait.ms
        // 用于配置当 数据量小于 minBytes时，等待的最长时间，超过直接返回当前的数据
        // 对于延迟比较敏感的可以适当调小
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // max.partition.fetch.bytes
        // 用于配置每个分区返回给 Consumer 的最大数据量
        // fetch.max.bytes 限制一次拉取整个分区的大小，而 max.partition.fetch.bytes 限制一次拉取每个分区消息的大小
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576);

        // max.poll.records
        // 限制 poll() records 的消息数量
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        // connections.max.idle.ms
        // 指定多久之后关闭链接
        props.put(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 540000);

        // exclude.internal.topics
        // 是否向消费者开放内部主题：_consumer_offsets 和 _transaction_state
        props.put(ConsumerConfig.EXCLUDE_INTERNAL_TOPICS_CONFIG, true);

        // 设置 Socket 发送、接收缓冲区的大小
        props.put(ConsumerConfig.SEND_BUFFER_CONFIG, 65535);
        props.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, 131072);

        // request.timeout.ms
        // 配置 Consumer 等待请求响应的最长时间
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        // metadata.age.ms
        // 配置元信息的过期时间，超过时间后将强制更新
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 300000);

        // reconnect.backoff.ms
        // 配置尝试重新连接指定主机之前的等待时间，避免频繁连接主机
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 50);

        // retry.backoff.ms
        // 配置尝试重新发送失败的请求到指定的主题分区之前的等待时间，避免了在某些故障情况下频繁地重复发送
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 100);

        // isolation.level
        // 配置消费者地事务隔离级别，表示消费者所消费到地位置 (LSO、HW)
        // "read_uncommitted"： HW
        // "read_committed":    LSO
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "");

        // 配置消费组的名称
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return props;
    }

    public static void main(String[] args) {
        Properties props = initConfig();
        consumer = new KafkaConsumer<String, String>(props);

        // 在订阅前可以通过 partitionInfo 获取想要的订阅信息，获取主题下的所有分区信息
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

    public static KafkaConsumer<String, String> getConsumer(){
        return consumer;
    }
}
