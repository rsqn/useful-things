package tech.rsqn.useful.things.storage;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

//import org.apache.commons.lang3.StringUtils;

public class S3FileHandle extends FileHandle {
    private transient Logger log = LoggerFactory.getLogger(getClass());
    private transient String bucketName;
    private transient AmazonS3 s3client;
    private ObjectMetadata objectMetadata;
    private transient String sseCustomerAlgorithm = null;
//    private transient SSECustomerKey sseCustomerKey = null;

    private static final String APPLICATION_META = "isx-app-meta-";

    public void setS3client(AmazonS3 s3client) {
        this.s3client = s3client;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    protected void setSseCustomerAlgorithm(String sseCustomerAlgorithm) {
        this.sseCustomerAlgorithm = sseCustomerAlgorithm;
    }

//    protected void setSseCustomerKey(SSECustomerKey sseCustomerKey) {
//        this.sseCustomerKey = sseCustomerKey;
//    }

    protected void setObjectMetadata(ObjectMetadata objectMetadata) {
        this.objectMetadata = objectMetadata;
    }


    private String getFullPath() {
        if (getResourcePath() != null) {
            return getResourcePath() + "/" + getUid();
        } else {
            return getUid();
        }
    }

    @Override
    public String getMimeType() {
        if (super.getMimeType() == null && objectMetadata != null) {
            super.setMimeType(objectMetadata.getContentType());
        }
        return super.getMimeType();
    }

    @Override
    public long getLength() {
        if (super.getLength() > 0) {
            return super.getLength();
        }
        if (objectMetadata != null) {
            super.setLength(objectMetadata.getContentLength());
            return objectMetadata.getContentLength();
        }
        return 0;
    }

    @Override
    public void delete() {
        DeleteObjectRequest request = new DeleteObjectRequest(bucketName, getUid());
        s3client.deleteObject(request);
    }

    private ObjectMetadata requestMetadata(String bucketName, String uid) {
        GetObjectMetadataRequest request = new GetObjectMetadataRequest(bucketName, uid);
//        if (sseCustomerKey != null) {
//            request.withSSECustomerKey(sseCustomerKey);
//        }
        return s3client.getObjectMetadata(request);
    }

    @Override
    public long streamIn(InputStream is) {
        ObjectMetadata metadata = new ObjectMetadata();

        metadata.addUserMetadata("name", getName());
        metadata.addUserMetadata("mimeType", getMimeType());
        metadata.setContentType(getMimeType());
        if (getLength() > 0) {
            metadata.setContentLength(getLength());
        }

        if (sseCustomerAlgorithm != null) {
//            metadata.setSSECustomerAlgorithm(sseCustomerAlgorithm);
        }

        PutObjectRequest request = new PutObjectRequest(bucketName, getFullPath(), is, metadata);

//        if (sseCustomerKey != null) {
//            request.withSSECustomerKey(sseCustomerKey);
//        }

        PutObjectResult result = s3client.putObject(request);
        log.debug("puObject result " + result);

        ObjectMetadata metaLoaded = this.requestMetadata(bucketName, getFullPath());

        this.objectMetadata = metaLoaded;

        return metaLoaded.getContentLength();
    }

    @Override
    public InputStream asInputStream() {
        GetObjectRequest request = new GetObjectRequest(bucketName, getFullPath());
        S3Object obj = s3client.getObject(request);
        return obj.getObjectContent();
    }

    @Override
    public long streamOut(OutputStream os) {
        try {
            long ret = IOUtils.copy(asInputStream(), os);
            return ret;
        } catch (IOException e) {
            throw new RuntimeException("Exception copying stream " + e.getMessage(), e);
        }
    }

    public Map<String, String> getUserDefinedMeta(){
        Map<String, String> meta = new HashMap<>();
        if(objectMetadata != null && objectMetadata.getUserMetadata() != null){
            // user defined meta - prepend tag so it does not get overridden by S3 defined meta
            for(String key : objectMetadata.getUserMetadata().keySet()){
                meta.put(APPLICATION_META + key, objectMetadata.getUserMetadata().get(key));
            }
        }
        return meta;
    }

    public Map<String,String> getS3Meta() {
        Map<String, String> meta = new HashMap<>();
        if(objectMetadata != null){
            /*
            meta.put(FileConstants.CONTENT_LENGTH, "" + objectMetadata.getContentLength());
            if(StringUtils.isNotEmpty(objectMetadata.getCacheControl())){
                meta.put(FileConstants.CACHE_CONTROL, objectMetadata.getCacheControl());
            }
            if(StringUtils.isNotEmpty(objectMetadata.getContentDisposition())){
                meta.put(FileConstants.CONTENT_DISPOSITION, objectMetadata.getContentDisposition());
            }
            if(StringUtils.isNotEmpty(objectMetadata.getContentEncoding())){
                meta.put(FileConstants.CONTENT_ENCODING, objectMetadata.getContentEncoding());
            }
            if(StringUtils.isNotEmpty(objectMetadata.getContentMD5())){
                meta.put(FileConstants.CONTENT_MD5, objectMetadata.getContentMD5());
            }
            if(StringUtils.isNotEmpty(objectMetadata.getContentType())){
                meta.put(FileConstants.CONTENT_TYPE, objectMetadata.getContentType());
            }
            if(StringUtils.isNotEmpty(objectMetadata.getETag())){
                meta.put(FileConstants.ETAG, objectMetadata.getETag());
            }
            if(objectMetadata.getExpirationTime() != null){
                meta.put(FileConstants.EXPIRES, DateUtil.getISO861DateFormat().format(objectMetadata.getExpirationTime()));
            }
            if(objectMetadata.getLastModified() != null){
                meta.put(FileConstants.LAST_MODIFIED, DateUtil.getISO861DateFormat().format(objectMetadata.getLastModified()));
            }
            if(StringUtils.isNotEmpty(objectMetadata.getVersionId())){
                meta.put(FileConstants.VERSION_ID, objectMetadata.getVersionId());
            }
            */
        }
        return meta;
    }

    @Override
    public Map<String, String> getMeta() {
        // s3 meta
        Map<String, String> meta = new HashMap<>();
        // user defined meta
        meta.putAll(getUserDefinedMeta());
        // s3 defined meta
        meta.putAll(getS3Meta());
        return meta;
    }

}
