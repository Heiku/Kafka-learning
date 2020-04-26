
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


##### 事务协调器

![](/img/kafka-consume-transform-produce.png)

Kafka 引入事务协调器（TransactionCoordinator）负责处理事务，类比于（GroupCoordinator）。每一个生产者都会被指派一个特定的
TransactionCoordinator，所有的事务逻辑包括分派PID等都是由 TransactionCoordinator 负责实施。TransactionCoordinator 会将
事务状态持久化到内部主题 `_transaction_state` 中。

1. 查找 TransactionCoordinator

TransactionCoordinator 负责分配 PID 和管理事务，因此生产者需要先找出对应的 TransactionCoordinator 的 broker 节点。
查找的方式和 GroupCoordinator 节点一样，通过 `FindCoordinatorRequest` 请求实现，但请求参数 `coordinator_type` 由 0 变为 1。

Kafka 收到了 `FindCoordinatorRequest` 之后，根据 `coordinator_key`（transaction_id）找对应的 TransactionCoordinator 节点。
类比于消费者根据 `groupId` 找 GroupCoordinator。之后返回 node_id、host 和 port。

```
Utils.abs(transactionalId.hashcode) % transactionTopicPartitionCount

transactionTopicPartitionCount 由 broker 配置参数 transaction.state.log.num.partitions， 默认50
```

2. 获取 PID

凡是生产者开启了 __幂等性__ 功能，都需要为其分配一个 PID。生产者获取 PID 通过 `InitProducerIdRequest` 实现

```
InitProducerIdRequest

    transaction_id:
    transaction_timeout_ms:
```

保存 PID

生产者的 `InitProducerRequest` 请求会发送给 TransactionCoordinator。如果未开启 __事务__ 而只开启 __幂等__ 特性，那么 
`InitProducerRequest` 可以发送给任意的 broker。

当 TransactionCoordinator 第一次收到含 transactionId 的 `InitProducerRequest` 请求时，会把 transactionId 和对应的 PID
以消息（事务日志消息）的形式保存到主题 `_transaction_state` 中，保证了 <TransactionId, PID> 的对应关系被持久化，从而
保证了 TransactionCoordinator 即使宕机该对应的关系也不会丢失。

`InitProducerRequest` 除了获取 PID 之外，还会增加 producer_epoch，同时恢复（commit）或中止（abort）之前的生产者未完成的事务。

___transaction_state__ 的内部格式如下：
```
key: 
    version:
    transaction_id

value:
    version:
    producer_id:
    producer_epoch: 如果有相同的 PID 但 producer_epoch 小于该 producer_epoch 的其他生产者新开启的事务将被拒绝。
    transaction_status:
    transaction_partition[]:
        topic:
        partition_id:[]
    
    transaction_entry_timestamp:
    transaction_start_timestamp:
``` 

3. 开启事务

    KafkaProducer 的 `beginTransaction()` 方法可以开启一个事务，调用之后，生产者本地会 __标记__ 已经开启了一个新的事务，
    但只有在生产者 __发送__ 第一条消息之后，TransactionCoordinator 才会认为该事务已经开启。

4. Consume-Transform-Produce

    - AddPartitionToTxnRequest

    当生产者给一个新的分区（TopicPartition）发送数据前，需要先向 TransactionCoordinator 发送 `AddPartitionToTxnRequest` 请求，
    TransactionCoordinator 会将 <TransactionId, TopicPartition> 的对应关系存储在主题 `_transaction_state` 中，以方便后续
    为每个分区设置 __COMMIT__ 或 __ABORT__ 标记。
    
    如果创建的分区对应事务的第一个分区，那么 TransactionCoordinator 会启动对该事务的计时。
    
    ```
    AddPartitionToTxnRequest
    
        transaction_id:
        producer_id:
        producer_epoch:
    
        topics[]:
            topic:
            partitions[]:
    ```

    - ProduceRequest

    生产者通过 `ProduceRequest` 发送消息（ProduceBatch）到用户自定义的主题中，和普通消息不同的是，ProduceBatch 中会包含实质
    的 PID、produce_epoch 和 sequence number。

    - AddOffsetsToTxnRequest（只保存消费组对应的内部分区）

    通过 KafkaProducer 的 `sendOffsetsToTransaction(offsets, groupId)` 可以在一个事务里批次处理消息的消费和发送，该方法会向 TransactionCoordinator 
    节点发送 `AddOffsetsToTxnRequest`，TransactionCoordinator 收到请求之后通过 groupId 退出消费组在 `_consumer_offsets` 中的分区，
    之后 TransactionCoordinator 将这个分区保存在 `_transaction_state` 中。

    ```
    AddOffsetsToTxnRequest
    
        transaction_id:
        producer_id:
        producer_epoch:
        group_id:
    ```

    - TxnOffsetCommitRequest（保存分区中的消费位移）

    `TxnOffsetCommitRequest` 也是 `sendOffsetsToTransaction()` 中的一部分，在处理完 `AddOffsetsToTxnRequest` 之后，
    生产者会发送 `TxnOffsetsCommitRequest` 到 GroupCoordinator，从而将本次事务中包含的消费位移信息 offsets 存储到主题 
    _consumer_offsets 内部主题中。


5. 提交或者中止事务

    一旦数据被写入成功，就可以调用 KafkaProducer 的 `commitTransaction()` 或者 `abortTransaction()` 方法结束当前事务。

    - EndTxnRequest
    
    无论调用 `commitTransaction()` 还是 `abortTransaction()`，都会向 TransactionCoordinator 发送 `EndTxnRequest`, 以此通知
    它提交（Commit）或是中止（Abort）事务。 

    ```
    EndTxnRequest
    
        transaction_id:
        producer_id:
        producer_epoch:
    
        transaction_result(boolean): 0 = Abort, 1 = Commit
    ```
    
    TransactionCoordinator 在收到 `EndTxnRequest` 执行以下操作：
    (1) 将 `PREPARE_COMMIT` 或 `PREPARE_ABORT` 消息写入主题 _transaction_state
    (2) 通过 `WriteTxnMarkersRequest` 将 COMMIT 或 ABORT 信息写入用户所使用的普通主题和 _consumer_offsets
    (3) 将 `COMPLETE_COMMIT` 或 `COMPLETE_ABORT` 信息写入内部主题 _transaction_state
    
    - WriteTxnMarkerRequest
    
    TransactionCoordinator 会向事务（所涉及）中各个分区的 leader 节点发送 `WriteTxnMarkerRequest`，当节点收到这个请求后，
    会在相应的分区中写入控制消息（ControlBatch）。控制消息用来标识事务的终结，和普通消息一样存储在日志文件中。
    
    RecordBatch 中的 `attributes` 字段第5位标识当前消息是否处于事务中，如果是事务中为1，否则为0。字段第6位标识当前消息
    是否是控制消息，如果是为1，否则为0。
    
    一个 RecordBatch 会为了压缩节省空间包含多个 ProducerRecord，ControlBatch 是个特殊的 RecordBatch，用于标识，
    内部只有一个 Record，Record 中的 `timestamp delta` 和 `offset data` 都为 0。
    
    - 写入 COMPLETE_COMMIT 和 COMPLETE_ABORT
    
    TransactionCoordinator 将最终的 `COMPLETE_COMMIT` 和 `COMPLETE_ABORT` 信息写入主题 _transaction_state 表明当前事务已经
    结束，此时可以删除主题 _transaction_state 中所有关于该事务的信息。由于主题 _transaction_state 采用的是日志清理策略为
    日志压缩，所以这里的删除只需将对应的消息设置为墓碑消息即可。
    
    