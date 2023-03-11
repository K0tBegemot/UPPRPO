package Core;

public class Constants {
    public static final int BUFFER_SIZE = 1024 * 64;
    public static final int BYTE_SIZE = 8;
    public static final int INT_SIZE = 4;
    public static class ResolverConstants {
        public static final int BUFF_SIZE = 65535;      // maximum length of message in wire format
        public static final int REQUESTS_DELTA_TIME = 1000;
        public static final int CACHE_CAPACITY = 256;   // number of buckets in resolved domain name hash table
        public static final double LOAD_FACTOR = 0.75;  // default load factor in Java HashTable
    }
}
