package com.heiku.kafka.producer.serializer;

import com.heiku.kafka.producer.serializer.entity.Company;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static com.heiku.kafka.producer.KafkaProducerAnalysis.initConfig;

/**
 * @author Heiku
 * @date 2020/4/4
 **/
public class KafkaProducerSerializer {
    public static final String topic = "custom-serializer";

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Properties props = initConfig();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // use custom serializer
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CompanySerializer.class.getName());
        KafkaProducer<String, Company> producer = new KafkaProducer<String, Company>(props);

        Company company = Company.builder().name("LinkedIn").address("China").build();
        ProducerRecord<String, Company> record = new ProducerRecord<>(topic, company);
        producer.send(record).get();
    }
}
