package Exceptions;

public class ServerException extends BaseException {
    public enum Types{
        INIT(""),
        SELECT("");
        private Types(String type)
        {
            this.typeMessage = type;
        }

        public String getTypeString()
        {
            return typeMessage;
        }
        final private String typeMessage;
    }

    private static String createMessage(Types type, String message, String ipData)
    {
        return type.getTypeString()+ " | " + message + " | " + ipData;
    }

    public ServerException(Types type, String message, String client)
    {
        super(createMessage(type, message, client));
        this.exceptionType = type;
    }

    Types exceptionType;
}
