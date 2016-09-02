package com.pluralsight.orderfulfillment.config;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.javaconfig.CamelConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pmackiewicz on 2016-09-02.
 */
@Configuration
public class IntegrationConfig extends CamelConfiguration{

    @Inject
    private Environment environment;

    @Override
    public List<RouteBuilder> routes() {

        List<RouteBuilder> routeList = new ArrayList<>();

        RouteBuilder routeBuilder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("file://" + environment.getProperty("order.fulfillment.center.1.outbound.folder") + "?noop=true")
                .to("file://" + environment.getProperty("order.fulfillment.center.1.outbound.folder") + "/test");

            }
        };
        routeList.add(routeBuilder);

        return routeList;
    }
}
