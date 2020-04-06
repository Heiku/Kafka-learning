package com.heiku.kafka.producer;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.Future;

/**
 * @author Heiku
 * @date 2020/4/4
 **/
public class KafkaProducerAnalysis {
    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-demo";

    public static void main(String[] args) {
        Properties props = initConfig();
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "Hello, Kafka!");
        try {
            // send async
            producer.send(record);

            // send block with future
            Future<RecordMetadata> future = producer.send(record);
            future.get();

            // send with callback
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null){
                        exception.printStackTrace();
                    }else {
                        System.out.println(metadata.topic() + "-" + metadata.partition() + ":" + metadata.offset());
                    }
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    public static Properties initConfig(){
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "producer.client.id.demo");

        // acks 指定分区中必须要有多少个副本收到这条调戏，之后 producer 才会认为这条消息写入成功
        // acks = 1，只要分区的 leader 副本成功写入消息，将会收到服务端的响应成功
        // acks = 0，不需要等待服务端返回，可能会存在消息丢失问题，但吞吐量最大
        // acks = -1/all， 需要等待 ISR 中的所有副本写入成功之后，才能收到服务端的成功响应。
        properties.put(ProducerConfig.ACKS_CONFIG, "1");

        // 生产者重试的次数，即发生异常时进行重试的次数
        // 异常：网络抖动、leader 副本的选举等
        // 通常配合着 retry.backoff.ms 设置重试的时间间隔
        properties.put(ProducerConfig.RETRIES_CONFIG, 10);
        properties.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);

        // 限制生产者发送的消息的最大值，默认为 1MB
        properties.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1 * 1024 * 1024);

        // 指定消息的压缩方式，包括了 "gzip" "snappy"等，消息压缩可以极大减少网络的传输量，降低网络 I/O
        // 但这是一种以时间换空间的概念，对时延有一定影响
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");

        // 指定多久之后关闭空闲连接，默认9min
        properties.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, "540000");
        // 配置 Producer 等待请求响应的最长时间
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");

        // 指定生产者发送 ProducerBatch 之前等待更多 ProducerRecord 加入 batch 的时间
        // 增大等待时间会增加消息的延迟，但能提升一定的吞吐量，与 TCP 的 Nagle 算法类似
        properties.put(ProducerConfig.LINGER_MS_CONFIG, "0");

        // 设置 Socket 发送缓冲区的大小
        properties.put(ProducerConfig.SEND_BUFFER_CONFIG, 32768);
        // 设置 Socket 接受缓冲区的大小
        properties.put(ProducerConfig.RECEIVE_BUFFER_CONFIG, 32768);
        return properties;
    }

}
