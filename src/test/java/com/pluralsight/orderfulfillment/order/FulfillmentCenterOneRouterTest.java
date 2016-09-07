package com.pluralsight.orderfulfillment.order;

import static org.junit.Assert.*;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import com.google.gson.Gson;
import com.pluralsight.orderfulfillment.fulfillmentcenterone.service.FulfillmentCenterOneProcessor;
import com.pluralsight.orderfulfillment.fulfillmentcenterone.service.FulfillmentResponse;

/**
 * Test case for the fulfillment center one RESTful web service route. Leverages
 * Camel test support as well as Spring test support.
 * 
 * NOTE: To execute this test, you must have the ActiveMQ server and fulfillment
 * center one restful service running.
 * 
 * @author Michael Hoffman, Pluralsight
 *
 */
@RunWith(CamelSpringJUnit4ClassRunner.class)
@ContextConfiguration(
      classes = { FulfillmentCenterOneRouterTest.TestConfig.class },
      loader = CamelSpringDelegatingTestContextLoader.class)
public class FulfillmentCenterOneRouterTest {

   @Produce(uri = "direct:test")
   protected ProducerTemplate testProducer;

   @EndpointInject(uri = "mock:direct:result")
   protected MockEndpoint resultEndpoint;

   public static String fulfillmentCenter1Message =
         "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
               + "<Order xmlns=\"http://www.pluralsight.com/orderfulfillment/Order\">"
               + "<OrderType>"
               + "<FirstName>Jane</FirstName>"
               + "<LastName>Smith</LastName>"
               + "<Email>jane@somehow.com</Email>"
               + "<OrderNumber>1003</OrderNumber>"
               + "<TimeOrderPlaced>2014-10-24T12:09:21.330-05:00</TimeOrderPlaced>"
               + "<FulfillmentCenter>"
               + com.pluralsight.orderfulfillment.generated.FulfillmentCenter.FULFILLMENT_CENTER_ONE
                     .value() + "</FulfillmentCenter>" + "<OrderItems>"
               + "<ItemNumber>078-1344200444</ItemNumber>"
               + "<Price>20.00000</Price>" + "<Quantity>1</Quantity>"
               + "</OrderItems>" + "</OrderType>" + "</Order>";

   @Before
   public void setUp() throws Exception {
   }

   @After
   public void tearDown() throws Exception {
   }

   @Configuration
   public static class TestConfig extends SingleRouteCamelConfiguration {
      @Bean
      public FulfillmentCenterOneProcessor fulfillmentCenterOneProcessor() {
         return new FulfillmentCenterOneProcessor();
      }

      @Bean
      public ConnectionFactory jmsConnectionFactory() {
         return new ActiveMQConnectionFactory("tcp://localhost:61616");
      }

      @Bean(initMethod = "start", destroyMethod = "stop")
      public PooledConnectionFactory pooledConnectionFactory() {
         PooledConnectionFactory factory = new PooledConnectionFactory();
         factory.setConnectionFactory(jmsConnectionFactory());
         factory.setMaxConnections(10);
         return factory;
      }

      @Bean
      public JmsConfiguration jmsConfiguration() {
         JmsConfiguration jmsConfiguration = new JmsConfiguration();
         jmsConfiguration.setConnectionFactory(pooledConnectionFactory());
         return jmsConfiguration;
      }

      @Bean
      public ActiveMQComponent activeMq() {
         ActiveMQComponent activeMq = new ActiveMQComponent();
         activeMq.setConfiguration(jmsConfiguration());
         return activeMq;
      }

      @Bean
      @Override
      public RouteBuilder route() {
         return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
               // 1 - Route from the direct component to an ActiveMQ component
               from("direct:test").to("activemq:queue:FC1_FULFILLMENT_REQUEST");

               // 2 - Route from the ActiveMQ component through the message
               // processor for fulfillment center one to the fulfillment center
               // one restful web service, and finally, to a mock component.
               from("activemq:queue:FC1_FULFILLMENT_REQUEST")
                     .beanRef("fulfillmentCenterOneProcessor", "transformToOrderRequestMessage")
                     .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                     .to("http4://localhost:8090/services/orderFulfillment/processOrders")
                     .to("mock:direct:result");

            }
         };
      }
   }

   /**
    * Tests the route for normal, successful execution. Sends the test message
    * through and then verifies a 200 for the response code from the web
    * service.
    * 
    * @throws Exception
    */
   @Test
   public void test_success() throws Exception {
      // 1 - Mock endpoint expects one message
      resultEndpoint.expectedMessageCount(1);

      // 2 - Send the XML as the body of the message through the route
      testProducer.sendBody(fulfillmentCenter1Message);

      // 3 - Waits ten seconds to see if the expected number of messages have
      // been received
      resultEndpoint.assertIsSatisfied();

      // 4 - Get the exchanges. Our exchange should be the first.
      Exchange exchange = resultEndpoint.getExchanges().get(0);
      assertNotNull(exchange);
      Message inMessage = exchange.getIn();
      FulfillmentResponse response =
            new Gson().fromJson(inMessage.getBody(String.class),
                  FulfillmentResponse.class);
      assertEquals(200, response.getResponseCode());
   }

}
