package com.heiku.kafka.consumer.offset;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.initConfig;
import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.topic;
import static org.apache.kafka.common.requests.FetchMetadata.log;

/**
 * [x+2, x+7] 当前正在处理 x+5 的时候，出现了异常
 *
 * 重复消费: 当处理完所有的消息之后才提交，发生异常之后，重新加载 [x+2, x+7]，导致 [x+2, x+5]区间重复消费
 * 消息丢失: 还未开始消费的时候就提交 x+7，当发生异常时，重新加载 [x+8 ...]，导致 [x+5, x+8] 区间消息丢失
 *
 * @author Heiku
 * @date 2020/4/6
 **/
public class OffsetDemo {
    private static AtomicBoolean isRunning = new AtomicBoolean(true);

    public static void main(String[] args) {
        Properties props = initConfig();

        // 关闭程序自动提交 offset
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);

        // lastConsumedOffset、commited offset、position
        TopicPartition tp = new TopicPartition(topic, 0);
        consumer.assign(Arrays.asList(tp));
        // 上次消费的位移
        long lastConsumedOffset = -1;
        while (true){
            ConsumerRecords<String, String> records = consumer.poll(1000);
            if (records.isEmpty()){
                break;
            }
            List<ConsumerRecord<String, String>> recordList = records.records(tp);
            // 获取最后一个 consumerRecord 的 offset
            lastConsumedOffset = recordList.get(recordList.size() - 1).offset();
            consumer.commitSync();      // 同步提交消费位移
        }
        System.out.println("consumed offset is " + lastConsumedOffset);         // 377
        OffsetAndMetadata offsetAndMetadata = consumer.committed(tp);
        System.out.println("commited offset is " + offsetAndMetadata.offset()); // 378
        long position = consumer.position(tp);
        System.out.println("the offset of the next record is " + position);     // 378


        // 异步提交
        while (isRunning.get()){
            ConsumerRecords<String, String> records = consumer.poll(1000);
            records.forEach(record -> {
                // doSomething
            });
            consumer.commitAsync(new OffsetCommitCallback() {
                @Override
                public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets, Exception exception) {
                    if (exception == null){
                        System.out.println(offsets);
                    }else {
                        log.error("failed to commit offsets {}", offsets, exception);
                    }
                }
            });
        }

        // close consumer
        try {
            while (isRunning.get()){
                ConsumerRecords<String, String> records = consumer.poll(1000);
                records.forEach(record -> {
                    // doSomething
                });
                consumer.commitAsync(new OffsetCommitCallback() {
                    @Override
                    public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets, Exception exception) {
                        if (exception == null){
                            System.out.println(offsets);
                        }else {
                            log.error("failed to commit offsets {}", offsets, exception);
                        }
                    }
                });
            }
        } catch (WakeupException e) {
            // ignore the error
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            consumer.close();
        }
    }
}
