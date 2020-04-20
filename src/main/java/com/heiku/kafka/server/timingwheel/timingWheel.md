
### 时间轮

![](/img/ksfka-timing-wheel.png)

Kafka 中的时间轮（TimingWheel） 是一个存储定时任务的环形队列。底层采用数组实现，数组中的每个元素都可以存放一个定时任务列表
（TimerTaskList）。TimerTaskList 是一个环形的双向链表，链表中的每一项表示的都是定时任务项（TimerTaskEntry），其中封装了
真正的定时任务（TimerTask）。

时间轮由多个时间格组成，每个时间格代表当前时间轮的基本时间跨度（tickMs）。时间轮的个数是固定的（wheelSize），整个时间轮的
总体时间跨度（interval = tickMs * wheelSize）。时间轮用表盘指针（currentTime），用来表示当前所处的时间，当 currentTime 
指向某个时间格时，说明正在处理当前时间格中的任务。

