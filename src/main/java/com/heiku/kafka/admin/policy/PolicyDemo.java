package com.heiku.kafka.admin.policy;

import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.server.policy.CreateTopicPolicy;

import java.util.Map;

/**
 * policy 提供了一个入口用来验证主题创建的合法性
 *
 * @Author: Heiku
 * @Date: 2020/4/9
 */
public class PolicyDemo implements CreateTopicPolicy {

    // config/server.properties
    // create.topic.policy.class=com.heiku.kafka.admin.policy.PolicyDemo
    @Override
    public void validate(RequestMetadata requestMetadata) throws PolicyViolationException {
        // check request partitionNum and replicationFactor
        if (requestMetadata.numPartitions() != null || requestMetadata.replicationFactor() != null){
            if (requestMetadata.numPartitions() < 5){
                throw new PolicyViolationException("Topic should have at least 5 partitions, received: " + requestMetadata.numPartitions());
            }
            if (requestMetadata.replicationFactor() <= 1){
                throw new PolicyViolationException("Topic should have at least 2 replication factor, received: " +
                        requestMetadata.replicationFactor());
            }
        }
    }

    @Override
    public void configure(Map<String, ?> configs) {

    }

    @Override
    public void close() throws Exception {

    }
}

