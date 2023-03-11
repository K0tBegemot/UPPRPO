package Handlers;

import Attachments.BaseAttachment.KeyState;
import Attachments.CompleteAttachment;
import DNS.AddressGetter;
import DNS.DomainNameResolver;
import Exceptions.SocksException;
import Logger.GlobalLogger;
import Logger.LogWriter;
import SOCKS.ConnectionMessage;
import SOCKS.SOCKSv5;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

// handle connection request and implements connection to the remote channel
public class ConnectionRequestHandler implements Handler, Connector {
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);

    @Override
    public void handle(SelectionKey key) throws Exception {
        LogWriter.logWorkflow("Reading the connection request.", workflowLogger);
        SelectableChannel channel = key.channel();
        assert channel instanceof SocketChannel;
        SocketChannel clientChannel = (SocketChannel) channel;

        CompleteAttachment attachment = (CompleteAttachment) key.attachment();
        ByteBuffer buffer = attachment.getOut();
        // read data
        int count = 0;
        try {count = clientChannel.read(buffer);}
        catch (IOException e) {
            LogWriter.logWorkflow("ERROR. Can't read Connection request from socket. Channel will be emergency closed.", workflowLogger);
            HandlerFactory.getChannelCloser().handle(key);
            return;
        }
        if (count == 0) {
            return;
        }else if(count < 0)
        {
            LogWriter.logWorkflow("ERROR. EOF of connection request was received before read message. Close connection", workflowLogger);
            HandlerFactory.getChannelCloser().handle(key);
            return;
        }
        buffer.flip();
        byte[] requestData = Arrays.copyOfRange(buffer.array(), 0, buffer.limit());
        buffer.clear();

        try {
            //here we can handle socksException
            ConnectionMessage message = SOCKSv5.parseConnectRequest(requestData);
            // get port value and it's bytes:
            int port = message.portNumber;
            byte[] portBytes = message.portBytes;
            assert portBytes.length == 2;

            // get address value and it's bytes:
            String address = message.addressValue;
            byte[] addressBytes = message.addressBytes;

            LogWriter.logWorkflow("Connection request parsed: dst host - " + address + "; port - " + port + ".", workflowLogger);

            // add connection request data to attachment; will use in response sending:
//            ConnectionRequestData additionalData = new ConnectionRequestData(portBytes[0], portBytes[1], addressBytes, message.AddressType);
//            attachment.setConnectionRequestData(additionalData);

            if(message.AddressType == SOCKSv5.DN) {
                // request on resolving domain name
                key.interestOps(0);
                DomainNameResolver.getResolver().resolveDomainName(address, new DomainNameResolver.KeyData(port, key));////////////////////////////////////////////////
                return;
            } else {
                try {
                    InetSocketAddress dstAddress;
                    dstAddress = AddressGetter.getAddress(address, port);
                    attachment.setRemoteAddress(dstAddress);
                    connectToChannel(key);
                } catch (UnknownHostException e) {
                    LogWriter.logWorkflow("Error. Unknown host address. This connection will be closed", workflowLogger);
                    key.interestOps(SelectionKey.OP_WRITE);
                    attachment.setState(KeyState.CONNECT_RESPONSE_FAILED);
                }
            }
        }
        catch (SocksException e)
        {
            LogWriter.logException(e, workflowLogger, "");
            LogWriter.logWorkflow("ERROR. Socks Parse exception occured. This connection will be closed.", workflowLogger);
            key.interestOps(SelectionKey.OP_WRITE);
            attachment.setState(KeyState.CONNECT_RESPONSE_FAILED);
        }
    }
    @Override
    public void connectToChannel(SelectionKey key){
        assert key.attachment() instanceof CompleteAttachment;
        CompleteAttachment attachment = (CompleteAttachment) key.attachment();
        InetSocketAddress address = attachment.getRemoteAddress();
        assert address != null;

        String hostInfo = address.getHostString() + " " + address.getPort();
        LogWriter.logWorkflow("connecting to remote channel: " + hostInfo, workflowLogger);
        // establish remote channel connection
        SocketChannel remoteChannel = null;
        //boolean var for case where host is unavailable
        boolean isHostAvailable = false;
        try{
            remoteChannel = SocketChannel.open();
            remoteChannel.configureBlocking(false);
            remoteChannel.connect(address);
            remoteChannel.setOption(StandardSocketOptions.SO_RCVBUF, remoteChannel.socket().getSendBufferSize());
            //remoteChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            isHostAvailable = true;
        } catch (IOException e) {
            //throw new HandlerException(Classes.CONNECTION_RQST, "can't init connection to remote host", Types.IO);

            //if we catch IOEXCEPTION here when host with ip addres is completely unavailable and we can't even create SocketChannel
            //so we set operations on key to write and set attachment state to CONNECT_RESPONSE_UNAVAILABLE
            LogWriter.logWorkflow("IOException was catched while connect to channel in ConnectionRequestHandler. This connection will be closed", workflowLogger);
            key.interestOps(SelectionKey.OP_WRITE);
            attachment.setState(KeyState.CONNECT_RESPONSE_UNAVAILABLE);
            return;
        }
        // refactor attachments and channel's operations:
        attachment.setRemoteChannel(remoteChannel);
        key.interestOps(0);

        // create and init remote channel attachment:
        CompleteAttachment remoteChannelAttachment = new CompleteAttachment(KeyState.FINISH_REMOTE_CONNECT, false);
        remoteChannelAttachment.setIn(attachment.getOut());
        remoteChannelAttachment.setOut(attachment.getIn());
        remoteChannelAttachment.setRemoteChannel(key.channel());
        try {
            remoteChannelAttachment.setRemoteAddress(((InetSocketAddress) ((SocketChannel) key.channel()).getRemoteAddress()));
        }catch(IOException e)
        {
            LogWriter.logWorkflow("Error. Breaked Socket. This connection will be closed.", workflowLogger);
            key.interestOps(SelectionKey.OP_WRITE);
            ((CompleteAttachment) key.attachment()).setState(KeyState.CONNECT_RESPONSE_FAILED);
            return;
        }
        // register remote channel on connect to finish the connection
        try{
            remoteChannel.register(key.selector(), SelectionKey.OP_CONNECT, remoteChannelAttachment);
        }catch(ClosedChannelException e)
        {
            LogWriter.logWorkflow("RemoteChannel to host: " + address.getHostString() + " was closed. This connection will be closed.", workflowLogger);
            key.interestOps(SelectionKey.OP_WRITE);
            ((CompleteAttachment) key.attachment()).setState(KeyState.CONNECT_RESPONSE_FAILED);
            return;
        }
        LogWriter.logWorkflow("connection initialized: " + hostInfo, workflowLogger);
    }
}
