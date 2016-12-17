package com.mirado.robocode.resources;

import com.mirado.robocode.domain.Scoreboard;
import com.mirado.robocode.services.ScoreService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/statistics")
@Produces(MediaType.APPLICATION_JSON)
public class StatisticsResource
{
    private final ScoreService scoreService;

    @Inject
    public StatisticsResource(ScoreService scoreService)
    {
        this.scoreService = scoreService;
    }

    @GET
    @Path("/scoreboard")
    public Scoreboard getStatistics()
    {
        return scoreService.getStatistics();
    }
}
