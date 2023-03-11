package Handlers;

import Attachments.CompleteAttachment;
import Exceptions.HandlerException;
import Exceptions.HandlerException.Types;
import Logger.GlobalLogger;
import Logger.LogWriter;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public class CloseChannelHandler implements Handler {
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);
    @Override
    public void handle(SelectionKey key) {
            assert key != null;
            assert key.selector() != null;
            LogWriter.logWorkflow("Closing the channel..", workflowLogger);
            CompleteAttachment attachment = (CompleteAttachment) key.attachment();
            if (attachment.getRemoteChannel() != null) {
                SelectionKey remoteKey = CompleteAttachment.getRemoteChannelKey(key);
                if(remoteKey != null) {
                    CompleteAttachment remoteKeyAttachment = (CompleteAttachment) remoteKey.attachment();
                    // remove this channel from remote channel's field
                    remoteKeyAttachment.removeRemoteChannel();
                }
            }
            // cancel key and close channel
            LogWriter.logWorkflow(key.toString(), workflowLogger);
            key.cancel();
            try {
                key.channel().close();
            }catch(IOException e)
            {
                LogWriter.logException(e, workflowLogger, "WARNING. OS can't close socket channel");
            }
            LogWriter.logWorkflow("the channel closed", workflowLogger);
    }
}
