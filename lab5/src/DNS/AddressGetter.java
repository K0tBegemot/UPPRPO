package DNS;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

// gets InetSocketAddress from IP address in text format + port
public class AddressGetter {
    public static InetSocketAddress getAddress(String ipAddress, int port) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ipAddress);
        return new InetSocketAddress(address, port);
    }
}
