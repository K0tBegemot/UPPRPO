package Logger;

import Exceptions.ServerException;

import java.sql.*;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.*;

// logger class which can have only two instances - exception logger and workflow logger
public class GlobalLogger {
    public enum LoggerType {EXCEPTION_LOGGER, WORKFLOW_LOGGER};
    public enum Mode {ENABLE_ALL, ENABLE_CONSOLE, ENABLE_DATABASE, DISABLE}  // logger mode (messages will be logging if mode equals 'ENABLE')

    private Logger logger;
    private Connection databaseConnection;
    private boolean isDatabaseWork = false;
    private final String databaseName = "test";
    private final String databaseHost = "localhost:5432";
    private final String databaseUser = "postgres";
    private final String databaseUserPassword = "51625162";

    private Mode mode = Mode.DISABLE; //disabled by default but can be overrided

    // singleton helper
    public static class LoggerCreator {
        private static GlobalLogger exceptionLogger;
        private static GlobalLogger workflowLogger;

        public static GlobalLogger getLogger(LoggerType type){
            switch (type) {
                case WORKFLOW_LOGGER: {
                    if (workflowLogger == null) {
                        workflowLogger = new GlobalLogger(type);
                    }
                    return workflowLogger;
                }
                case EXCEPTION_LOGGER: {
                    if (exceptionLogger == null) {
                        exceptionLogger = new GlobalLogger(type);
                    }
                    return exceptionLogger;
                }
            }
            return null;
        }
    }
    private GlobalLogger(LoggerType type){
        switch (type) {
            case EXCEPTION_LOGGER: {
                initialiseLogger("ExceptionLogger");
                break;
            }
            case WORKFLOW_LOGGER: {
                initialiseLogger("WorkflowLogger");
                break;
            }
        }

    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if(databaseConnection == null && (mode == Mode.ENABLE_DATABASE || mode == Mode.ENABLE_ALL))
        {
            initDatabase();
        }
    }

    private void initialiseLogger(String loggerName)
    {
        logger = Logger.getLogger(loggerName);
        logger = Logger.getLogger(logger.getName());
        logger.setUseParentHandlers(false);
        // config handler
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        // add handler to logger
        logger.addHandler(handler);
        initDatabase();
    }

    private void initDatabase()
    {
        isDatabaseWork = false;
        if(mode == Mode.ENABLE_ALL || mode == Mode.ENABLE_DATABASE) {
            String url = "jdbc:postgresql://" + databaseHost + "/" + databaseName;
            Properties property = new Properties();
            property.put("user", databaseUser);
            property.put("password", databaseUserPassword);
            try {
                databaseConnection = DriverManager.getConnection(url, property);
            }
            catch (SQLException e) {
                isDatabaseWork = false;
                System.err.println("ERROR. SQL DATABASE with name: " + url + " . Message: \n" + e.getMessage());
            }
            isDatabaseWork = true;
            System.out.println("Connection with database: " + url + " was established successfully on " + logger.getName());
            cleanUpSessionDBTable(url);
        }
    }

    private void cleanUpSessionDBTable(String url)
    {
        if(isDatabaseWork) {
            try {
                Statement statement = databaseConnection.createStatement();
                int oldNumberOfRows = statement.executeUpdate("TRUNCATE logs;");
                Statement statement2 = databaseConnection.createStatement();
                int oldNumberOfRows2 = statement2.executeUpdate("ALTER SEQUENCE logs_row_id_seq RESTART WITH 1");
                System.out.println("SQL DATABASE with name: " + url + " . Previous session logs have been removed successfully.");
            } catch (SQLException e) {
                isDatabaseWork = false;
                System.err.println("FATAL ERROR. SQL DATABASE with name: " + url + " . Previous session logs haven't been removed. ");
            }
        }
    }
    private static class SimpleFormatter extends Formatter {
        @Override
        public String format(LogRecord logRecord) {
            Calendar calendar = Calendar.getInstance(); // set current date and time
            String date = calendar.get(Calendar.DATE) + "." + calendar.get(Calendar.MONTH) +
                    "." + calendar.get(Calendar.YEAR) + " " + calendar.get(Calendar.HOUR) +
                    ":" + calendar.get(Calendar.MINUTE) + ":" + calendar.get(Calendar.SECOND);
            // log message represents: date + level + message
            return date + " " + logRecord.getLevel() + ": " + logRecord.getMessage() + "\n";
        }
    }
    public void log(Level level, String msg)
    {
        switch(this.mode)
        {
            case ENABLE_ALL:
            {
                logger.log(level, msg);
                if(isDatabaseWork)
                {
                    databaseLog(format(level, msg));
                }
                break;
            }
            case ENABLE_CONSOLE:
            {
                logger.log(level, msg);
                break;
            }
            case ENABLE_DATABASE:
            {
                if(isDatabaseWork)
                {
                    databaseLog(format(level, msg));
                }
                break;
            }
            case DISABLE:
            {
                break;
            }
        }
    }

    private String format(Level level, String message)
    {
        Calendar calendar = Calendar.getInstance(); // set current date and time
        String date = calendar.get(Calendar.DATE) + "." + calendar.get(Calendar.MONTH) +
                "." + calendar.get(Calendar.YEAR) + " " + calendar.get(Calendar.HOUR) +
                ":" + calendar.get(Calendar.MINUTE) + ":" + calendar.get(Calendar.SECOND);
        // log message represents: date + level + message
        return date + " " + level.getName() + message;
    }

    private void databaseLog(String msg)
    {
        try {
            PreparedStatement statement = databaseConnection.prepareStatement("INSERT INTO logs(data) VALUES(?)");
            statement.setString(1, msg);
            int numberOfInsertedRows = statement.executeUpdate();
            if(numberOfInsertedRows != 1)
            {
                System.err.println("ERROR. Database output is not working. Last row wasn't inserted");
                isDatabaseWork = false;
            }
        }catch(SQLException e)
        {
            System.err.println("ERROR. Database output is not working");
            isDatabaseWork = false;
        }
    }
}