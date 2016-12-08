package com.mirado.robocode.archaius;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.netflix.archaius.config.polling.PollingResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Created by Kurt on 04/12/16.
 */
public class DynamoDbSource implements Callable<PollingResponse>
{
    private final AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient();

    public DynamoDbSource()
    {
        this.dynamoDBClient.setRegion(Region.getRegion(Regions.EU_WEST_1));
    }

    @Override
    public PollingResponse call() throws Exception
    {
        Map<String, String> values = new HashMap<>();
        Map<String, AttributeValue> lastKeyEvaluated = null;
        do
        {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("robocodeproperties")
                    .withLimit(100)
                    .withExclusiveStartKey(lastKeyEvaluated);

            ScanResult result = dynamoDBClient.scan(scanRequest);
            for (Map<String, AttributeValue> item : result.getItems())
            {
                String key = item.get("key").getS();
                String value = item.get("value").getS();
                values.put(key, value);
            }
            lastKeyEvaluated = result.getLastEvaluatedKey();
        } while (lastKeyEvaluated != null);

        return PollingResponse.forSnapshot(values);
    }
}
