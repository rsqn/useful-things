package tech.rsqn.useful.things.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

//import com.mac.libraries.json.JsonUtils;
//import org.apache.commons.lang3.builder.ToStringBuilder;


public class FileSystemFileHandle extends FileHandle {
    private transient  Logger log = LoggerFactory.getLogger(getClass());
    private transient File tld;

    public void setTld(File tld) {
        this.tld = tld;
    }

    public void loadMeta() {
        try {
            String metaString = FileUtil.readFileToString(generateMetaFile());
//            FileSystemFileHandle handle = JsonUtils.fromJSON(getClass(), metaString);
//            BeanUtils.copyProperties(handle,this);
            validate();
        } catch ( Exception e ) {
            throw new RuntimeException("Unable to write metadata "+ e, e);
        }
    }

    public void validate() {
        if ( !generateDataFile().exists()) {
            throw new RuntimeException("Data validation failed, data does not exist " + generateDataFile().getName() );
        }
        if ( getLength() != generateDataFile().length()) {
            throw new RuntimeException("Data validation failed, data file is incorrect size " + generateDataFile().length() + " vs " + getLength());
        }
    }

    public void delete() {
        generateDataFile().delete();
        generateMetaFile().delete();
    }

    private void writeMeta() {
        try {
//            String metaString = JsonUtils.toJSON(this);
            FileUtil.writeStringToFile(generateMetaFile(), "yolo");
        } catch ( Exception e ) {
            throw new RuntimeException("Unable to write metadata "+ e, e);
        }
    }

    public long streamIn( InputStream is ) {
//        log.info("Streaming in " + ToStringBuilder.reflectionToString(this));
        try (FileOutputStream fos = new FileOutputStream(generateDataFile())) {
            log.debug("Writing initial metadata " + generateMetaFile().getAbsolutePath());
            writeMeta();
            log.info("Writing file " + generateDataFile().getAbsolutePath());
            FileUtil.copy(is,fos);
            setLength(generateDataFile().length());
            writeMeta();
            log.debug("Writing final metadata");
        } catch (Exception e) {
            throw new RuntimeException("Unable to write data "+ e, e);
        }
        return getLength();
    }

    public long streamOut( OutputStream os ) {
//        log.info("Streaming out " + ToStringBuilder.reflectionToString(this));
        try (FileInputStream fis = new FileInputStream(generateDataFile())) {
            log.info("Reading file " + generateDataFile().getAbsolutePath());
            FileUtil.copy(fis, os);
            return getLength();
        } catch (Exception e) {
            throw new RuntimeException("Unable to read or stream out data "+ e, e);
        }
    }

    private File generateMetaFile() {
        File file = new File(tld,"meta_" + getUid() +".json");
        return file;
    }

    private File generateDataFile() {
        File file = new File(tld,"data_" + getUid() + ".data");
        return file;
    }

    @Override
    public Map<String, String> getMeta() {
        return new HashMap<>();  // Has no meta that is not apart of FileHandle
    }
}
