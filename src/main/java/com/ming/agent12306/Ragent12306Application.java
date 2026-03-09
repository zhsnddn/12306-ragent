package com.ming.agent12306;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan({"com.ming.agent12306.memory.mapper", "com.ming.agent12306.knowledge.mapper"})
public class Ragent12306Application {

    public static void main(String[] args) {
        SpringApplication.run(Ragent12306Application.class, args);
    }

}
