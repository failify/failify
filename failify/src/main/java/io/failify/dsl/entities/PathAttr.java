package io.failify.dsl.entities;

public enum PathAttr {
    CHANGEABLE, // marks the path as changeable which results in a separate copy of the path for each node.
    LIBRARY, // marks the path as a library to be used by the instrumentation engine
    COMPRESSED // marks the path as compressed to be decompressed before being added to the node created out of this service
}
