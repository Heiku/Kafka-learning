
### 协调器

如果多个消费者中配置了不同的分配策略，那么以哪个为准呢？

多个消费者之间的分区是需要 __协同__ 的，这个协同的过程交由消费者 __协调器（ConsumerCoordinator）__ 和 __组协调器（GroupCoordinator）__ 
来完成，它们之间使用一套协调协议进行交互。



#### 旧版本

![](/img/kafka-zookeeper-struct.png)

旧版本中每个消费者在 __启动__ 时，都会在 `/consumers/<group>/ids` 和 `/brokers/ids` 路径上注册监听器。

* 当 `/consumers/<group>/ids` 路径下的子节点发生变化时，表示消费组中的消费者发生了变化。
* 当 `/brokers/ids` 路径下的子节点发生变化时，表示 broker 出现了增减。

这样通过 Zookeeper 提供的 __watcher__ ，每个消费者就可以监听消费组和 Kafka 集群的状态。但当触发 __再均衡__ 操作时，
一个消费组下的所有消费者会同时进行再均衡操作，而消费者之间并不知道彼此操作的结果，导致 Kafka 工作在一个不正确的状态。
同时这种严重依赖 Zookeeper 集群的做法有两个比较严重的问题；

1. 羊群效应（Herd Effect）：所谓的羊群效应是指 Zookeeper 中一个被监听的节点变化，大量的 Watcher 通知到发送到客户端，
导致通知期间其他操作延迟，也有可能出现类似死锁的问题

2. 脑裂问题（Split Brain）：消费者进行再均衡操作时，每个消费者都与 Zookeeper 进行通信以判断消费者或 broker 变化的情况，
由于 zookeeper 的特性，可能导致同一时刻各个消费者获取的状态不一致，这样将导致异常问题发生。

#### 新版本

新版中将全部消费组分为多个子集，每个消费组的子集在服务端对应一个 GroupCoordinator 对其进行管理，GroupCoordinator 在 Kafka
服务端中用于管理消费组的组件。而消费者客户端中的 ConsumerCoordinator 组件负责与 GroupCoordinator 进行交互。

ConsumerCoordinator 与 GroupCoordinator 负责执行消费者 __再均衡__ 操作，目前有一下情况会触发：

1. 有新的消费者加入消费者组
2. 当消费者宕机下线时。消费者并不一定真正下线，而是因为例如 长时间GC、网络延迟导致消费者长时间未向 GroupCoordinator 发送心跳情况等，
GroupCoordinator 会认为消费者已经下线
3. 有消费者主动退出消费组（LeaveGroupRequest）。比如客户端调用 `unsubscrible()` 取消某个主题的订阅
4. 消费组所对应的 GroupCoordinator 节点发生了变更
5. 消费组所订阅的任一主题或主题的分区数量发生变化

* Find_Coordinator

消费者需要确定它所属的消费组对应的 GroupCoordinator 所在的 broker(leader副本)，并与该 broker 建立对应的网络连接。
主要通过向集群中的某个节点 broker 发送 `FindCoordinatorRequest(coordinator_key, coordinator_type)`，然后根据 groupId 
计算对应的 `_consumer_offsets` 内部主题中的分区编号。

```
Utils.abs(groupId.hascode) % groupMetadataTopicPartitionCount
```

找到对应的 `_consumer_offsets` 中的分区后，再寻找此分区 leader 副本所在的 broker 节点，该 broker 节点为对应的 Coordinator 节点。


* Join_Group

这个阶段消费者会向 GroupCoordinator 发送 `JoinGroupRequest` 请求，并处理响应

```
JoinGroupRequest 中包含了多个域：

group_id:  消费组的id
session_timeout:  session.timeout.ms(100000) GroupCoordinator 超过指定的时间内没有收到心跳报文则认为此消费者已经下线
rabalance_timeout:  max.poll.interval.ms(300000) 当消费组再平衡时，GroupCoordinator 等待各个消费者重新加入的最长等待时间
member_id:  GroupCoordinator 分配给消费者的 id 标识
protocol_type:  消费组实现的协议

group_protocols[]: 标识多个分区分配策略
    protocol_name: 
    protocol_metadata
```