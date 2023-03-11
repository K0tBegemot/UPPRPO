package Attachments;

// parent class of keys attachments, represents key state
public class BaseAttachment {
    public enum KeyState {
        ACCEPT,                     // server channel state - accepting new clients
        INIT_REQUEST,               // client channel state - reading 'Hello' msg from channel
        INIT_RESPONSE_SUCCESS,      // client channel state - writing 'Hello' msg response to channel
        INIT_RESPONSE_FAILED,       // client channel state - writing failed response on 'Hello' msg to channel
        CONNECT_REQUEST,            // client channel state - reading 'Connection request' msg from channel
        CONNECT_RESPONSE_SUCCESS,   // client channel state - writing success connection response to channel
        CONNECT_RESPONSE_FAILED,    // client channel state - writing failed connection response to channel
        CONNECT_RESPONSE_UNAVAILABLE, //client channel state - destination server is unavailable
        FINISH_REMOTE_CONNECT,      // remote channel state - finish the connection of remote channel
        DNS_RESPONSE,               // datagram channel state - getting response from dns server
        PROXYING,                    // proxying state
        CLOSE_CONNECTION              //Close connection when connection responce is failed or destination host is unavailable
    }
    protected KeyState state;
    public KeyState getState() {return state;}
    public BaseAttachment(KeyState state) {this.state = state;}
}
