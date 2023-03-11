package Handlers;

import Attachments.BaseAttachment.KeyState;
import Attachments.CompleteAttachment;
import Logger.GlobalLogger;
import Logger.LogWriter;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class FinishConnectionHandler implements Handler {
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);
    private static GlobalLogger exceptionLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.EXCEPTION_LOGGER);

    @Override
    public void handle(SelectionKey key) throws Exception {
        SelectableChannel channel = key.channel();
        assert channel instanceof SocketChannel;

        SocketChannel clientChannel = (SocketChannel) channel;
        CompleteAttachment attachment = (CompleteAttachment) key.attachment();
        SelectionKey remoteKey = CompleteAttachment.getRemoteChannelKey(key);
        CompleteAttachment remoteAttachment = (CompleteAttachment) remoteKey.attachment();

        String hostInfo = remoteAttachment.getRemoteAddress().getHostString() + " " + remoteAttachment.getRemoteAddress().getPort();
        LogWriter.logWorkflow("finishing connection to host: " + hostInfo, workflowLogger);
        //variable to determine whether response is failed or not
        boolean isResponseSuccess = false;
        try {
            clientChannel.finishConnect();
            isResponseSuccess = true;
        }
        catch (Exception e) {
            isResponseSuccess = false;
            LogWriter.logException(e, exceptionLogger, "ClassName: " + e.getClass().getName());
        }
        LogWriter.logWorkflow(key.toString(), workflowLogger);
        LogWriter.logWorkflow(remoteKey.toString(), workflowLogger);
        if(isResponseSuccess) {
            key.interestOps(0);
            // register remote (client) key on Connection response success
            registerOnConnectionResponse(KeyState.CONNECT_RESPONSE_SUCCESS, remoteKey);
            LogWriter.logWorkflow("connection finished to host " + hostInfo, workflowLogger);
        }else{
            // register remote (client) key on Connection response failed
            registerOnConnectionResponse(KeyState.CONNECT_RESPONSE_FAILED, remoteKey);
            String msg = "can't finish the connection to " + attachment.getRemoteAddress().getHostString() + " " + attachment.getRemoteAddress().getPort();
            LogWriter.logWorkflow(msg, workflowLogger);
        }
    }
    private void registerOnConnectionResponse(KeyState state, SelectionKey key) {
        key.interestOps(SelectionKey.OP_WRITE);
        CompleteAttachment attachment = (CompleteAttachment) key.attachment();
        attachment.setState(state);
    }
}
