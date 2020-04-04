package com.heiku.kafka.producer.interceptor;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

/**
 * KafkaProducer 在将消息序列化 和计算分区之前会调用 producer 拦截器的 onSend() 对消息进行相应的操作
 * (Interceptor#OnSend() -> partitioner -> serializer)
 *
 * note: 不要在 onSend() 中对 producerRecord 中的 topic、key、partition 进行修改，否则会影响到分区的计算，
 * 同样会影响 broker 端日志压缩 （Log Compaction）
 *
 * KafkaProducer 会在消息被应答 (Acknowledgement) 之前或者消息发送失败时调用 producer 拦截器的 onAcknowledgement()，
 * 优先于用户设定的 Callback 之前执行。 （Acknowledgement -> Callback）
 *
 * note: onAcknowledgement() 运行在 Producer 的 I/O 线程中，所以这个方法的逻辑越简单越好，否则会影响到消息发送的速率
 *
 * @author Heiku
 * @date 2020/4/4
 **/
public class ProducerInterceptorPrefix implements ProducerInterceptor<String, String> {
    private volatile long sendSuccess = 0;
    private volatile long sendFailure = 0;


    // properties.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, ProducerInterceptorPrefix.class.getName() + "," + ...)
    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        String modifiedValue = "prefix-" + record.value();
        return new ProducerRecord<>(record.topic(), record.partition(), record.timestamp(),
                record.key(), modifiedValue, record.headers());
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (exception == null){
            sendSuccess++;
        }else {
            sendFailure++;
        }
    }

    @Override
    public void close() {
        double successRatio = (double) sendSuccess / (sendSuccess + sendFailure);
        System.out.println("[INFO] 发送成功率=" + String.format("%f", successRatio * 100) + "%");
    }

    @Override
    public void configure(Map<String, ?> configs) {

    }
}
