package com.heiku.kafka.client.transaction;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * @Author: Heiku
 * @Date: 2020/4/24
 */
public class ProducerTransactionDemo {

    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-demo";

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        // 事务配置
        properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "transaction.id");

        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);
        producer.initTransactions();;
        producer.beginTransaction();

        // 事务逻辑
        try {
            ProducerRecord<String, String> record1 = new ProducerRecord<>(topic, "msg1");
            producer.send(record1);

            ProducerRecord<String, String> record2 = new ProducerRecord<>(topic, "msg2");
            producer.send(record2);

            ProducerRecord<String, String> record3 = new ProducerRecord<>(topic, "msg3");
            producer.send(record3);

            // 提交
            producer.commitTransaction();
        }catch (ProducerFencedException e){
            e.printStackTrace();

            // 抛弃
            producer.abortTransaction();
        }
        producer.close();
    }
}
