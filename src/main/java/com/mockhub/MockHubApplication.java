package com.mockhub;

import com.mockhub.common.config.DataProperties;
import com.mockhub.common.config.LogRetainProperties;
import com.mockhub.common.config.MockCorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({DataProperties.class, LogRetainProperties.class, MockCorsProperties.class})
public class MockHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockHubApplication.class, args);
    }
}
