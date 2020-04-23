
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

##### Find_Coordinator

消费者需要确定它所属的消费组对应的 GroupCoordinator 所在的 broker(leader副本)，并与该 broker 建立对应的网络连接。
主要通过向集群中的某个节点 broker 发送 `FindCoordinatorRequest(coordinator_key, coordinator_type)`，然后根据 groupId 
计算对应的 `_consumer_offsets` 内部主题中的分区编号。

```
Utils.abs(groupId.hascode) % groupMetadataTopicPartitionCount
```

找到对应的 `_consumer_offsets` 中的分区后，再寻找此分区 leader 副本所在的 broker 节点，该 broker 节点为对应的 Coordinator 节点。


##### Join_Group

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

group_protocols[] 的长度取决于客户端参数 partition.assignment.strategy  的配置类型，protocol_name 对应 PartionAssignor 
接口的 name() 


protocol_meta: 
    version
    topics[]: 主题列表
    user_data: 
```

consumer 在发送 `JoinGroupRequest` 请求之后会阻塞等待 Kafka 服务端的响应。服务端在收到 `JoinGroupRequest` 请求后会
交由 GroupCoordinator 处理。GroupCoordinator 会对请求进行合法性校验，例如 `group_id` 是否为空，当前的 broker 节点是否是
请求的消费组对应的组协调器、`rebalance_timeout` 是否在合理的范围之内。如果没有执行 `member_id`，则由服务端生成一个。

* 选举消费组的 leader
 
GroupCoordinator 需要为消费组内的消费者挑选出一个消费组的 leader，如果消费组内没有 leader，那么加入消费组的第一个消费者
即为当前消费组的 leader。如果消费组内 leader 因为某种情况退出，那么重新在 map 中获取一个消费者成为 leader。
 
* 选举分区分配策略 
 
每个消费者都可以设置自己的分区分配策略，在 Coordinator 中会根据各个消费者呈报上来的分配策略从而选出消费者中支持最多的策略。

1. 收集各个消费者支持的所有分配策略，组成候选集 candidates
2. 每个消费者从候选集 candidates 中找出第一个自身支持的策略，为这个策略投一票
3. 计算候选集中各个策略的选票数，选票数最多的策略即为当前消费组的分配策略。

如果有消费者不支持最终的分配策略，将会抛出 `IllegalArgumentException` 异常

```
JoinGroupResponse
    error_code: 
    generation_id: 标识当前消费组的年代信息，避免受到过期请求的影响
    group_protoocol: 最终的分配策略
    leader_id: 
    member_id: 
    members[]:  成员信息只会向 leader 发送
        member_id:
        member_metadata:
```

Kafka 把分区分配的交给客户端，自身并不参与具体的分配细节，这样使得以后分区分配的策略发生了变更，也只需重启消费端的应用
即可，而不需要重启服务端。

##### Sync_Group

在 __Join_Group__ 中，已经选举出具体的分区分配策略，然后 leader 消费者将通过 GroupCoordinator “中间人” 来负责转发同步
分配方案。在 __Sync_Group__ 阶段中，各个消费者将会向 GroupCoordinator 发送 `SyncGroupRequest` 请求同步分配方案。

```
SyncGroupRequest: 
    group_id:
    generation_id:
    member_id:

    group_assignment[]: 各个消费者对应的具体分配方案
        
        member_id:
        member_assignment:  具体消费者对应的方案 
            version:
            user_data: 

            topic_partition: 
                topic: 
                partitions[]:
                    partition 
        
``` 

GroupCoordinator 会对 `SyncGroupRequest` 请求做合法性校验，然后从 leader 消费者发过来的分配方案提取出来，连同整个消费组的
元信息一起存入 Kafka 中的 `_consumer_offsets` 主题中，最后发送响应给各个消费者以提供各个消费者各自的分配方案。

```
SyncGroupResponse
    throttle_time_ms:
    error_code:  
    member_assigment:
```

当消费者受到所属的分配方案之后，会调用 PartitionAssignor 中的 `onAssignment()`，在调用 ConsumerRebalanceListener 中的 
`onPartitionAssigned()` 方法。之后开启心跳任务，消费者定期向服务端的 GroupCoordinator 发送 `HeartbeatRequest` 确认彼此在线。


* 消费组元信息数据

Kafka 的内部主题 `_consumer_offsets` 保存了客户端提交的 __消费位移__ 和 __消费组的元数据（GroupMetadata）__。

```
GroupMetadata：

    key:   version、group
    value:
        protocol_type: 消费组的实现协议
        generation: 标识当前消费组的年代信息，避免收到过期请求的影响
        protocol: 消费组选取的分区分配策略
        leader: 消费组的 leader 消费者的名称
        members[]: 消费组中各个消费者的信息
            member_id:
            client_id:
            client_host:
            rebalance_timeout:
            session_timeout:
            substription:   订阅信息
            assignment:     分配信息
``` 

* HeartBeat

在这个阶段时，消费组中的所有消费者就处在正常工作的状态了。在正式消费之前，消费者需要确定拉取消息的起始地址，
消费者可以通过发送 `OffsetFetchRequest` 从 GroupCoordinator 到内部主题 `_consumer_offsets` 获取上次提交的消费位移。

心跳线程是一个独立的线程，可以在轮询消息的空挡发送心跳。如果消费者长时间未发送心跳，则整个会话被判定为过期。
消费者的心跳间隔时间由参数 `heartbeat.interval.ms` 指定，默认3s，这个参数不得超过 `session.timeout.ms` 配置值的 1/3,
以控制重新平衡的预期时间。

如果一个消费者发生崩溃，并停止读取消息，GroupCoordinator 会等待一段时间 `session.timeout.ms`，确认这个消费者死亡之后才会
触发再均衡。

`max.poll.interval.ms` 用来指定使用消费者组管理 `poll()` 方法之间的最大延迟，也就是消费者在获取更多消息之前可以空闲
的时间量的上限。如果超时时间满之前没有调用 `poll()` （被认为宕机了），则消费者被视为失败，并且分区将重新平衡，以便分区重新
分配给其他成员。

除了这种检测的被动退出之外，还有通过 `unsubstrible()` 发送 `LeaveGroupRequest` 主动退出消费组。


