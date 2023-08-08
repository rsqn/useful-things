package tech.rsqn.useful.things.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import org.apache.commons.io.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;

public class S3ObjectRetriever {

    private Logger LOG = LoggerFactory.getLogger(getClass());
    private AmazonS3 s3Client;
    private String region = null;

    public String getS3Object(String bucket, String path, String encoding) {

        LOG.info("Preparing to retrieve an object from S3 bucket: " + bucket);
        connect();
        LOG.info("Established connection with S3.");

        if (bucket.equalsIgnoreCase("root")) {
            bucket = "";
        }

        S3Object s3Object = null;
        try {
            LOG.info("Retrieving an object from S3.");
            s3Object = s3Client.getObject(new GetObjectRequest(bucket, path));
            LOG.info("Object Retrieved Successfully from S3");

            S3ObjectInputStream s3ObjectInputStream = s3Object.getObjectContent();

            StringWriter writer = new StringWriter();
            try {
                IOUtil.copy(s3ObjectInputStream, writer, encoding);
                return writer.toString();
            } catch (IOException e) {
                LOG.error("IO Error: " + e.getMessage());
            }

        } catch (AmazonServiceException ase) {
            LOG.error("Caught an AmazonServiceException.");
            LOG.error("Error Message:    " + ase.getMessage());
            LOG.error("HTTP Status Code: " + ase.getStatusCode());
            LOG.error("AWS Error Code:   " + ase.getErrorCode());
            LOG.error("Error Type:       " + ase.getErrorType());
            LOG.error("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            LOG.error("Caught an AmazonClientException.");
            LOG.error("Error Message: " + ace.getMessage());
        }
        return null;
    }

    private void connect() {
        ClientConfiguration clientConfig = null;

        if (System.getProperty("proxyHost") != null) {
            LOG.info("Connecting with proxy to s3 via " + System.getProperty("proxyHost") + ":" + System.getProperty("proxyPort"));
            clientConfig = new ClientConfiguration();
            clientConfig.setProxyHost(System.getProperty("proxyHost"));
            clientConfig.setProxyPort(Integer.parseInt(System.getProperty("proxyPort")));
        }

        if (clientConfig == null) {
            s3Client = AmazonS3ClientBuilder.standard().build();
        } else {
            s3Client = AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfig).build();
        }

        if (region != null) {
            Region r = Region.getRegion(Regions.fromName(region));
            s3Client.setRegion(r);
        }
    }
}