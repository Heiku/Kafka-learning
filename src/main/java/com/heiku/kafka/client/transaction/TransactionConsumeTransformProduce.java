package com.heiku.kafka.client.transaction;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;

/**
 * topic-source                                         topic-sink
 *
 *  kafkaConsumer   ->      Application(Transform)      -> KafkaProducer
 *
 * 消费 - 转换 - 生产模式
 *
 * @Author: Heiku
 * @Date: 2020/4/24
 */
public class TransactionConsumeTransformProduce {
    public static final String brokerList = "localhost:9092";

    public static Properties getConsumerProperties(){
        Properties prop = new Properties();
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        prop.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prop.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // 关闭自动提交
        prop.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        prop.put(ConsumerConfig.GROUP_ID_CONFIG, "groupId");
        return prop;
    }

    public static Properties getProducerProperties(){
        Properties prop = new Properties();
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        prop.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prop.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // 开启事务
        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "transactionId");
        return prop;
    }

    public static void main(String[] args) {
        // 初始化生产者和消费者
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(getConsumerProperties());
        consumer.subscribe(Collections.singletonList("topic-source"));

        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(getProducerProperties());
        producer.initTransactions();

        while (true){
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            if (!records.isEmpty()){
                Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                // begin transaction
                producer.beginTransaction();
                try {
                    records.partitions().forEach(topicPartition -> {
                        List<ConsumerRecord<String, String>> partitionRecords = records.records(topicPartition);
                        partitionRecords.forEach(record -> {
                            // do some logical processing

                            // then send the record
                            ProducerRecord<String, String> producerRecord = new ProducerRecord<>("topic-sink", record.key(), record.value());
                            producer.send(producerRecord);
                        });

                        // compute the new offset
                        long lastConsumedOffset = partitionRecords.get(partitionRecords.size() - 1).offset();
                        offsets.put(topicPartition, new OffsetAndMetadata(lastConsumedOffset + 1));
                    });

                    // commit
                    producer.sendOffsetsToTransaction(offsets, "groupId");
                    producer.commitTransaction();
                }catch (ProducerFencedException e){
                    e.printStackTrace();

                    // abort transaction
                    producer.abortTransaction();
                }
            }
        }
    }
}
