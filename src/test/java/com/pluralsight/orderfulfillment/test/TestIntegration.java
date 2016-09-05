package com.pluralsight.orderfulfillment.test;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class TestIntegration {

	@Bean
	public DataSource dataSource() {
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
		dataSource.setUrl("jdbc:derby:memory:orders;create=true");
		return dataSource;
	}

	@Bean
	public JdbcTemplate jdbcTemplate() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource());
		return jdbc;
	}

	@Bean(initMethod = "create", destroyMethod = "destroy")
	public DerbyDatabaseBean derbyDatabaseBean() {
		DerbyDatabaseBean derby = new DerbyDatabaseBean();
		derby.setJdbcTemplate(jdbcTemplate());
		return derby;
	}
}
