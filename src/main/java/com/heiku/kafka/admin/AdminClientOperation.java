package com.heiku.kafka.admin;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * create topic by AdminClient
 *
 * @Author: Heiku
 * @Date: 2020/4/9
 */
public class AdminClientOperation {
    private static AdminClient client;

    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-admin";

    static {
        initClient();
    }

    private static void initClient() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        client = AdminClient.create(props);
    }

    /**
     * create Topic
     */
    public static void createTopic(){
        NewTopic newTopic = new NewTopic(topic, 4, (short) 1);
        CreateTopicsResult result = client.createTopics(Collections.singletonList(newTopic));
        try {
            // block util all topic created
            result.all().get();
        }catch (InterruptedException | ExecutionException e){
            e.printStackTrace();
        }
        client.close();
    }

    /**
     * get all topic info
     */
    public static void listTopic(){
        ListTopicsResult result = client.listTopics();
        try {
            List<TopicListing> topicListings = new ArrayList<>(result.listings().get());
            topicListings.forEach(topicListing -> {
                System.out.println(topicListing.toString());
            });

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * describe topic config
     */
    public static void describeTopicConfig(){
        // --topic topic-demo
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        DescribeConfigsResult result = client.describeConfigs(Collections.singletonList(resource));
        try {
            Config config = result.values().get(resource).get();
            System.out.println(config.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * alter config
     */
    public static void alterConfig(){
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        // config param
        ConfigEntry entry = new ConfigEntry("cleanup.policy", "compact");
        Map<ConfigResource, Collection<AlterConfigOp>> configMap = new HashMap<>();
        AlterConfigOp op = new AlterConfigOp(entry, AlterConfigOp.OpType.SET);
        configMap.put(resource, Collections.singleton(op));
        AlterConfigsResult result = client.incrementalAlterConfigs(configMap);
        try {
            result.all().get();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * increase partition num
     */
    public static void increasePartition(){
        NewPartitions newPartitions = NewPartitions.increaseTo(5);
        Map<String, NewPartitions> newPartitionsMap = new HashMap<>();
        newPartitionsMap.put(topic, newPartitions);

        CreatePartitionsResult result = client.createPartitions(newPartitionsMap);
        try {
            result.all().get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // createTopic();
        // listTopic();
        // describeTopicConfig();
        // alterConfig();
        increasePartition();
    }
}
