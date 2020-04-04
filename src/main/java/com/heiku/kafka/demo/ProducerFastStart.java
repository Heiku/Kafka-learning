package com.heiku.kafka.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

/**
 * @author Heiku
 * @date 2020/4/4
 **/
public class ProducerFastStart {
    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-demo";

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("bootstrap.servers", brokerList);

        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);
        int i = 0;
        while (true){
            String str = String.format("hello, comsumer[%d]", i++);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, str);

            try {
                Thread.sleep(1000);

                // send record
                producer.send(record);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
