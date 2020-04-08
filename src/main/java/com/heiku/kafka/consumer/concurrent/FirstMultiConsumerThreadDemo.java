package com.heiku.kafka.consumer.concurrent;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.initConfig;
import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.topic;

/**
 * @Author: Heiku
 * @Date: 2020/4/8
 */
public class FirstMultiConsumerThreadDemo {

    public static void main(String[] args) {
        Properties props = initConfig();
        int consumerNum = 10;
        for (int i = 0; i < consumerNum; i++) {
            new KafkaConsumerThread(props, topic).start();
        }
    }

    // 一个线程对应一个 tcp 链接，但这样能保证每个消耗线程都能够顺序地处理消息
    public static class KafkaConsumerThread extends Thread{
        private KafkaConsumer<String, String> kafkaConsumer;

        public KafkaConsumerThread(Properties properties, String topic){
            this.kafkaConsumer = new KafkaConsumer<String, String>(properties);
            this.kafkaConsumer.subscribe(Arrays.asList(topic));
        }

        @Override
        public void run() {
            try {
                while (true){
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        // do somethings
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                kafkaConsumer.close();
            }
        }
    }
}
