
## 如何保证消息不丢失

1. `onSend(Callable)` 使用带有回调的发送方法，如果发送失败后，listener 配合 `tries` 进行重试尝试
2. `unclean.leader.election.enable = false`，禁止落后过多的 broker被选举成为 leader。 
3. `replication.factor` 指定 leader broker 有多少个副本备份
4. `acks = all`，保证该 leader broker 下的所有 ISR 集合都能同步消息后才算消息提交
5. `min.insync.replicas` 指定 ISR 集合数量，如果小于该值，那么就算提交失败
6. 确保消费者消费完之后，再进行提交（注意接口幂等，否则可能会重复消费），同时关闭 `enable.auto.commit`，采用手动提交，避免多线程下的提交
出现问题
   

## 流程

interceptor  -> serializer -> partitioner

## TCP 连接

在创建 producer 的时候，就会创建一个 sender 线程，他会向配置的所有 `bootstrap.servers` 建立 tcp 连接。

关闭可以通过 `producer.close()` 或者是 `connections.max.idle.ms` 自动关闭 tcp 连接，默认9min，如果设置成-1，那么将会
启动 keepalive 机制，连接将一直存活。

## 幂等

`enable.idempotence` 幂等性 producer，但只能保证某个主题的一个分区不会重复消息，无法实现多个分区的幂等性。其次，仅限于单会话上，
如果重启 producer，幂等性无法保证。

## 事务

```
producer.initTransactions();

try {
    producer.beginTransaction();
    producer.send(record1);
    producer.send(record2);
    producer.commitTransaction();
} catch () {
    producer.abortTransaction();
}
```

要么全部成功，要么全部失败。consumer 读取到消息的时候，取决与参数 `isolation.level`，如果是 `read_uncommitted`
写入的消息都能被读到，而如果是 `read_commited`，那么只能看到被提交的事务性 producer 提交的消息，非事务型的也能被看到。


## 消费者组

1. group id 全局的 Consumer Group 标识
2. Consumer Group 下所有实例订阅的主题的单个分区，只能分配给组内的某个 Consumer 实例消费。

理想情况下，Consumer 实例的数量应该等于该 Group 订阅主题的分区总数。


## ReBalance

一个 Consumer Group 下所有的 Consumer 如何达成一致去分配订阅 Topic 的所有分区。

比如某个 Group 有 20个 Consumer，那么该消费者组订阅了 100个分区的 Topic，Kafka 会为每个 consumer 分配 5 个分区获取数据。

### Coordinator

Coordinator 为 Consumer Group 服务，负责为 Group 执行 ReBalance 以及提供位移管理和成员管理等。（本质上就是一个 broker）。

定位过程：
1. hashcode(group.id)
2. hash % offsetsTopicPartitionCount(50)

根据对应的值找到对应分区，然后根据分区找到分区的 leader broker。

### ReBalance 触发条件

1. 组成员发生变更。比如有新的 Consumer 加入组或者退出组，或者 Consumer 实例崩溃被踢出（线程异常退出）
2. 订阅的主题数发生变更。比如某个组订阅了 `consumer.substribe(Pattern.compile("t.*c"))`，如果这时创建了一个满足正则条件的 topic，
那么将发生重平衡。
3. 订阅的分区数发生变更。（分区数量增加）

### ReBalance 检测

一般来说 2.3 基本都是运维操作，所以发生的概率比较可控。而 1 出现的原因很有可能是消费者自身出问题（业务程序运行）

当 consumer group 完成 reBalance 之后，每个 consumer 实例都会向 coordinator 发送心跳请求，如果 coordinator 在一定时间内
`session.timeout.ms`（默认10s）无法收到心跳，那么就会开启新一轮 reBalance

* `heartbeat.interval.ms`：用于 consumer 控制心跳发送的频率，适当减小可以更快触发 rebalance，但可能会带来额外的网络带宽消耗。
* `max.poll.interval.ms`：默认5min，如果在间隔时间内无法重新发起一次新的 poll 请求，那么会主动发起离开组请求，即重新 rebalance。


### 减少 rebalance 的措施

1. 因为未能及时发送心跳，导致 coordinator 认为 consumer 下线

    * session.timeout.ms = 6s
    * heartbeat.interval.ms = 2s
    
2. consumer 消费时间过长

    * 死锁？线程阻塞了？gc？  比如 spring-kafka poll 和执行在同一个业务线程中，导致超时 poll ？ 一步一步排查
    


## 位移主题

将 Consumer 消费的位移数据作为一条 Kafka 消息，提交到 `_consumer_offsets`，会在 Kafka 集群的第一个 Consumer 启动时，
自动创建内部位移主题。默认50个分区 `offsets.topic.num.partitions`。为啥不使用 Zookeeper 呢，主要是因为强一致，导致数据每次位移提交的
时候都需要 leader 和 follower 同步，效率比较低，比较适合那些元数据变更不频繁的。

建议：关闭 `enable.auto.commit`，因为如果 consumer 没有继续消费了，在开启自动提交的情况下，会一直发送 oldOffset 到 `consumer_offset`
中，导致 Kafka 的消息会一直增加（Kafka 必须有针对位移主题的特点删除策略），而且手动提交可能会出现 __重复消费__

Kafka 提供了专门的后台线程定期（时间轮）检查待 Compact 的主题，如果满足条件的将自动删除，用于减少位移主题无限膨胀占用过多的磁盘空间。
（如果 Cleaner 线程崩溃了会导致 Kafka 异常）


