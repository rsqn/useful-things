package tech.rsqn.useful.things.util;

public class Requirement {

    public static void notNull(Object o, String msg) {
        if ( o == null ) {
            throw new RequirementException(msg  + " is required");
        }
    }
}
