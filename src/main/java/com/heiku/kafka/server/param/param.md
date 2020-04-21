
### 参数

#### broker.id

`broker.id` 记录 Kafka 集群中每个 broker 的唯一id，在 Zookeeper 中，通过在 `/brokers/ids` 中记录 brokerId 的健康状态。

#### bootstrap.servers

`bootstrap.servers` 指定的我们将要连接的 Kafka 集群的 broker 地址列表。

1. 客户端 KafkaProducer 与 `bootstrap.servers` 参数所指定的 Server 连接，并发送 MetadataRequest 请求集群的元数据信息
2. Server 在收到 MetadataRequest 请求之后，返回 MetadataResponse 给 KafkaProducer
3.