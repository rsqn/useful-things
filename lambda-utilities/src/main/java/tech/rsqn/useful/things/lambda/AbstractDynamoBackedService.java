package tech.rsqn.useful.things.lambda;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Deprecated
public abstract class AbstractDynamoBackedService {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDynamoBackedService.class);

    protected AmazonDynamoDB dynamodb = null;
    protected DynamoDBMapper mapper = null;
    protected DynamoDBMapperConfig mapperConfig;
    private String region = null;
    private String endpoint = null;

    public AbstractDynamoBackedService() {
        region = System.getProperty("dynamo.region");
        endpoint = System.getProperty("dynamo.endpoint");

        Regions r = Regions.AP_SOUTHEAST_2;
        if (region != null && region.length() > 0) {
            r = Regions.fromName(region);
        }
        mapperConfig = DynamoDBMapperConfig.DEFAULT;

        if (endpoint != null && endpoint.length() > 0) {
            dynamodb = AmazonDynamoDBClientBuilder.standard().withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(endpoint, r.getName())).build();
        } else {
            dynamodb = AmazonDynamoDBClientBuilder.standard().withRegion(r).build();
        }

        mapper = new DynamoDBMapper(dynamodb);
    }

}
