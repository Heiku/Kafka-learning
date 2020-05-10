
### 副本

LEO (log-end-offset) 标识每个分区中最后一条消息的下一个位置，分区中的每个副本都有自己的 LEO  
在 ISR 集合中最小的 LEO 即为 HW（高水位），消费者只能拉到 HW 之前的消息。

#### 失效副本

正常情况下，分区的所有副本都处于 ISR 集合中，而处于 ISR 集合之外的，就是处于同步失效或者功能失效的 __失效副本__。失效副本对应的
分区也被称为 __同步失效分区(under_replicated)__。

判断一个分区是否有副本处于同步失效的状态通过参数 `replica.lag.time.max.ms 默认10s`，当 ISR 集合中的一个 follower 副本之后 leader 副本
的时间超过此参数指定的值，则判定为同步失败。Kafka 的副本管理器会启动一个副本过期检测的定时任务，而这个定时任务，会定时检查当前时间
与副本的 lastCaughtUpTimeMs 差值是否大于参数 `replica.lag.time.max.ms`。

有两种情况一般会导致副本失效：

* follower 副本进程卡住，在一段时间内没有向 leader 副本发起同步请求，比如频繁的 Full GC
* follower 副本进程同步过慢，在一段时间内都无法赶上 leader 副本，比如 I/O 开销过大

![](/img/kafka-high-water-level.png)


#### LEO HW

副本有两个概念：本地副本（Local Replica） 和 远程副本（Remote Replica），同一个分区中的信息会存在多个 broker 节点上，
并被其上的副本管理，逻辑层面上每个 broker 节点上的多个分区有了多个副本，但只有本地副本才有对应的日志。

在同一个分区中，leader 副本所在的节点会记录所有副本的 LEO，而 follower 副本所在的节点只会记录本身的 LEO，而不会记录
其他副本的 LEO。对 HW 而言，各个副本所在的节点都只记录它本身的 HW。


#### Leader Epoch （版本号获取 LEO）

在 0.11.0.0 之前，Kafka 使用的是基于 HW 的同步机制，如果 leader 重启称为 follower 之后，根据新 leader 的 HW 进行
__日志截断（truncated）__，导致了 leader副本和 follower副本数据不一致的问题。

```
follower 会根据之前保存的 HW 进行截断，HW - LEO 数据丢失（大不了重新同步）

leader 重启之后，会根据 leader 副本的 HW 进行截断，但如果 leader 像 A 一样并不是最新的，
那截断之后的数据就真的丢失了。
```

引入 Leader Epoch 之后，需要截断数据的时候使用 leader epoch 作为参考依据而不是原本的 HW。每当 leader 变更一次，
leader epoch+1 （版本号+1）.  
同时，每个副本保存一个矢量<LeaderEpoch，StartOffset>，StartOffset 表示当前 LeaderEpoch 下写入第一条消息的偏移量。
在发生 Leader Epoch 变更时，会将矢量信息写入到每个副本对应的 Log 下的 leader-epoch-checkpoint 文件。

![](/img/kafka-leader-epoch.png)

```
在副本重启之后，会发送 OffsetForLeaderEpochRequest(leader_epoch) 发送给 leader 副本，
请求在 leader_epoch 下的 LEO，然后 follower 副本根据这个 LEO 进行消息截断。

如果 leader 副本重启了，一个 follower 成为新leader，这时候 new_Leader_epoch = leader_epoch+1，
旧 leader 按照 leader_epoch 请求同步 LEO，最后再按照 FetchRequest 同步数据。
```


### 为啥不支持读-写分离

主写从读有两个缺点：

1. 数据一致性问题

数据从主节点转到从节点必然会有一个延时的时间窗口，这个时间窗口会导致主从节点之前的数据不一致。

2. 延时问题

在 Redis 中，数据从写入主节点到同步至从节点需要经过 `网络->主节点内存->网络->从节点内存`，而在 Kafka 中
因为保证数据不丢失，采用日志记录的方式，所以会多个磁盘保存步骤 `主节点内存->主节点磁盘->从节点内存->从节点磁盘`，
虽然采用追加日志可以极大加快写进度，对于延时敏感的应用还是不适用的。

![](/img/kafka-producer-consumer-model.png)

Kafka 在对分区进行分配时，会尽量保证了分区的合理，保证了在集群中每个 broker 的读写情况是一样的。
如果开发的时候自己指定不合理的分配策略，可能会导致多个broker的读写分配负载不一致。

#### 日志同步机制

Kafka 中动态维护这一个 ISR 集合，处于 ISR 集合内的节点保持与 leader 相同的高水位（HW），只有位列其中的副本
`（unclean,leader.election.enable）为false`，才有资格被选为新的 leader。

#### 可靠性分析

* ack

对于 `ack= -1`，生产者将消息发送到 leader 副本，leader 完成写入本地日志之后还要等待 ISR 中 follower 副本 
__全部同步完成__ 才能告诉生产者已经提交成功，即使 leader 副本宕机，消息也不会丢失。

可以配合参数 `min.insync.replicas` 参数值，指定 ISR 中最小的副本数，通常副本数配置为3， `min.insync,replicas`
配置为2，这样只要有2个同步就算是 acks = -1,全部完成同步。

* leader 选举

建议采用默认的 `unclean.leader.election.enable` false，保证消息的可靠性。