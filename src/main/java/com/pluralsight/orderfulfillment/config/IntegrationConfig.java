package com.pluralsight.orderfulfillment.config;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
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
	
	/**
	 * <Order xmlns="http://www.pluralsight.com/orderfullfilment/Order">
	 * <OrderType><FulfillmentCenter>ABCFulfillmentCenter</FulfillmentCenter>
	 * @return
	 */
	@Bean
	public RouteBuilder fullfillmentCenterContentBasedRouter() {
		return new RouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				Namespaces namespace = new Namespaces("o", "http://www.pluralsight.com/orderfulfillment/Order");
				
				//Send from ORDER_ITEM_PROCESSING queue to the correct
				//fulfillment center queue.
				from("activemq:queue:ORDER_ITEM_PROCESSING")
					.choice()
					.when()
					.xpath("/o:Order/o:OrderType/o:FulfillmentCenter = '"
							+ com.pluralsight.orderfulfillment.generated.FulfillmentCenter.ABC_FULFILLMENT_CENTER.value()
							+ "'", namespace)
					.to("activemq:queue:ABC_FULLFILMENT_REQUEST")
					
					.when()
					.xpath("/o:Order/o:OrderType/o:FulfillmentCenter = '"
							+ com.pluralsight.orderfulfillment.generated.FulfillmentCenter.FULFILLMENT_CENTER_ONE.value()
							+ "'", namespace)
					.to("activemq:queue:FC1_FULLFILMENT_REQUEST")
					
					.otherwise()
					.to("activemq:queue:ERROR_FULLFILMENT_REQUEST");				
			}
		};
	}
	
	@Bean
	public RouteBuilder fulfillmentCenterOneRouter(){
		return new RouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				from("activemq:queue:FC1_FULLFILMENT_REQUEST")
					.beanRef("fulfillmentCenterOneProcessor", "transformToOrderRequestMessage")
					.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
					.to("http4://localhost:8090/services/orderFulfillment/processOrders");
			}
		};
	}
}
