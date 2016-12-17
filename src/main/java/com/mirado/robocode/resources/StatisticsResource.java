package com.mirado.robocode.resources;

import com.mirado.robocode.domain.BattleStatistics;
import com.mirado.robocode.services.ScoreService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

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
    @Path("/")
    public Collection<BattleStatistics> getStatistics()
    {
        return scoreService.getStatistics();
    }
}
