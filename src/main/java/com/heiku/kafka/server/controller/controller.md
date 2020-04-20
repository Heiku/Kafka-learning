
### 控制器

Kafka 集群中会有一个或者多个 broker，其中有一个 broker 会被选举为控制器（Kafka Controller），它负责管理整个集群中
所有分区和副本的状态。当某个分区的 leader 副本出现故障时，由控制器负责该分区选举新的 leader 副本。当检测到某个分区的 
ISR 集合发生变化时，由控制器负责通知所有 broker 更新其元数据信息。

![](/img/kafka-controller-design.png)


### 优雅关闭 Kafka

1. 使用 Java `jps` 命令或者 Linux 中的 `ps`，查看 Kafka 的服务进程 PIDS
2. 使用 `kill -s TERM $PIDS` 或 `kill -15 $PIDS` 的方式关闭进程，而不是使用 `kil -9`

这样的操作会在 Kafka 进程捕获终止信号的时候执行关闭钩子 `kafka-shutdown-hock` 的内容，其中除了正常关闭一些必要的资源，
还会执行一个控制关闭（ControlledShutdown）的动作。

ControlledShutdown 的方式关闭有两个优点：

1. 可以让消息完全同步到磁盘上，在服务上下线时不需要进行日志的恢复操作
2. ControllerShutdown 在关闭服务之前，会对其上的 leader 副本进行迁移，这样就可以减少分区的不可用时间。


### 分区 leader 的选举

分区 leader 副本的选举由控制器 controller 负责具体实施。当创建分区（创建主题或者增加分区）或分区上线（原 leader 下线，
分区需要选举一个新的 leader 上线来对外提供服务）的时候都需要执行 leader 的选举动作，对应的的选举策略为 
OfflinePartitionLeaderElectionStrategy。这种策略的基本思路是按照 AR 集合中副本的顺序查找第一个存活的副本，并且这个
副本在 ISR 集合中（已经同步）。一个分区的 AR 集合在分配的时候被指定，并且只要不发生 __重分配__ 的情况，集合内部副本的
顺序是保持不变的，而分区的 ISR 集合中的副本的顺序可能会改变。


