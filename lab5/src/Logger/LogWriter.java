package Logger;

import Exceptions.BaseException;

import java.io.IOException;
import java.util.logging.Level;

// logging exceptions of several types
public class LogWriter {
    public static synchronized void logException(Exception e, GlobalLogger exceptionLogger, String additionalMsg) {
        if (!additionalMsg.equals("")) {
            exceptionLogger.log(Level.WARNING, getExceptionLog(e) + ". " + additionalMsg + " ");
        }
        else {
            exceptionLogger.log(Level.WARNING, getExceptionLog(e) + " ");
        }
    }
    public static synchronized void logWorkflow(String msg, GlobalLogger workflowLogger) {
        workflowLogger.log(Level.INFO, msg + " ");
    }
    private static String getExceptionLog(Exception e) {
        if (e instanceof BaseException) {
            BaseException cast = (BaseException) e;
            return cast.getMessage();
        }
        else if(e instanceof IOException) {
            return "Fatal: I/O exception";
        }
        else if (e instanceof InterruptedException) {
            return "Fatal: Interrupted exception";
        }
        else {
            return "Fatal: other exception";
        }
    }
}
