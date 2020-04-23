
### consumer_offsets

Kafka 的内部主题 `consumer_offsets` 保存了消费者的 __消费位移__ 和 __消费组的消费者的元数据__。
该主题通常在集群中第一次有消费者消费信息的时候创建，而分区数通过 `offsets.topic.num.partition` 参数设置，默认50。

客户端通过发送请求 `OffsetCommitRequest` 实现消费位移的提交

```
OffsetCommitRequest:

    group_id:
    memeber_id:
    generation_id:
    retention_time:  表示当前提交的消费位移所能保留的时长，默认根据 broker 配置的 `offsets.retention.minutes` 1天
                     超过这个时间后的消费位移的信息就会被删除。
    topics[]:
        topic:
        partitions[]:
            partition:
            offset:
            metadatas:  不使用将设为空，长度不得超过 offset.metadata.max.bytes 参数

            这里对应着手动提交位移 Map<TopicPartition, OffsetAndMetadata> offsets
            
            OffsetAndMetadata(long offset, String metadata)



OffsetCommitResponse:   返回各个分区消息位移提交是否成功
    throttle_time_ms:
    responses:
        topic:
        partition_responses:
            partition:
            error_code:
```

__consumer_offset__ 内部存储格式：

```
key:
    version:
    group:
    topic:
    partition:

value:
    version:
    offset:
    metadata:
    commit_timestamp:
    expire_timestamp:
```

当对应的消费位移已经过期时，Kafka 中的定时任务 `delete-expired-group-metadata` 负责清理过期的消费位移，执行的周期由参数 
`rentention.check.interval.ms` 控制，默认为 10分钟，清理完之后分区的消费位移为 null



