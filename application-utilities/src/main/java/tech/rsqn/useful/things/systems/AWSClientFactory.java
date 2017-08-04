package tech.rsqn.useful.things.systems;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

public class AWSClientFactory implements InitializingBean {

    private String accessKey;
    private String secretKey;

    @Required
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    @Required
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }


    public AmazonS3Client s3Client() {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        AmazonS3Client s3client = new AmazonS3Client(credentials);
        return s3client;
    }

    public AWSSimpleSystemsManagementClient systemsManagementClient() {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        AWSSimpleSystemsManagementClient client = new AWSSimpleSystemsManagementClient(credentials);
        return client;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }
}