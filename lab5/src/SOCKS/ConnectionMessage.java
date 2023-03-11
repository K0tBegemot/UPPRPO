package SOCKS;

public class ConnectionMessage {
    public final byte commandCode;
    public final byte AddressType;
    public final String addressValue;   // IP address or domain name
    public final int portNumber;

    public final byte[] portBytes;      // two bytes of port
    public final byte[] addressBytes;   // bytes of address (4-ipv4; 16-ipv6; 1 + domain name bytes count)

    public ConnectionMessage(byte code, byte type, String address, int port, byte[] portBytes, byte[] addressBytes) {
        this.AddressType = type;
        this.addressValue = address;
        this.commandCode = code;
        this.portNumber = port;

        this.portBytes = portBytes;
        this.addressBytes = addressBytes;
    }
}
