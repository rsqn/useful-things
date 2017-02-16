package tech.rsqn.useful.things.identifiers;

import java.util.UUID;

public class UIDHelper {
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
