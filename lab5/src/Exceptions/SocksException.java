package Exceptions;

public class SocksException extends BaseException {

    public enum Types{
        FORMAT("");
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

    public SocksException(Types type, String message, String client)
    {
        super(createMessage(type, message, client));
        this.exceptionType = type;
    }

    Types exceptionType;
}
