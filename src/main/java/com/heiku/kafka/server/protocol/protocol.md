
### 协议设计

AbstractRequest

```
request = requestHeader + requestBody
RequestHeader = api_key + api_verison + correlation_id + client_id

api_key: API 标识，如 PRODUCE、FETCH 等标识
api_version: API 版本   
correlation_id: 由客户端指定的一个数字来唯一标识这次请求的id，在服务端处理完请求之后会将 id 写回 response
client_id: 客户端id

```

AbstractResponse

```
response = responseHeader + responseBody
ResponseHeader = correlation_id
```

在 AbstractRequest/AbstractResponse 中，数据都将被封装在 Struct 中，每一个 Schema 对应一个 Object[]，以存放数据。  
也就是说这个 struct 存放着 filed -> byte[]


##### ProduceRequest/ProduceResponse

![](/img/produce-request-struct.png)

* ProduceRequest 中的 field 包括：

```
transactional_id:   事务id
acks: 客户端参数 acks
timeout: 超时时间

topic_data[]: 代表 ProducerRequest 中所要发送的数据集合。以主题名分类，主题中再以分区分类
    topic(string):  主题名称
    data[]:         主题对应的数据
            partition(int32):   分区编号
            record_sets:        分区对应的数据 
```

注意：由于分区中的消息是顺序追加的，所以客户端会按照分区顺序写入 record_sets，这样发往服务端的数据就是分区有序的，
避免了服务端多余的排序转换操作，提升了整体的性能。

![](/img/produce-response-struct.png)

* ProduceResponse field:

```
throttle_time_ms(int32): 如果超过了配额（quota）限制则需要延迟该请求的处理时间。
responses[]:  ProduceResponse 中返回的数据集合。同样按照主题分区的粒度进行划分。
        topic(string): 主题名称
        parition_responses[]:  主题中所有分区的响应
                partition(int32):   分区编号
                error_code(int16):  错误码  
                
                base_offset(int64):  消息集的起始偏移量
                log_append_time(int64):  消息写入 broker 端的时间
                log_start_offset(int64):  所在分区的起始偏移量
```

#### FetchRequest/FetchResponse

![](/img/fetch-reqeust-struct.png)

* FetchRequest field: 

```
replica_id(int32):  用来指定副本的 brokerId，这个域是用于 follower 副本向leader 副本同步信息的时候使用的，普通客户端为-1
max_wait_time(int32):  消费者客户端参数 fetch.max.wait.ms
min_bytes(int32):  消费者客户端参数 fetch.min.bytes
max_bytes(int32): 消费者客户端参数 fetch.max.bytes
isolation_level(int8):  消费者客户端参数 isolation_level (默认 read_uncommited, read_commited)
session_id(int32):  fetch session id
epoch(int32):  fetch session 元数据，配合 session_id

topics[]:  拉取的主题信息 
    topic(string): 主题名称
    partitions[]:  分区信息
        partition(int32):  分区编号 
        fetch_offset(int64):  指定从分区的哪个位置开始读取信息。
                              如果是 follower 副本发起的请求，那么当前域为 follower 副本的 LEO
        log_start_offset(int64):  用于 follower 副本发起的 FetchRequest 请求，用于指明分区的起始偏移量。
        max_bytes(int32):  和消费者客户端参数 max.partition.fetch.bytes 对应

forgotton_topics_data[]:  指定从 fetch session 中指定要去拉取信息
    topic(string):  主题信息   
    partitions[]:  分区编号集合
```

在 FetchRequest 中，如果要拉取某个分区的信息，就需要指定详细的拉取信息`partition, fetch_offset, log_start_offset, max_bytes`
这4个具体值，总共占据 4+8+8+4 = 24B。如果存在1000个分区，那么在网络频繁交互的 FetchRequest 中就会有固定 1000 * 24 = 24KB
的数据在传动，这时候就可以通过 fetch session 存储这部分数据，减少带宽。

这时，引入了 `session_id、epoch、forgotton_topics_data` 等域，通过 session_id 和 epoch 确定一条拉取链路的 fetch session，
当 session 变动时，填充 topics[] 补充请求数据，否则使用默认缓存的 session 请求数据。如果需要从当前 fetch session 中
取消某些分区的拉取订阅，则可以使用 `forgotton_topics_data`


![](/img/fetch-response-struct.png)
