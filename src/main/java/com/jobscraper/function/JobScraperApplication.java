package com.jobscraper.function;

import com.jobscraper.configuration.BlobStorageProperties;
import com.jobscraper.configuration.CosmosProperties;
import com.jobscraper.configuration.KafkaProperties;
import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.configuration.ResendProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

//This class defines the root of the Spring application context.
@SpringBootApplication(scanBasePackages = "com.jobscraper")
@EnableConfigurationProperties({
        JobScraperProperties.class,
        KafkaProperties.class,
        BlobStorageProperties.class,
        CosmosProperties.class,
        ResendProperties.class,
        RawStorageProperties.class
})
public class JobScraperApplication {
}
