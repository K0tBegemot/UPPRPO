package Exceptions;

public class ResolverException extends BaseException {
    public enum Types{
        aa("");

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

    private static String createMessage(Exception parent, HandlerException.Types type, String message, String ipData)
    {
        return parent.getMessage()+ " | " + type.getTypeString()+ " | " + message + " | " + ipData;
    }

    public ResolverException(Exception e, HandlerException.Types type, String message, String client)
    {
        super(createMessage(e, type, message, client));
        this.initCause(e);
        this.exceptionType = type;
    }

    HandlerException.Types exceptionType;
}
