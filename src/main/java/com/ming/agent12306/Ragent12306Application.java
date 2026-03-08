package com.ming.agent12306;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Ragent12306Application {

    public static void main(String[] args) {
        SpringApplication.run(Ragent12306Application.class, args);
    }

}
