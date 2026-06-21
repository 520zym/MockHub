package com.mockhub;

import com.mockhub.common.config.DataProperties;
import com.mockhub.common.config.LogRetainProperties;
import com.mockhub.common.config.MockCorsProperties;
import com.mockhub.common.config.MockFileProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({DataProperties.class, LogRetainProperties.class, MockCorsProperties.class,
        MockFileProperties.class})
public class MockHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockHubApplication.class, args);
    }
}
