package com.jobscraper.function;

import com.jobscraper.application.command.DailyScrapeCommandProducer;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import org.springframework.stereotype.Component;

@Component
public class DailyScrapeCommandProducerFunction {

    private final DailyScrapeCommandProducer commandProducer;

    public DailyScrapeCommandProducerFunction(DailyScrapeCommandProducer commandProducer) {
        this.commandProducer = commandProducer;
    }

    @FunctionName("DailyScrapeCommandProducer")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "%JOB_SCRAPER_DAILY_CRON%") String timerInfo,
            final ExecutionContext context) {
        context.getLogger().info("Daily scrape timer fired: " + timerInfo);
        int commandsPublished = commandProducer.publishDailyCommands();
        context.getLogger().info("Published " + commandsPublished + " scrape commands for this run.");
    }
}

