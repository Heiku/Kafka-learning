
### 概念

__AR__: 分区中的所有副本统称为 AR (Assigned Reaplicas)  
__LSR__: 所有与 leader 副本保持 __一定程度同步__ 的副本 (包括 leader 副本在内) 组成 ISR (In-Sync Replicas)  
__OSR__: 与 leader 副本同步 __滞后过多__ 的副本 (不包括 leader 副本)，组成 OSR (Out-Sync Replicas)

在正常情况下，所有的 follower 副本都应该与 leader 副本保持一定的同步，即 AR = ISR, OSR = null  

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
（broker不均衡率 = 非优先副本的 leader 个数 / 分区总数），默认 10%，如果超过则自动执行优先副本的选举动作以求分区平衡。

#### 分区重分配

当要对集群的一个节点进行有计划的下线操作时，为了保证分区及副本的合理分配，可以通过 kafka-reassign-partition.sh 操作，
通过 --generate 生成重分配的方案，--execute 执行方案的方式完成重分区

当集群中新增 broker 节点是，只有新创建的主题分区才有可能被分配到这个节点上，而之前的主题分区并不会自动分配到新加入的节点，
因为在它们被创建时还没有这个新节点的记录，这样导致了新节点的负载和原先节点的负载之前严重不平衡，需要重分区

* 原理

分区重分配的基本原理是先通过 __控制器__ 为每个分区添加新副本（增加副本因子），新的副本将从分区的 leader 副本那里复制所有的数据。
根据分区的大小不同，复制过程可能花费一些时间，以为数据是通过网络复制到新副本上。在复制完成之后，控制器将旧副本从副本清单里清除。

#### 分区数与吞吐量

适量提高分区数可以提高吞吐量，但当分区数提至某一个阈值时，吞吐量将下降。

分区数会占用文件描述符，而一个进程所能支配的文件描述符是有限的，也就是文件句柄的开销。当超过句柄的限制如（65535）将导致
进程退出（Kafka关闭）。

同时，分区数还会影响系统的可用性。如果分区数过大，当集群中的某个 broker 节点宕机，那么就会有大量的分区需要同时进行 leader 角色切换，
切换的过程会消耗一笔可观的时间，并且这个时间窗口内的分区也会变得不可用。

最后，在关闭的时候不仅会增加日志清理，而且在被删除的时候也会耗费更多的时间，导致启动或者关闭的时候维护日志的时间过长。