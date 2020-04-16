
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
replica_id
```