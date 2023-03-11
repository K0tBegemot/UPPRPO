package SOCKS;

import Attachments.BaseAttachment.KeyState;
import Attachments.CompleteAttachment;
import Exceptions.SocksException;
import Exceptions.SocksException.Types;
import Logger.LogWriter;
import com.google.common.net.InetAddresses;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class SOCKSv5 {
    // Socks version, here - 5'th
    private static final byte SOCKS_VERSION = 0x05;
    // no authentication required
    private static final byte AUTH_METHOD = 0x00;
    // establish TCP connection command available
    private static final byte ESTABLISH_TCP = 0x01;
    // Reserved byte
    private static final byte RESERVED = 0x00;
    // unsupported authentication method
    private static final byte UNSUPPORTED_AUTH_METHOD = (byte) 0xff;

    // Inet Addresses types:
    public static final byte IPV4 = 0x01;
    public static final byte IPV6 = 0x04;
    public static final byte DN = 0x03;

    //server response codes:
    private static final byte REQUEST_PROVIDED = 0x00;
    private static final byte SOCKS_SERVER_EXCEPTION = 0x01;
    private static final byte HOST_UNAVAILABLE = 0x04;

    // other constants:
    private static final int IPV4_SIZE = 4;
    private static final int IPV6_SIZE = 16;
    private static final int AUTH_METHODS_COUNT = 1;
    private static final int INIT_MSG_HEADER_SIZE = 2;
    private static final int CONNECT_MSG_BASE_SIZE = 6;

    // methods for getting server response data:
    public static byte[] getSuccessInitResponse() {
        return new byte[]{SOCKS_VERSION, AUTH_METHOD};
    }
    public static byte[] getFailedInitResponse() {
        return new byte[]{SOCKS_VERSION, UNSUPPORTED_AUTH_METHOD};
    }
    public static byte[] getConnectionResponse(CompleteAttachment keyAttachment, InetSocketAddress aliasAddress) throws SocksException {
        //ConnectionRequestData requestData = keyAttachment.getConnectionRequestData();
        String stringAddress = "";
        if(aliasAddress == null)
        {
            aliasAddress = InetSocketAddress.createUnresolved("0.0.0.0", 0);
            stringAddress = "0.0.0.0";
        }else {
            stringAddress = aliasAddress.getAddress().getHostAddress();
        }
        byte addressType;
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(aliasAddress.getPort());
        byte[] portAddressBytes = buffer.array();
        byte[] addressBytes = InetAddresses.forString(stringAddress).getAddress();
        int aliasLength = addressBytes.length;
        if(aliasLength == 4)
        {
            addressType = IPV4;
        }else if(aliasLength == 16)
        {
            addressType = IPV6;
        }else{
            throw new SocksException(Types.FORMAT, "Error. Parse address is not IP_V4 or IP_V6. Address: " + stringAddress, "");
        }

        int responseMessageSize = 0;    // size of response message in bytes
        byte respCode = -1;                  // server response code

        if (keyAttachment.getState() == KeyState.CONNECT_RESPONSE_FAILED) {
            respCode = SOCKS_SERVER_EXCEPTION;
        }
        else if (keyAttachment.getState() == KeyState.CONNECT_RESPONSE_SUCCESS) {
            respCode = REQUEST_PROVIDED;
        }
        else if (keyAttachment.getState() == KeyState.CONNECT_RESPONSE_UNAVAILABLE){
            respCode = HOST_UNAVAILABLE;
        }
        else
        {
            throw new SocksException(Types.FORMAT, "Error. Incorrect response code.", "");
        }

        switch (addressType) {
            case IPV4:
                responseMessageSize = CONNECT_MSG_BASE_SIZE + IPV4_SIZE;
                break;
            case IPV6:
                responseMessageSize = CONNECT_MSG_BASE_SIZE + IPV6_SIZE;
                break;
        }
        byte[] response = new byte[responseMessageSize];
        // write response:
        response[0] = SOCKS_VERSION;    //  write header
        response[1] = respCode;         //
        response[2] = RESERVED;         //
        response[3] = addressType;      //

        writeAddressToRespMsg(response, addressBytes, addressType); // write address

        int lastIdx = response.length - 1;               // write port
        response[lastIdx - 1] = portAddressBytes[2];  //
        response[lastIdx] = portAddressBytes[3];      //
        return response;
    }
    // throws SOCKS exception if 'init message' is invalid
    public static void parseInitRequest(byte[] data) throws SocksException {
        if (data.length < INIT_MSG_HEADER_SIZE) {
            throw new SocksException(SocksException.Types.FORMAT, "Invalid request message size. Correct size: " +
                    Integer.toString(INIT_MSG_HEADER_SIZE) + " Your size: " + Integer.toString(data.length), "");
        }
        if (data[0] != SOCKS_VERSION) {
            throw new SocksException(Types.FORMAT, "Invalid socks version: " + Byte.toString(data[0]) + ".Needed version: " + Integer.toString(SOCKS_VERSION), "");
        }
        if (data[1] <= 0) {
            throw new SocksException(Types.FORMAT, "Invalid authentication methods count: " + Byte.toString(data[1]), "");
        }
        int count = data[1];    // authentication methods count

        // check data size:
        if (data.length != INIT_MSG_HEADER_SIZE + count) {
            throw new SocksException(Types.FORMAT, "Invalid request message size: " + Integer.toString(data.length) + ".Required: " + Integer.toString(INIT_MSG_HEADER_SIZE + count), "");
        }
        // check 'no auth required' method supporting:
        boolean found = false;
        for (int i = INIT_MSG_HEADER_SIZE; i < INIT_MSG_HEADER_SIZE + count; i++) {
            if (data[i] == AUTH_METHOD) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new SocksException(Types.FORMAT, "Suggested auth methods are not supported", "");
        }
    }
    // parses byte array of connection request message, throws SOCKS exception if message is invalid
    public static ConnectionMessage parseConnectRequest(byte[] msg) throws SocksException {
        // check header:
        if (msg.length < CONNECT_MSG_BASE_SIZE) {
            throw new SocksException(Types.FORMAT, "Invalid connect message size: " + Integer.toString(msg.length) + " .Required: " + Integer.toString(CONNECT_MSG_BASE_SIZE), "");
        }
        if (msg[0] != SOCKS_VERSION) {
            throw new SocksException(Types.FORMAT, "Invalid socks version: " + Byte.toString(msg[0]), "");
        }
        if (msg[1] != ESTABLISH_TCP) {
            throw new SocksException(Types.FORMAT, "Unsupported operation: not TCP connection", "");
        }
        if (msg[2] != RESERVED) {
            throw new SocksException(Types.FORMAT, "invalid reserved byte: " + Byte.toString(msg[2]), "");
        }
        byte addressType = msg[3];
        byte commandCode = msg[1];
        byte[] addressBytes = null;
        String address = null;

        // getting address value:
        switch (addressType) {
            case IPV4:
                checkMsgSize(msg, IPV4_SIZE + CONNECT_MSG_BASE_SIZE);
                addressBytes = Arrays.copyOfRange(msg, 4, 4 + IPV4_SIZE);
                address = getIP(addressBytes);
                break;
            case IPV6:
                checkMsgSize(msg, IPV6_SIZE + CONNECT_MSG_BASE_SIZE);
                addressBytes = Arrays.copyOfRange(msg, 4, 4 + IPV6_SIZE);
                address = getIP(addressBytes);
                break;
            case DN:
                int nameLength = msg[4];
                checkMsgSize(msg, CONNECT_MSG_BASE_SIZE + nameLength + 1);
                addressBytes = Arrays.copyOfRange(msg, 5, 5 + nameLength);
                address = new String(Arrays.copyOfRange(msg, 5, 5 + nameLength));
            default:
                assert false;
        }
        // getting port:
        int lastIdx = msg.length - 1;
        byte b1 = msg[lastIdx - 1];
        byte b2 = msg[lastIdx];
        int port = getPort(b1, b2);

        return new ConnectionMessage(commandCode, addressType, address, port, new byte[]{b1,b2}, addressBytes);
    }
    // decode ip address from byte array
    private static String getIP(byte[] data) throws SocksException {
        assert data.length == 4 || data.length == 6;
        String result;
        try {
            InetAddress address = InetAddress.getByAddress(data);
            result = address.getHostAddress();
        }
        catch (UnknownHostException e) {
            String ipString = "";
            for(int i = 0; i < data.length;i++)
            {
                ipString += Byte.toString(data[i]) + ".";
            }
            throw new SocksException(Types.FORMAT, "Invalid ip-address of destination host: " + ipString, "");
        }
        return result;
    }
    // return int value of port
    private static int getPort(byte b1, byte b2) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{0x00, 0x00, b1, b2});
        //return ((0xFF & b1) << Constants.BYTE_SIZE) + (0xFF & b2);
        return buffer.getInt();
    }
    // write address to response message
    private static void writeAddressToRespMsg(byte[] responseMsg, byte[] addressBytes, byte addressType) {
        final int stPosition = 4;
        if (addressType == DN) {
            byte addressSize = (byte) addressBytes.length;
            addressBytes[stPosition] = addressSize;
            System.arraycopy(addressBytes, 0, responseMsg, stPosition + 1, addressSize);
        }
        else {
            int size = addressBytes.length;
            System.arraycopy(addressBytes, 0, responseMsg, stPosition, size);
        }
    }
    /*
    // return two bytes of the port
    public static byte[] getPortBytes(int port) {
        int size = Constants.INT_SIZE;
        byte[] data = ByteBuffer.allocate(size).putInt(port).array();

        return new byte[]{data[size - 2], data[size - 1]};
    }
    */
    // check msg size and trow exception if it is invalid
    private static void checkMsgSize(byte[] msg, int size) throws SocksException {
        if (msg.length != size) {
            throw new SocksException(Types.FORMAT, "invalid message size: " + Integer.toString(msg.length) + ".Required: " + Integer.toString(size),"");
        }
    }
}
