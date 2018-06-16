package tech.rsqn.useful.things.storage;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class S3FileRecordService implements FileRecordService {
    private Logger log = LoggerFactory.getLogger(getClass());
    private AmazonS3 s3c;
    private Map<String, String> properties;
    private Map<String, Bucket> bucketMap;
    private String bucketName;
//    private SSECustomerKey sseCustomerKey = null;
    private String sseCustomerAlgorithm = null;
    private Region region;
    private String defaultPath = null;

    public S3FileRecordService() {
        bucketMap = new HashMap<>();
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void setDefaultPath(String defaultPath) {
        this.defaultPath = defaultPath;
    }

    public void connect() {
        ClientConfiguration clientConfig = null;

        if (System.getProperty("proxyHost") != null) {
            log.info("Will proxy to s3 via " + System.getProperty("proxyHost") + ":" + System.getProperty("proxyPort"));
            clientConfig = new ClientConfiguration();
            clientConfig.setProxyHost(System.getProperty("proxyHost"));
            clientConfig.setProxyPort(Integer.parseInt(System.getProperty("proxyPort")));
        }

        if (properties.containsKey("SSECustomerKey")) {
            // this is to make it compatible with node
            try {
                String keyString = properties.get("SSECustomerKey");
                byte[] key = keyString.getBytes();
                log.info("Key length " + key.length);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
        if (properties.containsKey("SSECustomerAlgorithm")) {
            sseCustomerAlgorithm = properties.get("SSECustomerAlgorithm");
        }

        if (clientConfig == null) {
            s3c = AmazonS3ClientBuilder.standard().build();
        } else {
            s3c = AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfig).build();
        }


        if (properties.get("region") != null) {
            region = Region.getRegion(Regions.fromName(properties.get("region")));
            s3c.setRegion(region);
        }

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
//        handle.setSseCustomerAlgorithm(sseCustomerAlgorithm);
//        handle.setSseCustomerKey(sseCustomerKey);
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
//        handle.setBucket(bucket);
        handle.setResourcePath(path);
        handle.setBucketName(bucketName);
        handle.setS3client(s3c);
//        handle.setSseCustomerAlgorithm(sseCustomerAlgorithm);
//        handle.setSseCustomerKey(sseCustomerKey);

        return handle;

    }

    @Override
    public void getAllByPath(String path, FileIterator fileIterator) {
        try{
            for ( S3ObjectSummary summary : S3Objects.withPrefix(s3c, bucketName, path) ) {
                // S3Objects iterator handles pagination
                FileHandle fileHandle = getByUid(summary.getKey());
                if (!fileIterator.onfileHandle(fileHandle)) {
                    return;
                }
            }
        } catch (Exception e){
            log.error("Problem with getAll", e);
        }
    }

    @Override
    public void getAll(FileIterator fileIterator) {
        getAllByPath(defaultPath, fileIterator);
    }
}
