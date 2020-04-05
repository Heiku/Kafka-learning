package com.heiku.kafka.consumer.deserializer;

import com.heiku.kafka.producer.serializer.entity.Company;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * @author Heiku
 * @date 2020/4/5
 **/
public class CompanyDeserializer implements Deserializer<Company> {

    @Override
    public Company deserialize(String topic, byte[] data) {
        if (data == null){
            return null;
        }
        if (data.length < 8){
            throw new SerializationException("Size of data received by DemoDeserializer is shorter than" +
                    " exppected !");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int nameLen, addressLen;
        String name, address;

        // read data bytes
        nameLen = buffer.getInt();
        byte[] nameBytes = new byte[nameLen];
        buffer.get(nameBytes);
        addressLen = buffer.getInt();
        byte[] addressBytes = new byte[addressLen];
        buffer.get(addressBytes);

        try {
            name = new String(nameBytes, "UTF-8");
            address = new String(addressBytes, "UTF-8");
        }catch (UnsupportedEncodingException e){
            throw new SerializationException("Error occur when deserializing !");
        }
        return new Company(name, address);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }
}
