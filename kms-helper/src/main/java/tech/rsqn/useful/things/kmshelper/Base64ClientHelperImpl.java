package tech.rsqn.useful.things.kmshelper;

import com.google.common.io.BaseEncoding;
import tech.rsqn.useful.things.encryption.AESEncryptionTool;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Base64ClientHelperImpl implements Base64ClientHelper {

    private Charset charset;

    @Override
    public String encode(byte[] data) {
        return BaseEncoding.base64Url().encode(data);
    }

    @Override
    public byte[] decode(String data) {
        return BaseEncoding.base64Url().decode(data);
    }

    public Base64ClientHelperImpl() {
        this(StandardCharsets.UTF_8);
    }

    public Base64ClientHelperImpl(Charset charset) {
        this.charset = charset;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }
}
