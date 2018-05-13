package tech.rsqn.useful.things.storage;

@FunctionalInterface
public interface FileIterator {
    boolean onfileHandle(FileHandle fileHandle);
}
