package Handlers;

import Attachments.BaseAttachment.KeyState;
import Attachments.CompleteAttachment;
import Exceptions.HandlerException;
import Exceptions.SocksException;
import Logger.GlobalLogger;
import Logger.LogWriter;
import SOCKS.SOCKSv5;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ConnectionResponseHandler implements Handler {
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);
    private static GlobalLogger exceptionLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.EXCEPTION_LOGGER);

    @Override
    public void handle(SelectionKey key) throws HandlerException {
        SelectableChannel channel = key.channel();
        assert channel instanceof SocketChannel;

        SocketChannel clientChannel = (SocketChannel) channel;
        CompleteAttachment attachment = (CompleteAttachment) key.attachment();
        SelectionKey remoteKey = CompleteAttachment.getRemoteChannelKey(key);
        InetSocketAddress aliasAddress = null;
        try {
            aliasAddress = ((InetSocketAddress) ((SocketChannel) remoteKey.channel()).getLocalAddress());
        } catch (Exception e) {
            //do nothing
        }
        String hostInfo = "Invalid info";
        try {
            hostInfo = ((SocketChannel) key.channel()).getRemoteAddress().toString();
        } catch (IOException e) {
            //do nothing
        }
        LogWriter.logWorkflow("Sending connection response. host: " + hostInfo, workflowLogger);

        ByteBuffer buffer = attachment.getIn();

        // write response to buffer (if don't wrote yet)
        try {
            if (!attachment.isRespWroteToBuffer) {
                byte[] responseData = SOCKSv5.getConnectionResponse(attachment, aliasAddress);
                buffer.put(responseData);
                buffer.flip();
                attachment.isRespWroteToBuffer = true;
            }
        } catch (SocksException e) {
            LogWriter.logWorkflow("ERROR. SocksException was catched while ConnectionResponce. This channel will be closed. Host: " + hostInfo, workflowLogger);
            try {
                ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(key);
            } catch (Exception ee) {

            }
            if (remoteKey != null) {
                try {
                    ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(remoteKey);
                } catch (Exception ee) {

                }
            }
            return;
        }
        // write data to channel
        try {
            clientChannel.write(buffer);
        } catch (IOException e) {
            LogWriter.logWorkflow("ERROR. IOException was catched while ConnectionResponce. This channel will be closed. Host: " + hostInfo, workflowLogger);
            try {
                ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(key);
            } catch (Exception ee) {

            }
            if (remoteKey != null) {
                try {
                    ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(remoteKey);
                } catch (Exception ee) {

                }
            }
            return;
        }
        // change channel state if message wrote
        if (buffer.remaining() <= 0) {
            LogWriter.logWorkflow("Connection response send. Host: " + hostInfo, workflowLogger);
            attachment.isRespWroteToBuffer = false; // reset flag
            buffer.clear();
            if (attachment.getState() == KeyState.CONNECT_RESPONSE_SUCCESS) {
                // change client channel state
                attachment.setState(KeyState.PROXYING);
                key.interestOps(SelectionKey.OP_READ);
                CompleteAttachment remoteKeyAttachment = (CompleteAttachment) remoteKey.attachment();

                // change remote channel state
                remoteKeyAttachment.setState(KeyState.PROXYING);
                remoteKey.interestOps(SelectionKey.OP_READ);
                LogWriter.logWorkflow(key.toString(), workflowLogger);
                LogWriter.logWorkflow(remoteKey.toString(), workflowLogger);
            } else if (attachment.getState() == KeyState.CONNECT_RESPONSE_FAILED) {
                try {
                    ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(key);
                } catch (Exception ee) {

                }
                if (remoteKey != null) {
                    try {
                        ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(remoteKey);
                    } catch (Exception ee) {

                    }
                }
                return;
            } else if (attachment.getState() == KeyState.CONNECT_RESPONSE_UNAVAILABLE) {
                try {
                    ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(key);
                } catch (Exception ee) {

                }
                if (remoteKey != null) {
                    try {
                        ((CloseChannelHandler) HandlerFactory.getChannelCloser()).handle(remoteKey);
                    } catch (Exception ee) {

                    }
                }
                return;
            }
        }
    }
}
