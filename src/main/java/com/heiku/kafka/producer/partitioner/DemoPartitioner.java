package com.heiku.kafka.producer.partitioner;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在默认分区器 DefaultPartitioner 的实现中，partition() 方法定义了主要的分区分配逻辑。
 *      如果 key != null，那么 defaultPartition 会对 key 进行 hash（MurmurHash2），
 *          最终根据得到的哈希值计算分区号，拥有相同的 key 的消息会被写入到同一个分区中
 *      如果 key == null，那么消息将会以 轮询 的方式发往主题内的各个可用分区。
 *
 * 如果一个商品的topic，对应多个仓库，可以根据仓库的数量进行合理的分区
 *
 * @author Heiku
 * @date 2020/4/4
 **/
public class DemoPartitioner implements Partitioner {
    private final AtomicInteger counter = new AtomicInteger(0);

    // props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, DemoPartitioner.class.getName())
    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitionInfos = cluster.partitionsForTopic(topic);
        int numPartitions = partitionInfos.size();
        if (null == keyBytes){
            return counter.getAndIncrement() % numPartitions;
        }else {
            return Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions;
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> configs) {

    }
}
