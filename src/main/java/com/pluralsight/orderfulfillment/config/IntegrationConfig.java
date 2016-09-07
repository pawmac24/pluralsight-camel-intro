package com.pluralsight.orderfulfillment.config;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.spring.javaconfig.CamelConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.pluralsight.orderfulfillment.order.OrderStatus;

import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.sql.DataSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pmackiewicz on 2016-09-02.
 */
@Configuration
public class IntegrationConfig extends CamelConfiguration {

	@Inject
	private Environment environment;

	@Inject
	private DataSource dataSource;

	@Bean
	public ConnectionFactory jmsConnectionFactory() {
		return new ActiveMQConnectionFactory(environment.getProperty("activemq.broker.url"));
	}
	
	@Bean(initMethod = "start", destroyMethod = "stop")
	public PooledConnectionFactory pooledConnectionFactory() {
		PooledConnectionFactory factory = new PooledConnectionFactory();
		factory.setConnectionFactory(jmsConnectionFactory());
		factory.setMaxConnections(Integer.parseInt(environment.getProperty("pooledConnectionFactory.maxConnections")));
		return factory;
	}
	
	@Bean
	public JmsConfiguration jmsConfiguration(){
		JmsConfiguration jmsConfiguration = new JmsConfiguration();
		jmsConfiguration.setConnectionFactory(pooledConnectionFactory());
		return jmsConfiguration;
	}
	
	@Bean
	public ActiveMQComponent activeMQ(){
		ActiveMQComponent activeMQ = new ActiveMQComponent();
		activeMQ.setConfiguration(jmsConfiguration());
		return activeMQ;
	}
	
	@Bean
	public SqlComponent sql() {
		SqlComponent sqlComponent = new SqlComponent();
		sqlComponent.setDataSource(dataSource);
		return sqlComponent;
	}

	@Bean
	public RouteBuilder newWebsiteOrderRoute() {
		return new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from(
						"sql:"
								+ "select id from orders.\"order\" where status = '"
								+ OrderStatus.NEW.getCode()
								+ "'"
								+ "?"
								+ "consumer.onConsume=update orders.\"order\" set status = '"
								+ OrderStatus.PROCESSING.getCode() + "'"
								+ " where id = :#id")
						.beanRef("orderItemMessageTranslator", "transformToOrderItemMessage")
						.to("activemq:queue:ORDER_ITEM_PROCESSING");
			}
		};

	}
}
