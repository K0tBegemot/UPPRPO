package Handlers;

import Attachments.BaseAttachment;
import Attachments.CompleteAttachment;
import Exceptions.HandlerException;
import Exceptions.HandlerException.Types;
import Logger.GlobalLogger;
import Logger.LogWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.net.StandardSocketOptions;

public class AcceptHandler implements Handler {
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);
    @Override
    public void handle(SelectionKey key) throws HandlerException {
        assert key != null;
        try {
            LogWriter.logWorkflow("Accepting new client.", workflowLogger);

            SelectableChannel channel = key.channel();
            System.err.println(key.toString());
            assert channel instanceof ServerSocketChannel;

            ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
            Selector selector = key.selector();

            //accept new client channel
            SocketChannel clientChannel = serverChannel.accept();
            clientChannel.configureBlocking(false);
            //clientChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            clientChannel.setOption(StandardSocketOptions.SO_RCVBUF, clientChannel.socket().getSendBufferSize());

            //clientChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            // register channel on read initial request
            clientChannel.register(selector, SelectionKey.OP_READ, new CompleteAttachment(BaseAttachment.KeyState.INIT_REQUEST, true));

            LogWriter.logWorkflow("New client accepted.", workflowLogger);
        }
        catch (IOException e) {
            LogWriter.logWorkflow("FATAL ERROR. IOException triggered throw of HandlerException.", workflowLogger);
            throw new HandlerException(e, Types.ACCEPT_IMPOSSIBLE, "FATAL ERROR. Server can't accept new client. Server will be terminated.", "");
        }
    }
}
