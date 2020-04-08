package com.heiku.kafka.consumer.concurrent;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.*;

/**
 * @Author: Heiku
 * @Date: 2020/4/8
 */
public class ThirdMultiConsumerThreadDemo {
    public static Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();

    public static void main(String[] args) {
        Properties props=  initConfig();
        KafkaConsumerThread consumerThread = new KafkaConsumerThread(props, topic, Runtime.getRuntime().availableProcessors());
        consumerThread.start();
    }

    // 只有一个消费者，每次拉取的消息直接丢进线程池中执行
    // 减少了 TCP 链接带来的性能消耗，但无法做到顺序处理消息
    public static class KafkaConsumerThread extends Thread{
        private KafkaConsumer<String, String> kafkaConsumer;
        private ExecutorService executorService;
        private int threadNum;

        public KafkaConsumerThread(Properties props, String topic, int threadNum){
            this.kafkaConsumer = new KafkaConsumer<String, String>(props);
            this.kafkaConsumer.subscribe(Arrays.asList(topic));
            this.threadNum = threadNum;

            executorService = new ThreadPoolExecutor(threadNum, threadNum, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1000),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }

        @Override
        public void run() {
            try {
                while (true){
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                    if (!records.isEmpty()){
                        executorService.submit(new RecordsHandler(records));
                        // 处理消息唯一提交
                        synchronized (offsets){
                            if (!offsets.isEmpty()){
                                kafkaConsumer.commitSync(offsets);
                                offsets.clear();
                            }
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                kafkaConsumer.close();
            }
        }

        static class RecordsHandler implements Runnable{
            public final ConsumerRecords<String, String> records;

            public RecordsHandler(ConsumerRecords<String, String> records){
                this.records = records;
            }

            @Override
            public void run() {
                // 更新 map[TopPartition]OffsetAndMetadata
                for (TopicPartition tp : records.partitions()) {
                    List<ConsumerRecord<String, String>> tpRecords = records.records(tp);
                    long lastConsumedOffset = tpRecords.get(tpRecords.size() - 1).offset();
                    synchronized (offsets){
                        if (!offsets.containsKey(tp)){
                            offsets.put(tp, new OffsetAndMetadata(lastConsumedOffset + 1));
                        }else {
                            long partition = offsets.get(tp).offset();
                            if (partition < lastConsumedOffset + 1){
                                offsets.put(tp, new OffsetAndMetadata(lastConsumedOffset + 1));
                            }
                        }
                    }
                }
            }
        }
    }
}
