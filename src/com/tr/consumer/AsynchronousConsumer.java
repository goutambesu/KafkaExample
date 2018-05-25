package com.tr.consumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

public class AsynchronousConsumer implements Runnable {
	private final KafkaConsumer<String, String> consumer;
	private final TopicPartition topicPartition;
	private final int id;

	public AsynchronousConsumer(int id,String brokers, String groupId, TopicPartition topicPartition) {
		this.id = id;
		this.topicPartition = topicPartition;
		this.consumer = getConsumer(brokers,groupId);
	}
	private KafkaConsumer getConsumer(String brokers,String groupId) {
		Properties props = new Properties();
		props.put("bootstrap.servers", brokers);
		//props.put("group.id", groupId);
		props.put("key.deserializer", StringDeserializer.class.getName());
		props.put("value.deserializer", StringDeserializer.class.getName());
		return new KafkaConsumer<>(props);
	}
	@Override
	public void run() {
		try {
			//consumer.subscribe(topics);
			List<TopicPartition> topicPartitions = new  ArrayList<TopicPartition> ();
			topicPartitions.add(topicPartition);
			 consumer.assign(topicPartitions);
			while (true) {
				ConsumerRecords<String, String> records = consumer.poll(1000);
				
				for (ConsumerRecord<String, String> record : records) {
					Map<String, Object> data = new HashMap<>();
					data.put("partition", record.partition());
					data.put("offset", record.offset());
					data.put("value", record.value());
					data.put("consumerId", id);
					//data.put("consumerId", record.);
				System.out.println(data);
				}
				consumer.commitAsync(new OffsetCommitCallback() {
					public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets, Exception exception) {
						if (exception != null)
							System.out.println("Commit failed for offsets {}");
					}
				});
			}
		} catch (WakeupException e) {
			// ignore for shutdown
		} finally {
			consumer.close();
		}
	}

	

	public void shutdown() {
		consumer.wakeup();
	}


	class ColumnHashProducerCallback implements Callback { 
		@Override
	    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
	    	if (e != null) {
	        	e.printStackTrace(); 
	        }
	    }
	}
}

