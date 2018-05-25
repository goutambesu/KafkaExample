package com.tr.consumer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import kafka.cluster.BrokerEndPoint;
import kafka.javaapi.PartitionMetadata;
import kafka.javaapi.TopicMetadata;
import kafka.javaapi.TopicMetadataRequest;
import kafka.javaapi.consumer.SimpleConsumer;

public class InHousePartitionConsumer {
private String  topic;
private String[] brokers ;
private int portToConsumer; 
/*it is a optional field , if we run all brokers in the same system then all brokers will be running with same 
 * hostname but with diffirent ports,so we can provide port number which running consumer will be consuming
 *  messages from those partitions which belong to the broker which is running on that port.
 *  For example if there to broker running on localhost:9091 and localhost:9092
 *  now you created  a topic with 4 partitions , and broker1(localhost:9091) --> partition 0 and partition 2
 *  and broker1(localhost:9092) --> partition 1 and partition 3
 *  Now if we want to consumer only messages which are from broker1 then we can provide portToConsume=9091
*/
	public static void main(String[] args) throws Exception {
		
		String topic = "testWith4";
		String brokersStr = "localhost:9092,localhost:9091";
		String[] brokers = brokersStr.split(",");

		new InHousePartitionConsumer(topic,brokers,9092).run();
		}

	public InHousePartitionConsumer(String topic, String[] brokers) {
		this.topic = topic;
		this.brokers = brokers;
	//"localhost:9092,localhost:9091"; should be in this format , need to handle validation
	}
	
	public InHousePartitionConsumer(String topic, String[] brokers, int portToConsumer) {
		this.topic = topic;
		this.brokers = brokers;
		this.portToConsumer = portToConsumer;
	}

	public void run() throws Exception {		
		


		// find the meta data about the topic and partition we are interested in
		//
		// Find the leader broker from metadata

		 Map<Integer,BrokerEndPoint> partitionToBrokerMap = findLeader(brokers, topic);
		if ( partitionToBrokerMap.isEmpty()) {
			System.out.println("Please check and iputs:topic name and brokers name");
			return;
		}
		InetAddress 	ip = InetAddress.getLocalHost();
        String hostname = ip.getHostName();
     //   System.out.println(hostname);
       List<TopicPartition> topicPartitionList = new ArrayList<TopicPartition>();
		for (Map.Entry<Integer,BrokerEndPoint> partitionToBrokerEntry : partitionToBrokerMap.entrySet())
		{
			int partitionId =partitionToBrokerEntry.getKey();
			BrokerEndPoint leader =  partitionToBrokerEntry.getValue();
		  //  System.out.println(partitionId + "-->" + leader);
		   if( leader.host().equalsIgnoreCase(hostname)) {
			   if(portToConsumer!= 0 && (leader.port()!= portToConsumer)) {
				   System.out.println("provide portToConsumer("+portToConsumer+") and leader port("+leader.port()+") not matched, will be trying next broker");
				   continue;
			    }
			   topicPartitionList.add(new TopicPartition(topic, partitionId));
		   }
		    
		     
		}		
		if(topicPartitionList.isEmpty()) {
			System.out.println("No partition found in this broker for the provided topic");
			return;
		}else {
			System.out.println("Going to read only below partitions"+topicPartitionList);
			String brokerstr = String.join(",", brokers);
			String groupId= topic+"_"+hostname; // e.g: topic1_PCADMIN
			if(portToConsumer!= 0) {
				groupId = groupId + "_"+portToConsumer;// e.g: topic1_PCADMIN_9091
			}
			
			  int numConsumers = topicPartitionList.size();
			  ExecutorService executor = Executors.newFixedThreadPool(numConsumers);
			  
			  final List<AsynchronousConsumer> consumers = new ArrayList<>();
			  for (int i = 0; i < numConsumers; i++) {
				  AsynchronousConsumer consumer = new AsynchronousConsumer(i, brokerstr,groupId, topicPartitionList.get(i));
			    consumers.add(consumer);
			    executor.submit(consumer);
			  }
	
			  Runtime.getRuntime().addShutdownHook(new Thread() {
			    @Override
			    public void run() {
			      for (AsynchronousConsumer consumer : consumers) {
			        consumer.shutdown();
			      } 
			      executor.shutdown();
			      try {
			        executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
			      } catch (InterruptedException e) {
			      }
			    }
			  });
		}
	}

	private Map<Integer,BrokerEndPoint> findLeader(String[] a_seedBrokers, String a_topic) {
		 Map<Integer,BrokerEndPoint> partitionToBrokerMap = new HashMap<Integer,BrokerEndPoint>();
		 
		 
		 loop: for (String seed : a_seedBrokers) {
			SimpleConsumer consumer = null;
			try {
				String[] brokerInfo = seed.split(":");
				consumer = new SimpleConsumer(brokerInfo[0], Integer.parseInt(brokerInfo[1]), 100000, 64 * 1024, "leaderLookup");
				List<String> topics = Collections.singletonList(a_topic);
				TopicMetadataRequest req = new TopicMetadataRequest(topics);
				kafka.javaapi.TopicMetadataResponse resp = consumer.send(req);
				
				// Received the topic metadata
				List<TopicMetadata> metaData = resp.topicsMetadata();
				for (TopicMetadata item : metaData) {
					for (PartitionMetadata part : item.partitionsMetadata()) {
						int id =  part.leader().id();
						String leadBroker = part.leader().host();
						System.out.println(part);
						partitionToBrokerMap.put(part.partitionId(), part.leader());
					}
					break loop;
				}
			} catch (Exception e) {
				System.out.println("Error communicating with Broker [" + seed + "] to find Leader for [" + a_topic
						+ ", "  + "] Reason: " + e);
			} finally {
				if (consumer != null)
					consumer.close();
			}
		}	
		return partitionToBrokerMap;
	}
	private KafkaConsumer getConsumer(String brokers,String groupId) {
		Properties props = new Properties();
		props.put("bootstrap.servers", "localhost:9092");
		props.put("group.id", groupId);
		props.put("key.deserializer", StringDeserializer.class.getName());
		props.put("value.deserializer", StringDeserializer.class.getName());
		return new KafkaConsumer<>(props);
	}
}
