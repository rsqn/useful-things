package tech.rsqn.useful.things.serialization;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class XmlSerializer {

    public static byte[] toXmlBytes(Object o) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        final XMLEncoder encoder = new XMLEncoder(bos);
        encoder.writeObject(o);
        encoder.close();

        return bos.toByteArray();
    }

    public static <T> T fromXmlBytes(byte[] buff) {

        ByteArrayInputStream bis = new ByteArrayInputStream(buff);

        final XMLDecoder decoder = new XMLDecoder(bis);

        Object ret = decoder.readObject();
        decoder.close();

        return (T) ret;
    }
}
