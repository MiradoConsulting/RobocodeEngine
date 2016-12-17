package com.mirado.robocode.dropwizard;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Injector;
import com.mirado.robocode.resources.StatisticsResource;
import io.dropwizard.Application;
import io.dropwizard.Bundle;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;

/**
 * Created by Kurt on 18/11/16.
 */
public class RobocodeApplication extends Application<RoboConfiguration>
{
    private final Injector injector;

    public RobocodeApplication(Injector injector)
    {
        this.injector = injector;
    }

    /**
     * When the application runs, this is called after the {@link Bundle}s are run. Override it to add
     * providers, resources, etc. for your application.
     *
     * @param configuration the parsed {@link Configuration} object
     * @param environment   the application's {@link Environment}
     *
     * @throws Exception if something goes wrong
     */
    public void run(RoboConfiguration configuration, Environment environment) throws Exception
    {
        environment.jersey().register(injector.getInstance(StatisticsResource.class));
        environment.getObjectMapper().registerModule(new JavaTimeModule());
        environment.getObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
}
