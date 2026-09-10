package com.jobscraper.function;

import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;

//This tells the Azure Functions Java runtime to obtain Function instances from Spring.
public class SpringFunctionInstanceInjector implements FunctionInstanceInjector {

    @Override
    public <T> T getInstance(Class<T> functionClass) {
        return SpringApplicationContext.getBean(functionClass);
    }
}