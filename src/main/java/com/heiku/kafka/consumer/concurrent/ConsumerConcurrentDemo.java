package com.heiku.kafka.consumer.concurrent;

import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.heiku.kafka.consumer.KafkaConsumerAnalysis.getConsumer;

/**
 * KafkaProducer 是线程安全的，然而 KafkaConsumer 是非线程安全的，
 * 内部定义了 acquire()， 用来检测当前是否只有一个线程在操作，如果存在其他线程在操作，将抛出 ConcurrentModificationException
 *
 * 所以多线程进行消费的话，需要为每一额线程创建一个 consumer，即 消费线程。
 *
 * 一个消费线程可以消费多个分区，所有的消费线程都隶属于同一个消费组。当消费线程的个数大于分区数时，就会又部分线程处于空闲状态。
 * （线程数最大为分区数）
 *
 *
 *
 * @Author: Heiku
 * @Date: 2020/4/8
 */
public class ConsumerConcurrentDemo {
    public static void main(String[] args) {
        KafkaConsumer<String, String> consumer = getConsumer();

    }


    // currentThread holds the threadId of the current thread accessing KafkaConsumer
    // and is used to prevent multi-threaded access
    private static final long NO_CURRENT_THREAD = -1L;
    private final AtomicLong currentThread = new AtomicLong(NO_CURRENT_THREAD);
    // refcount is used to allow reentrant access by the thread who has acquired currentThread
    private final AtomicInteger refcount = new AtomicInteger(0);

    // 保证了 一个consumer 只能被一个线程使用
    private void acquire() {
        long threadId = Thread.currentThread().getId();
        if (threadId != currentThread.get() && !currentThread.compareAndSet(NO_CURRENT_THREAD, threadId))
            throw new ConcurrentModificationException("KafkaConsumer is not safe for multi-threaded access");
        refcount.incrementAndGet();
    }

    private void release() {
        if (refcount.decrementAndGet() == 0)
            currentThread.set(NO_CURRENT_THREAD);
    }
}
