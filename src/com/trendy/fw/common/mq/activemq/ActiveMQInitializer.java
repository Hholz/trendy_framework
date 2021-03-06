package com.trendy.fw.common.mq.activemq;

import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trendy.fw.common.mq.MQListenerConfigBean;
import com.trendy.fw.common.mq.MQObjectPoolConfigBean;
import com.trendy.fw.common.mq.MQReceiverConfigBean;
import com.trendy.fw.common.mq.MQSenderConfigBean;
import com.trendy.fw.common.util.JsonKit;

public class ActiveMQInitializer {
	private static Logger log = LoggerFactory.getLogger(ActiveMQObjectFactory.class);

	// 配置文件路径
	private static String CONFIG_PATH = "/config/activemq.xml";

	// 配置信息
	private static List<ActiveMQConfigBean> configList = new ArrayList<ActiveMQConfigBean>(100);
	private static Map<String, ActiveMQConfigBean> configMap = new HashMap<String, ActiveMQConfigBean>(100);

	static {
		init();
	}

	private static void init() {
		initConfigList();
	}

	/**
	 * 读取配置文件内容到configList
	 */
	private static void initConfigList() {
		try {
			InputStreamReader in = new InputStreamReader(ActiveMQObjectFactory.class.getResourceAsStream(CONFIG_PATH));
			SAXReader reader = new SAXReader();
			Document document = reader.read(in);
			Element root = document.getRootElement();
			Iterator<Element> iter = root.elementIterator("mq_broker");

			while (iter.hasNext()) {
				ActiveMQConfigBean bean = new ActiveMQConfigBean();
				Element config = iter.next();

				bean.setBrokerId(config.elementText("broker_id"));
				bean.setBrokerUrl(config.elementText("broker_url"));
				bean.setUserName(config.elementText("user_name"));
				bean.setPassword(config.elementText("password"));

				MQSenderConfigBean senderConfigBean = new MQSenderConfigBean();
				Element senderConfig = config.element("sender");
				senderConfigBean.setTransacted(Boolean.valueOf(senderConfig.elementText("transacted")));
				senderConfigBean.setAcknowledgeMode(Integer.valueOf(senderConfig.elementText("acknowledge_mode")));
				senderConfigBean.setDeliveryMode(Integer.valueOf(senderConfig.elementText("delivery_mode")));
				bean.setSenderConfig(senderConfigBean);

				MQReceiverConfigBean receiverConfigBean = new MQReceiverConfigBean();
				Element receiverConfig = config.element("receiver");
				receiverConfigBean.setTransacted(Boolean.valueOf(receiverConfig.elementText("transacted")));
				receiverConfigBean.setAcknowledgeMode(Integer.valueOf(receiverConfig.elementText("acknowledge_mode")));
				bean.setReceiverConfig(receiverConfigBean);

				List<MQListenerConfigBean> listenerConfigList = new ArrayList<MQListenerConfigBean>();
				Element receiverListenerConfig = config.element("receiver_listeners");
				Iterator<Element> iterReceiverListener = receiverListenerConfig.elementIterator("receiver_listener");
				while (iterReceiverListener.hasNext()) {
					MQListenerConfigBean listenerConfigBean = new MQListenerConfigBean();
					Element elementListener = iterReceiverListener.next();
					listenerConfigBean.setQueueName(elementListener.elementText("queue_name"));
					listenerConfigBean.setStartup(Boolean.valueOf(elementListener.elementText("is_startup")));
					listenerConfigBean.setReceiverClass(elementListener.elementText("receiver_class"));
					listenerConfigList.add(listenerConfigBean);
				}
				bean.setListenerConfigList(listenerConfigList);

				MQObjectPoolConfigBean objectPoolConfigBean = new MQObjectPoolConfigBean();
				Element objectPoolConfig = config.element("pool");
				objectPoolConfigBean.setMaxTotal(Integer.parseInt(objectPoolConfig.elementText("max_total")));
				objectPoolConfigBean.setBlockWhenExhausted(Boolean.parseBoolean(objectPoolConfig
						.elementText("block_when_exhausted")));
				objectPoolConfigBean.setMaxWaitMillis(Long.parseLong(objectPoolConfig.elementText("max_wait_millis")));
				objectPoolConfigBean.setNumTestsPerEvictionRun(Integer.parseInt(objectPoolConfig
						.elementText("num_tests_per_eviction_run")));
				objectPoolConfigBean.setTestOnBorrow(Boolean.parseBoolean(objectPoolConfig
						.elementText("test_on_borrow")));
				objectPoolConfigBean.setTestOnReturn(Boolean.parseBoolean(objectPoolConfig
						.elementText("test_on_return")));
				objectPoolConfigBean.setTestWhileIdle(Boolean.parseBoolean(objectPoolConfig
						.elementText("test_while_idle")));
				objectPoolConfigBean.setTimeBetweenEvictionRunsMillis(Long.parseLong(objectPoolConfig
						.elementText("time_between_eviction_runs_millis")));
				objectPoolConfigBean.setMinEvictableIdleTimeMillis(Long.parseLong(objectPoolConfig
						.elementText("min_evictable_idle_time_millis")));
				bean.setObjectPoolConfig(objectPoolConfigBean);

				configList.add(bean);
				configMap.put(bean.getBrokerId(), bean);
			}
			in.close();
			log.info("[ActiveMQ配置]加载：{}", JsonKit.toJson(configList));
		} catch (Exception e) {
			log.error("[ActiveMQ配置]加载ActiveMQ配置文件异常：", e);
		}
	}

	/**
	 * 初始化接收者监听器列表
	 */
	public void initReceiverListenerList() {
		for (ActiveMQConfigBean bean : configList) {
			for (MQListenerConfigBean listenerConfigBean : bean.getListenerConfigList()) {
				if (listenerConfigBean.isStartup()) {
					try {
						Class<?> clazz = Class.forName(listenerConfigBean.getReceiverClass());
						Constructor<?> constructor = clazz.getDeclaredConstructor(new Class[] { String.class });
						constructor.setAccessible(true);
						ActiveMQReceiver receiver = (ActiveMQReceiver) constructor.newInstance(new Object[] { bean
								.getBrokerId() });
						receiver.receiveBylistener(listenerConfigBean.getQueueName());
					} catch (Exception e) {
						log.error("ActiveMQ error：", e);
					}
				}
			}
		}
	}

	/**
	 * 获取指定中间件的配置
	 * 
	 * @param brokerId
	 *            中间件ID
	 * @return
	 */
	public ActiveMQConfigBean getActiveMQConfig(String brokerId) {
		return configMap.get(brokerId);
	}

	public List<ActiveMQConfigBean> getActiveMQConfigList() {
		return configList;
	}
}
