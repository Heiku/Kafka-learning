
### 分区分配策略

Kafka 提供了消费者客户端参数 `partition.assignment.strategy` 来设置消费者与订阅主题之间的分区分配策略

#### RangeAssignor

RangeAssignor 分配策略的原理是按照 __消费者总数__ 和 __分区总数__ 进行整除运算来获得一个跨度，然后将分区按照跨度进行
平均分配，以保证分区尽可能地分配给所有地消费者。

对于每一个主题，RangeAssignor 策略会将消费者组内所有订阅这个主题地消费者按照名称地字典排序，然后为每个消费者划分固定
地分区范围，如果不够平均分配，那么字典序靠前地消费者会被多分配一个分区。

```
假设消费组中有2个消费者C0 和 C1，都订阅了主题 t0 和 t1，并且每个主题都有 4 个分区，那么一共有分区：
t0p0,t0p1,t0p2,t0p3,t1p0,t1p1,t1p2,t1p3

C0：t0p0,t0p1,t1p0,t1p1
C1：t0p2,t0p3,t1p2,t1p3

假如2个主题，每个主题只有3个分区，t0p0,t0p1,t0p2,t1p0,t1p1,t1p2

C0：t0p0,t0p1,   t1p0,t1p1
C1：t0p2,        t1p2

因为对每个主题进行分配时，靠前的主题t0就会被分配多一个分区，两个就多分配了两个

如果订阅的主题越多，额外分配的分区也会越多，这样导致了部分消费者过载的情况。
```


#### RoundRobinAssignor 

RoundRobinAssignor 分配策略的原理是将消费组内所有消费者及消费者订阅的所有主题的分区按照字典序排序，然后通过 __轮询__ 的
方式将分区依次分配给每个消费者。

```
如果同一个消费组内所有的消费者的订阅信息都是相同的，那么 RoundRobinAssignor 的分区分配将会是均匀的。
假设两个消费者 C0 和 C1，都订阅了主题 t0 和 t1，并且每个主题都有三个分区，那么所有的分区为：t0p0,t0p1,t0p2,t1p0,t1p1,t1p2

C0：t0p0,t0p2,t1p2
C1：t0p1,t1p0,t1p3

如果同一个消费组内的消费者订阅的信息是不相同的，那么在执行分区分配的时候就不是完全的轮询分配，有可能导致分区分配得不均匀。
假设消费组内有3个消费者 C0、C1、C2，它们一共订阅了 3 个主题（t0、t1、t2），这三个主题分别有 1，2，3 个分区，
即整个消费组订阅了 t0p0, t1p0,t1p1, t2p0,t2p1,t2p2 这6个分区。
具体而言，消费者C0 订阅了 t0， 消费者 C1 订阅了 t0，t1，消费者 C2 订阅了 t0、t1、t2

C0：t0p0
C1：t1p0
C2：t1p1，t2p0，t2p1，t2p2

导致了分配得不平均 
```

### StickyAssignor

StickAssignor 

