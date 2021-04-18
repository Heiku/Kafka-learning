
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

### producer

在创建 producer 的时候，就会创建一个 sender 线程，他会向配置的所有 `bootstrap.servers` 建立 tcp 连接。

关闭可以通过 `producer.close()` 或者是 `connections.max.idle.ms` 自动关闭 tcp 连接，默认9min，如果设置成-1，那么将会
启动 keepalive 机制，连接将一直存活。

### consumer

对于 consumer 来说，只会在第一次 poll 的时候进行连接的创建。

对于 consumer，会先发送 findCoordinator 给集群中的任意一个 broker（连接数最少的），然后得到 coordinator 所在的 broker 后，
就可以进行获取组方案（根据组方案再向分配的分区所在的 leader broker 进行建立 tcp 连接），位移提交，加入组等操作。

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
    
### 流程解析

kafka 消费者组状态：

* empty：组内没有任何成员
* dead：组内没有任何成员，同时组的元数据信息已经被协调者端删除
* prepareingRebalance：消费组准备开启重平衡
* completingRebalance：消费组所有成员已经加入，各个成员正在等待分配方案（block状态）
* stable：稳定状态，即重平衡已经完成

当有新 consumer 加入消费者组的时候，会向 coordinator 发送 `JoiGroupReq` 请求，coordinator 接受到之后，会向该消费者组中的 leader
（默认是第一个加入该 group 的 consumer）发送通知消息，由 leader consumer 生成分区的分配方案。同时 coordinator 会变更当前状态为 
__重平衡__，所以这时其他消费者都会向 coordinator 发送 `syncGroupReq` 请求，定时轮训当前消费者组的分配策略。

当 leader consumer 生成分配策略之后，会发送一份给 coordinator，这时候，coordinator 就可以向其他 consumer 回复 syncGroup 请求，
告知他们自己被分配的 topicPartition 是哪些。

[消费者组流程解析](https://time.geekbang.org/column/article/111226)




## 位移主题

将 Consumer 消费的位移数据作为一条 Kafka 消息，提交到 `_consumer_offsets`，会在 Kafka 集群的第一个 Consumer 启动时，
自动创建内部位移主题。默认50个分区 `offsets.topic.num.partitions`。为啥不使用 Zookeeper 呢，主要是因为强一致，导致数据每次位移提交的
时候都需要 leader 和 follower 同步，效率比较低，比较适合那些元数据变更不频繁的。

建议：关闭 `enable.auto.commit`，因为如果 consumer 没有继续消费了，在开启自动提交的情况下，会一直发送 oldOffset 到 `consumer_offset`
中，导致 Kafka 的消息会一直增加（Kafka 必须有针对位移主题的特点删除策略），而且手动提交可能会出现 __重复消费__

Kafka 提供了专门的后台线程定期（时间轮）检查待 Compact 的主题，如果满足条件的将自动删除，用于减少位移主题无限膨胀占用过多的磁盘空间。
（如果 Cleaner 线程崩溃了会导致 Kafka 异常）


## CommitFailedException

消费者组开启了 rebalance 过程，并且将要提交位移的分区分配给了另一个消费者实例，出现的原因通常是两次调用 poll 的间隔时间超过了
`max.poll.interval.ms`。

* 适当增大 `max.poll.interval.ms`
* 减少一次 poll 的消息数量，`max.poll.records`


## 副本机制

采用 "拉取模式"，follower replica 会主动向 leader 拉取日志，并写入自己提交的日志中，从而实现与领导者副本饿同步。

follower 只进行备份（主备模式）：

1. "read-your-writes"：写入的消息能够即使看到
2. 方便实现单调读：比如 F1、F2同步的消息进度是不一样的，如果一个消费者先从F1拉取，再向F2获取消息，那么可能出现消息不一致的情况。


## ISR 

in-sync replica 包括了 leader 副本（某些情况下，只有 leader 副本），判断的标准是根据 `replica.lag.time.max.ms`（默认10s）

如果连 leader 都不在 ISR 中，那么说明需要重新选举一个 leader，根据参数将 `unclean.leader.election.enable` 判断是否能允许 unclean
领导者选举，如果开启会导致消息丢失，如果不开启，可能会导致分区一直无法选出 leader，即停止对外提供服务，失去了高可用性。


## LEO

LEO（log end offset），标识当前副本日志下一条写入的 offset，分区 ISR 中的每个副本都会维护自己的 LEO（不管是 leader 自己处理写，还是 
follower 同步偏移，都会更新自己本地的 LEO），而 ISR 集合中的最小的那个 LEO 被称为 HW（high watermark），消费者 consumer 只能读到
HW 标识的 offset。

### controller

broker 启动的时候，会向 zookeeper 创建 /controller 节点，第一个创建成功的节点的 broker 会被指定为控制器 controller。

1. 主题管理（创建、删除、增加分区）
2. 分区重分配
3. preferred 领导者选举
4. 集群成员管理（新增 broker、broker主动关闭、broker 宕机）
5. 数据服务

