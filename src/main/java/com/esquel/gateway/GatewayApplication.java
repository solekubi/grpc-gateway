package com.esquel.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "com.esquel.gateway")
public class GatewayApplication {

	public static ConfigurableApplicationContext ac ;

	public static void main(String[] args) {
		ac = SpringApplication.run(GatewayApplication.class, args);
	}

}
