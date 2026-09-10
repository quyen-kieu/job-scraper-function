package com.jobscraper.function;

import com.jobscraper.application.command.DailyScrapeCommandProducer;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyScrapeCommandProducerFunctionTest {

    @Test
    void timerFunctionDelegatesToApplicationService() {
        DailyScrapeCommandProducer producer = mock(DailyScrapeCommandProducer.class);
        when(producer.publishDailyCommands()).thenReturn(2);

        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());

        DailyScrapeCommandProducerFunction function = new DailyScrapeCommandProducerFunction(producer);

        function.run("{}", context);

        verify(producer, times(1)).publishDailyCommands();
    }
}

