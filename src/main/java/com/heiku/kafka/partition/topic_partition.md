
### topic && partition

主题和分区都是逻辑上地概念，分区可以有一至多个 __副本__，每个副本对应一个 __日志文件__，每个日志文件对应一至多个 __日志分段__，
每个日志分段还可以细分为 索引文件、日志存储文件和快照文件等。

#### topic、partition、replica、log

![](/img/topic_partition_replication_log.png)

主题和分区都是提供给上层用户的抽象，在副本层面/Log层面才有实际物理上的存在。

同一个分区中的多个副本必须分布在不同的 broker 中，这样才能提供有效的数据冗余。

```
kafka-topics.sh --zookeeper localhost:2181/kafka --create --topic topic-create --partition 4 --replication-factor 2

对于每一个分区，会创建 2 个副本，所以一共会有 2 * 4 = 8 个副本
在 broker 的情况下，会按照 2, 3, 3 的方式分配副本，使每个 broker 中的副本数目达到最优
```




