package Handlers;

import Attachments.CompleteAttachment;
import Attachments.BaseAttachment.KeyState;
import Exceptions.HandlerException;
import Exceptions.HandlerException.Types;
import Exceptions.SocksException;
import Logger.GlobalLogger;
import Logger.LogWriter;
import SOCKS.SOCKSv5;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class InitRequestHandler implements Handler {
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);
    @Override
    public void handle(SelectionKey key) throws Exception {
        LogWriter.logWorkflow("read init request... " + key.toString(), workflowLogger);

        assert key != null;
        SelectableChannel channel = key.channel();
        assert channel instanceof SocketChannel;
        assert key.attachment() instanceof CompleteAttachment;

        SocketChannel clientChannel = (SocketChannel) channel;
        CompleteAttachment attachment = (CompleteAttachment) key.attachment();
        ByteBuffer buffer = attachment.getOut();

        // read data from channel
        int count = 0;
        try {count = clientChannel.read(buffer);}
        catch (IOException e) {
            LogWriter.logWorkflow("ERROR. Exception while reading data from channel. This channel wil be closed.", workflowLogger);
            attachment.setState(KeyState.INIT_RESPONSE_FAILED);
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        // check end-of-stream was reached or no available in the channel
        if (count == 0) {
            //due to non blocking nature of SocketStream we must wait while there will be some data in this socket so we will return from this method
            return;
        } else if(count < 0){
            LogWriter.logWorkflow("ERROR. EOF occured earlier than INITRequest was read. This channel wil be closed.", workflowLogger);
            attachment.setState(KeyState.INIT_RESPONSE_FAILED);
            key.interestOps(SelectionKey.OP_WRITE);
        }
        buffer.flip();
        try {SOCKSv5.parseInitRequest(Arrays.copyOf(buffer.array(), buffer.limit()));}
        catch (SocksException e) {
            // set new state to the channel:
            key.interestOps(SelectionKey.OP_WRITE);
            attachment.setState(KeyState.INIT_RESPONSE_FAILED);
            return;
        }
        buffer.clear();
        // set new state to the channel:
        key.interestOps(SelectionKey.OP_WRITE);
        attachment.setState(KeyState.INIT_RESPONSE_SUCCESS);
        LogWriter.logWorkflow("Init request is read", workflowLogger);
    }
}
