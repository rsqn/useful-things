package tech.rsqn.useful.things.storage;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.CopyObjectRequest;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.lang.IllegalArgumentException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class S3FileRecordService implements FileRecordService {
    private Logger log = LoggerFactory.getLogger(getClass());
    
    private AmazonS3 s3c;
    private String defaultPath = null;
    private String bucketName = null;
    private String regionName = null;
    private String accessKey = null;
    private String accessSecret = null;

    public S3FileRecordService() {
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucket) {
        if (bucket.startsWith("s3://")) {
            bucket = bucket.substring(5);
        }
        this.bucketName = bucket;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String region) {
        this.regionName = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String key) {
        this.accessKey = key;
    }

    public String getAccessSecret() {
        return accessSecret;
    }

    public void setAccessSecret(String secret) {
        this.accessSecret = secret;
    }

    public AmazonS3 getClient() {
        return s3c;
    }

    @Override
    public String toString() {
        return String.format("S3FileRecordService(%s)", bucketName);
    }

    public void connect() {
        // make sure we're given bucket name before proceeding
        if (bucketName == null) {
            throw new IllegalArgumentException("Property 'bucketName' must be set for S3FileRecordService");
        }

        ClientConfiguration clientConfig = null;

        if (System.getProperty("proxyHost") != null) {
            log.info("Will proxy to s3 via " + System.getProperty("proxyHost") + ":" + System.getProperty("proxyPort"));
            clientConfig = new ClientConfiguration();
            clientConfig.setProxyHost(System.getProperty("proxyHost"));
            clientConfig.setProxyPort(Integer.parseInt(System.getProperty("proxyPort")));
        }

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        if (clientConfig != null) {
            builder = builder.withClientConfiguration(clientConfig);
        }
        if (this.accessSecret != null && this.accessKey != null) {
            builder = builder.withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(this.accessKey, this.accessSecret)));
        }
        if (this.regionName != null) {
            builder = builder.withRegion(Regions.fromName(this.regionName));
        }
        s3c = builder.build();
    }

    private ObjectMetadata requestMetadata(String bucketName, String uid) {
        GetObjectMetadataRequest request = new GetObjectMetadataRequest(bucketName, uid);
        return s3c.getObjectMetadata(request);
    }

    private String getFullPath(String path, String uid) {
        if (path != null) {
            return path + "/" + uid;
        } else {
            return uid;
        }
    }


    @Override
    public FileHandle createNew(String name, String mimeType) {
        S3FileHandle handle = new S3FileHandle();
        handle.setUid(UUID.randomUUID().toString());
        handle.setName(name);
        handle.setMimeType(mimeType);
        handle.setBucketName(bucketName);
        handle.setS3client(s3c);
        handle.setResourcePath(defaultPath);
        return handle;
    }

    @Override
    public boolean exists(String uid) {
        return exists(defaultPath, uid);
    }

    @Override
    public boolean exists(String path, String uid) {
        try {
            this.requestMetadata(bucketName, getFullPath(path, uid));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public FileHandle getByUid(String uid) {
        return getByUidAndPath(defaultPath, uid);
    }

    public FileHandle getByUidAndPath(String path, String uid) {
        ObjectMetadata meta;

        try {
            meta = this.requestMetadata(bucketName, getFullPath(path, uid));
        } catch (Exception e) {
            log.error("Unable to request meta data for path: " + path + ", uid: " + uid + ", error:" + e.getMessage(), e);
            return null;
        }
        if (meta == null) {
            log.error("Unable to request meta data for path: " + path + ", uid: " + uid);
            return null;
        }

        S3FileHandle handle = new S3FileHandle();
        handle.setObjectMetadata(meta);
        handle.setUid(uid);
        handle.setName(meta.getUserMetadata().get("name"));
        handle.setMimeType(meta.getContentType());
        handle.setResourcePath(path);
        handle.setBucketName(bucketName);
        handle.setS3client(s3c);

        return handle;

    }

    @Override
    public void getAllByPath(String path, FileIterator fileIterator) {
        try{
            for ( S3ObjectSummary summary : S3Objects.withPrefix(s3c, bucketName, path) ) {
                // S3Objects iterator handles pagination
                if (summary.getSize() <= 0) {
                    log.info("Skipping empty key {}", summary.getKey());
                    continue;
                }
                FileHandle fileHandle = getByUid(summary.getKey());
                if (!fileIterator.onfileHandle(fileHandle)) {
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Failed to list S3 bucket {} content - {}", bucketName, e.toString());
        }
    }

    @Override
    public void getAll(FileIterator fileIterator) {
        getAllByPath(defaultPath, fileIterator);
    }

    @Override
    public void copy(String fromUid, String toUid)
    {
        copyTo(fromUid, this, toUid);
    }

    @Override
    public void copyTo(String fromUid, FileRecordService toSrv, String toUid)
    {
        // The copy operation is performed by the destination [toSrv] service since it is
        // expected to have credentials for the target bucket access
        if (!(toSrv instanceof S3FileRecordService)) {
            throw new IllegalArgumentException("Destination service must be instance of S3FileRecordService");
        }

        S3FileRecordService dst = (S3FileRecordService)toSrv;
        CopyObjectRequest copyObjRequest = new CopyObjectRequest(bucketName, fromUid, dst.getBucketName(), toUid);
        dst.getClient().copyObject(copyObjRequest);
    }
}
