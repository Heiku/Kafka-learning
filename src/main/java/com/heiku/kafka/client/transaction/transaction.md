
### 事务

#### 消息传输保障

消息传输保障有 3 个级别，如下：

1. at most once: 最多一次，消息可能会丢失，但绝对不会重复传输
2. at least once: 最少一次，消息绝不会丢失，但可能会重复传输
3. exactly once: 恰好一次，每条消息肯定会被传输一次且仅传输一次

* 生产者

生产者向 Kafka 发送消息时，一旦消息被成功提交到日志文件，由于 __多副本机制的存在，消息不会丢失__。但如果生产者发送消息
到 Kafka 之后，遇到网络问题而造成了通信中断，那么生成者 producer 无法判断该消息是否已经提交。虽然 Kafka 无法确定网络
故障期间发生了什么，但生产者可以进行多次重试确保消息已经写入 Kafka，这个重试的过程可能会造成消息的 __重复写入__，
所以这里 Kafka 提供的消息传输保障为 at least。

* 消费者

消费者处理消息和提交消费位移的顺序在很大程度上决定了消费者提供哪一种消息传输保障。

如果消费者在拉取完消息之后，应用逻辑先处理消息后提交消费位移，那么消息处理完之后在消费位移提交之前发生宕机，待它重新上线
之后，会从上一次位移提交的位置拉取，这样就出现了重复消费。因为有部分消息已经处理过了只是还没来得及提交消费位移，
此时对应 at least once。

如果消费者在拉完消息之后，应用逻辑先提交消费位移后进行消息处理，那么在位移提交之后且消息处理完成之前消费者宕机了，
待它重新上线之后，会从已经提交的唯一处开始重新消费，但之前尚有部分消息未进行消费，如此就会发生消息丢失，对应 at most once

#### 幂等

幂等：对接口的多次调用所产生的结果和调用一次是一致的（避免了生产者重复写入的问题）

幂等功能的开启需要修改生成者客户端参数 `enable.idempotence = true`（默认为false）

* 实现

Kafka 引入了 producer_id（PID）和 序列号（sequence_number）的概念，分别对应 v2 日志格式中 RecordBatch 的 `producer_id` 和 
`first_sequence` 这两个字段。每个生产者实例在初始化的时候都会被分配一个 PID，对于每个 PID 消息发送到每一个分区都有对应的
序列号，序列号从 0 开始递增。生产者每个送一条消息，就会将 <PID, 分区> 对应的序列号的值 + 1。

broker 在内存中为每一对 <PID, 分区>维护了一个序列号。只有当消息的序列号（SN_new） = broker 中的序列号（SN_old）+ 1 时，
broker 才会接收它。

    * 如果 SN_new < SN_old + 1，说明消息被重复写入，可以直接丢弃  
    * 如果 SN_new > SN_old + 1，说明消息可能存在丢失，生产者会抛出将对应的 OutOfOrderSequenceException

#### 事务

幂等性不能跨多个分区工作，而事务可以弥补这个缺陷。__事务可以保证对多个分区写入操作的原子性。__ 操作的原子性是指多个操作
要么全部成功，要么全部失败，不存在部分成功、部分失败的可能。

实现事务，应用程序需要指定唯一的 `transactionId`，同时要求生产者开启幂等等特性

```
    // 设置幂等，事务
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "transaction_id");
```

transaction_id 与 PID 一一对应，transaction_id 由用户指定，而 PD 由 Kafka 内部分配。  
为了保证新的生产者启动后具有相同的 transactionId 的旧生产者能够失效，每个生产者通过 transactionId 获取 PID 的同时，
还会获取一个单调递增的 producer epoch（RecordBatch `producer epoch`），如果同一个 transactionId 启动两个生产者，就会报错。

* 生产者

通过事务，Kafka 可以保证跨生产者会话的消息幂等发送，以及跨生产者会话的事务恢复。前者表示具有 transactionId 的新生产者
实例被创建且工作的时候，旧的且拥有相同 transactionId 的生产者实例将不再工作。后者指当某个生产者实例宕机后，新的生产者
实例可以保证任何未完成的旧事务要么被提交（Commit），要么被中止（Abort），保证了新的生产者实例可以正常工作。

* 消费者

Kafka 并不能保证自己提交的事务中的所有信息都能够被消费

    * 对采用日志压缩策略的主题而言，事务中的某些消息可能被清理（相同 key 的消息，后写入的消息会覆盖前面写入的消息）
    
    * 事务中消息可能分布在同一个分区的多个日志分段中（LogSegment）中，当老的日志段被删除时，对应的消息可能会丢失
    
    * 消费者可以通过 seek() 方法访问任意 offset 的消息，从而可能遗漏事务中的部分消息
    
    * 消费者在消费时可能没有分配到事务内的所有分区，这样就不能读取事务中的所有消息
    

在消费者端有个参数配置为： `isolation.level` 默认值为 "read_uncommitted", 意思是消费端应用可以看到（消费到）未提交的事务，
对于已提交的事务也是可见的。如果设置成 "read_committed"，表示消费端应用不可以看到尚未提交的事务内的消息。

```
比如生产者开启事务并向某个分区发送了消息 msg1, msg2, msg3，在执行 commitTrasnaction() / abortTransaction() 之前，
设置了 ”read_committed“ 的消费者端应用是消费不到这些消息。

不过在 KafkaConsumer 内部会缓存这些消息，直到生产者执行 commitTransaction() 之后才会将这些消息推送给消费端应用。
反之，如果生产者执行了 abortTransaction() 之后，KafkaConsumer 会将这些缓存的消息丢弃而不推送给消费者端应用。
```


在日志文件中，除了记录普通的消息，还有一种消息专门标志一个事务的结束，它就是 __控制消息（ControlBatch）__。  
消息控制一共有两种类型，__COMMIT__ 和 __ABORT__，分别用来表示事务已经成功提交或已经成功终止。Kafka 通过这个控制消息
来判断对应的事务是提交了还是被中止了，然后结合参数 `isolation.level` 配置的隔离级别来决定是否将相应的消息返回给消费端应用。

![](/img/kafka-transaction-controlBatch.png)

