package tech.rsqn.useful.things.systems;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

public class AWSClientFactory implements InitializingBean {

    private String accessKey;
    private String secretKey;
    private String region;

    @Required
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    @Required
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @Required
    public void setRegion(String region) {
        this.region = region;
    }

    public AmazonS3Client s3Client() {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        AmazonS3Client client = new AmazonS3Client(credentials);
        return client;
    }

    public AWSSimpleSystemsManagementClient systemsManagementClient() {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        AWSSimpleSystemsManagementClient client = new AWSSimpleSystemsManagementClient(credentials);
        client.setRegion(RegionUtils.getRegion(region));
        return client;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }
}