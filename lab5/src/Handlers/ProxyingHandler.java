package Handlers;

import Attachments.CompleteAttachment;
import Exceptions.HandlerException;
import Exceptions.HandlerException.Types;
import Logger.GlobalLogger;
import Logger.LogWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

// read data from channel and write it to out buffer
public class ProxyingHandler implements Handler {
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);
    private static GlobalLogger exceptionLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.EXCEPTION_LOGGER);

    @Override
    public void handle(SelectionKey key) throws Exception {
        SelectableChannel channel = key.channel();
        assert channel instanceof SocketChannel;

        SocketChannel clientChannel = (SocketChannel) channel;
        CompleteAttachment attachment = (CompleteAttachment) key.attachment();
        SelectionKey remoteKey = CompleteAttachment.getRemoteChannelKey(key);
        String operationHostIP = "Can't read host name. Connection closed";
        if(remoteKey != null)
        {
            operationHostIP = ((CompleteAttachment)remoteKey.attachment()).getRemoteAddress().getAddress().getHostAddress();
        }
        String remoteOperationHostIp = attachment.getRemoteAddress().getHostName();
        ByteBuffer buffer;
            if (key.isReadable()) {
                    buffer = attachment.getOut();

                    if(remoteKey == null)
                    {
                        LogWriter.logWorkflow("Connection to the host:" + operationHostIP + "was emergency closed. " + key.toString(), workflowLogger);
                        HandlerFactory.getChannelCloser().handle(key);
                    }else {
                        try {
                            int count = clientChannel.read(buffer);
                            if (count == -1) {
                                // EOS reached so close channel
                                LogWriter.logWorkflow("EOF is read in connection to host: " + operationHostIP + " .Connection will be closed " + key.toString(), workflowLogger);
                                HandlerFactory.getChannelCloser().handle(key);
                                remoteKey.interestOps(SelectionKey.OP_WRITE);   // we should write all remaining data
                            } else if (count >= 0) {
                                key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);              // remove read operation
                                remoteKey.interestOps(remoteKey.interestOps() |SelectionKey.OP_WRITE); // add write operation to remote channel
                            }
                            LogWriter.logWorkflow("proxying: read " + buffer.position() + " bytes from host with address: " + operationHostIP + " " + key.toString(), workflowLogger);

                            buffer.flip();  // prepare buffer to writing
                        } catch (IOException e) {
                            //if we catch this exception then we need to emergency close this connection
                            LogWriter.logWorkflow("Connection to the host: " + operationHostIP + "// was emergency closed.", workflowLogger);
                            LogWriter.logException(e, exceptionLogger, " ClassName: " + e.getClass().getName() + " " + key.toString());
                            HandlerFactory.getChannelCloser().handle(key);
                        }
                    }

            } else if (key.isWritable()) {
                    buffer = attachment.getIn();
                    try {
                        clientChannel.write(buffer);
                    }catch(IOException e)
                    {
                        LogWriter.logException(e, workflowLogger, "connection to the host:" + operationHostIP + "was emergency closed. " + key.toString());
                        HandlerFactory.getChannelCloser().handle(key);
                    }
                    if (buffer.remaining() == 0) {
                        LogWriter.logWorkflow("proxying: wrote " + buffer.position() + " bytes to host with address: " + operationHostIP + key.toString(), workflowLogger);
                        if (remoteKey == null) {
                            // remote channel is closed, so close the connection
                            LogWriter.logWorkflow("Connection to the host" + remoteOperationHostIp + " was emergency closed. " + key.toString(), workflowLogger);
                            HandlerFactory.getChannelCloser().handle(key);
                        } else {
                            // all data wrote so clear the buffer and change channel's states:
                            buffer.clear();
                            key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
                            remoteKey.interestOps(remoteKey.interestOps() |SelectionKey.OP_READ); //
                        }
                    }

            }
    }
}
