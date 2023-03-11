package Exceptions;

public class HandlerException extends BaseException {

    public enum Types{
        ACCEPT_IMPOSSIBLE(""),
        ACCEPT_CLOSED_CHANNEL(""),
        DNS_CHANNEL_READ_IMPOSSIBLE(""),
        DNS_CHANNEL_WRITE_IMPOSSIBLE(""),
        DNS_RESOLVER_ERROR("");
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

    private static String createMessage(Exception parent, Types type, String message, String ipData)
    {
        return parent.getMessage()+ " | " + type.getTypeString()+ " | " + message + " | " + ipData;
    }

    public HandlerException(Exception e, Types type, String message, String client)
    {
        super(createMessage(e, type, message, client));
        this.initCause(e);
        this.exceptionType = type;
    }

    Types exceptionType;
}
