package com.jobscraper.function;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/*
    The static context is initialized once per Azure Functions worker process.
    If Azure scales out to multiple worker instances, each worker process will have its own Spring context. That is expected:
    Worker instance 1 -> one Spring context
    Worker instance 2 -> one Spring context
    Worker instance 3 -> one Spring context
    The context should not be created inside the Function method because that would initialize Spring repeatedly during execution.
 */
public final class SpringApplicationContext {

    private static final ConfigurableApplicationContext CONTEXT =
            new SpringApplicationBuilder(JobScraperApplication.class)
                    .web(WebApplicationType.NONE)
                    .logStartupInfo(false)
                    .run();

    private SpringApplicationContext() {
    }

    public static <T> T getBean(Class<T> beanType) {
        return CONTEXT.getBean(beanType);
    }
}