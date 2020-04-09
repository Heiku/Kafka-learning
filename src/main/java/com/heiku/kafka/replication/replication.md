
### replication

分区 partition 使用多副本机制来提升可靠性，但只有 leader 副本对外提供 __读写服务__，而 follower 副本只负责在内部进行
消息的同步。如果一个分区的 leader 副本不可用，那么就意味着 __整个分区__ 变得不可用，此时就需要 Kafka 从剩余的 follower 
副本中挑选一个新的 leader 副本继续对外提供服务。

```
[root@nodel kafka 2 . 11 - 2 . 0 . 0 ]# bin/kafka- topics. sh --zookeeper localhost : 2181/ 
kafka --describe --topic topic - partitions 

Topic :topic-partitions PartitionCount : 3 ReplicationFactor : 3 Con figs : 
Topic : topic - partitions Partition: 0 Leader: 1 Replicas: 1 , 2 , 0 Isr: 1 , 2 , 0 
Topic : topic - partitions Partition: 1 Leader: 2 Replicas: 2 , 0 , 1 Isr: 2 , 0 , 1 
Topic : topic - partitions Partition: 2 Leader: 0 Replicas: 0 , 1 , 2 Isr: 0 , 1 , 2
```

当 leader 不可用时，从副本 follower 中选出一个 leader，当 oldLeader 恢复之后，会重新加入到副本 follower

#### 优先副本 (preferred replica)

优先副本指的是 AR 集合列表（Replicas）中的 __第一个副本__，即 分区 0 的优先副本为 1。

* 自动分区平衡

分区平衡：所有分区下的 leader, follower 均衡分布

```
auto.leader.rebalance.enable=true，默认开启
```

Kafka 的控制器会启动一个定时任务，这个 ScheduleTask 会轮询所有的 broker 节点，计算每个 broker 节点的分区不均衡率  
（broker不均衡率 = 非优先副本的 leader 个数 / 分区总数）

