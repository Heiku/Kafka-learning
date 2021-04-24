
### Producer

#### client

![生产者客户端整体架构图](/img/kafka-producer-client-all.png)

整个 producer client 由两个线程协调运行，这两个线程分别为 __主线程__ 和 __Sender 线程__。在主线程中由 KafkaProducer 创建消息，
然后通过可能的 interceptor、serializer、partitioner 的作用之后缓存到消息累加器 （RecordAccumulator，也称为消息收集器）中。
Sender 线程负责从 RecordAccumulator 中获取消息并将其发送到 Kafka 中。

##### RecordAccumulator

RecordAccumulator 的主要用来 __缓存消息__ 以便 Sender 线程可以批量发送，从而减少网络传输的资源消耗以提升性能。  
`buffer.memory 配置，默认为 32MB
`

主线程发送过来的消息都会被追加到 RecordAccumulator 的某个 __双端列表 （Deque）__ 中，在 RecordAccumulator 的内部为每个 __分区__
都维护了一个双端队列，队列的内容为 ProducerBatch，即 Deque<ProducerBatch>。消息写入缓存时，追加到 deque 的尾部，Sender 线程读取
消息时，从双端队列的头部读取。

```
这里的 ProducerBatch 是一个消息批次，可以包含一个或多个 ProducerRecord，ProdcuerRecord 被包含在 ProducerBatch中，
这样可以使字节的使用更加紧凑。同时将多个较小的 ProducerRecord 拼凑成一个较大的 ProducerBatch 也可以减少网络请求的次数以提升整体的吞吐量。

通过 batch.size 可以配置缓存的 ProducerRecord 信息大小。
```

##### 发送

Sender 线程从 RecordAccumulator 中获取缓存的消息之后，会进一步将原本的 <partition, Dequeue<ProducerBatch>> 的保存形式
转变成 <Node, List<ProducerBatch>> 的形式，其中 Node 标识 Kafka 集群的 __broker 节点__。

```
对于网络连接来说，producer client 与 具体的 broker 节点建立连接，也就是向具体的 broker 节点发送消息，而不关心消息属于那个一分区。

对于 KafkaProducer 的应用逻辑而言，我们只关注那个 partition 中发送哪些消息，所以在这里需要做一个应用逻辑层到 网路I/O 层的转换。 
```

在转换成 <Node, List<ProducerBatch>> 的形式之后，Sender 还会进一步封装成 <Node, Request> 的形式，这样就可以将请求发送各个节点。
请求request 在从 Sender 线程发往 Kafka 之前还会保存在 __InFlightRequests__ 中，InFlightRequests 保存对象的拘役形式为 
Map<NodeId, Deque<Request>>，它的主要作用是缓存了已经发出去但还没有接受到响应的请求。

```
max.in.flight.requests.connection 5 缓存5个还未响应的请求，
如果导致限制值，将无法向该连接发送请求，说明这个 Node 节点负载较大或者网络连接有问题。
```

通过 InFlightRequests 多个节点对应缓存的请求，我们获取到负载最小的节点 __leastLoadedNode__，选择这个节点进行数据发送可以避免
网络拥塞等异常而造成整体进度的下降。

#### 元信息

KafkaProducer 将消息追加到指定主题 topic 的某个分区所对应的 leader 副本之前，首先需要知道主题的分区数量，然后经过计算得出目标分区，
之后 KafkaProdcer 需要知道目标分区的 leader 副本所在的 broker 节点的地址、端口等信息才能建立连接，最终才能将消息发送到 Kafka，
而这一过程中的所需要的消息都属于 __元信息__。

当需要更新 __元信息__ 时，会先挑选出 leastLoadedNode，然后向这个 Node 发送 MetadataRequest 请求获取具体的元数据信息。
更新操作由 Sender 线程发起，在创建完 MetadataRequst 之后同样会存入 InFlightRequests，步骤类似。

__元数据__ 的更新通过 Sender 线程完成，当主线程需要读取元信息时，通过 `synchronized` 和 `final` 字段保障最新。


## onSend 阻塞

就算使用异步 onSend() future，也还是有可能出现阻塞的：

* 发送的时候会根据 `batch.size` 缓存在 BufferByte 中，一旦 bufferPool 中没有足够的 Bytebuffer 的时候，就会阻塞发送，
知道 bufferPool 中有足够的 bufferByte 被分配出来
  
* 第一次发送的时候，是需要向主题的 leader 获取 MetaData（节点信息、ISR列表等等），只有再获取到 metadata 之后，才会将
消息累加在缓冲区中。
  

## batch.size

如果消息大小比 `batch.size` 大，则不会从 free 中循环获取已分配号的内存块，而是重新创建一个新的 byteBuffer，
并且该 byteBuffer 不会被归还到缓冲池中（jvm gc 回收），如果此时 nonPooledAvailableMemory 比消息体还要小，
还会将 free 中空闲的内存块销毁（jvm gc回收），以便缓冲池中与足够的内存空间提供给用户申请，这些动作将导致频繁 gc。