package com.heiku.kafka.client.assignor;


import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerPartitionAssignor;
import org.apache.kafka.common.Cluster;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/**
 * 自定义分区分配策略
 *
 * @Author: Heiku
 * @Date: 2020/4/22
 */
public class SimpleAssignor implements ConsumerPartitionAssignor {
    @Override
    public short version() {
        return 0;
    }

    @Override
    public GroupAssignment assign(Cluster metadata, GroupSubscription groupSubscription) {
        return null;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public ByteBuffer subscriptionUserData(Set<String> topics) {
        return null;
    }

    @Override
    public void onAssignment(Assignment assignment, ConsumerGroupMetadata metadata) {

    }

    @Override
    public List<RebalanceProtocol> supportedProtocols() {
        return null;
    }
}
