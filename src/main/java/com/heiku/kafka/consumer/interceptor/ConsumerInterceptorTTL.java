package com.heiku.kafka.consumer.interceptor;

import com.heiku.kafka.BytesUtils;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TTL (time to live，过期时间) 在 header 中标记消息是否有 TTL 标识，如果存在则进行消息判断
 *
 * 如果某条消息在既定的时间窗口内无法到达，那么将被视为无效，不再进行处理
 *
 * @Author: Heiku
 * @Date: 2020/4/8
 */
public class ConsumerInterceptorTTL implements ConsumerInterceptor<String, String> {

    private static final long EXPIRE_INTERVAL = 10 * 1000L;

    // consumer 使用自定义的 拦截器
    // props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG ,ConsumerInterceptorTTL.class.getName());

    // 在 poll() 方法返回之前调用 onConsume() 即对用户展示之前进行处理
    @Override
    public ConsumerRecords<String, String> onConsume(ConsumerRecords<String, String> records) {
        long now = System.currentTimeMillis();

        Map<TopicPartition, List<ConsumerRecord<String, String>>> newRecords = new HashMap<>();
        for (TopicPartition tp : records.partitions()) {
            List<ConsumerRecord<String, String>> tpRecords = records.records(tp);
            List<ConsumerRecord<String, String>> newTpRecords = new ArrayList<>();

            for (ConsumerRecord<String, String> record : tpRecords) {
                Headers headers = record.headers();
                long ttl = -1;
                for (Header header : headers){
                    if (header.key().equalsIgnoreCase("ttl")){
                        ttl = BytesUtils.bytesToLong(header.value());
                    }
                }
                // 在超时时间内
                if (ttl > 0 && now - record.timestamp() < ttl * 1000){
                    newTpRecords.add(record);
                }else if (ttl < 0){     // 没设置 ttl，不需要过滤
                    newTpRecords.add(record);
                }
            }
            if (!newTpRecords.isEmpty()){
                newRecords.put(tp, newTpRecords);
            }
        }
        return new ConsumerRecords<>(newRecords);
    }

    // 在提交完消费位移之后调用 onCommit()
    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        offsets.forEach((tp, offset) -> {
            System.out.println(tp + ":" + offset.offset());
        });
    }

    @Override
    public void configure(Map<String, ?> configs) {

    }

    @Override
    public void close() {

    }
}
