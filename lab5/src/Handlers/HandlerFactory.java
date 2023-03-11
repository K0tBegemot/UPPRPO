package Handlers;

import Attachments.BaseAttachment;

import java.nio.channels.SelectionKey;

// getting key handler instance depending on its state
// all handlers instances are singleton, so keys with the same state are handled by single handler

public class HandlerFactory {
    private static AcceptHandler acceptHandler;
    private static InitRequestHandler initRequestHandler;
    private static InitResponseHandler initResponseHandler;
    private static ConnectionRequestHandler connectionRequestHandler;
    private static ConnectionResponseHandler connectionResponseHandler;
    private static DnsResponseHandler dnsResponseHandler;
    private static FinishConnectionHandler finishConnectionHandler;
    private static ProxyingHandler proxyingHandler;
    private static CloseChannelHandler closeChannelHandler;

    public static Handler getHandler(SelectionKey key) {
        BaseAttachment attachment = (BaseAttachment) key.attachment();
        Handler result = null;

        switch (attachment.getState()) {
            case ACCEPT:
                if (acceptHandler == null) {
                    acceptHandler = new AcceptHandler();
                }
                result = acceptHandler;
                break;
            case INIT_REQUEST:
                if (initRequestHandler == null) {
                    initRequestHandler = new InitRequestHandler();
                }
                result = initRequestHandler;
                break;
            case INIT_RESPONSE_FAILED:
            case INIT_RESPONSE_SUCCESS:
                if (initResponseHandler == null) {
                    initResponseHandler = new InitResponseHandler();
                }
                result = initResponseHandler;
                break;
            case CONNECT_RESPONSE_FAILED:
            case CONNECT_RESPONSE_UNAVAILABLE:
            case CONNECT_RESPONSE_SUCCESS:
                if(connectionResponseHandler == null) {
                    connectionResponseHandler = new ConnectionResponseHandler();
                }
                result = connectionResponseHandler;
                break;
            case CONNECT_REQUEST:
                if (connectionRequestHandler == null) {
                    connectionRequestHandler = new ConnectionRequestHandler();
                }
                result = connectionRequestHandler;
                break;
            case DNS_RESPONSE:
                if (dnsResponseHandler == null) {
                    dnsResponseHandler = new DnsResponseHandler();
                }
                result = dnsResponseHandler;
                break;
            case FINISH_REMOTE_CONNECT:
                if (finishConnectionHandler == null) {
                    finishConnectionHandler = new FinishConnectionHandler();
                }
                result = finishConnectionHandler;
                break;
            case PROXYING:
                if (proxyingHandler == null) {
                    proxyingHandler = new ProxyingHandler();
                }
                result = proxyingHandler;
                break;
            default:
                assert false;
        }
        return result;
    }
    public static Connector getConnector() {
        assert connectionRequestHandler != null;
        return connectionRequestHandler;
    }
    public static Handler getChannelCloser() {
        if(closeChannelHandler == null) {
            closeChannelHandler = new CloseChannelHandler();
        }
        return closeChannelHandler;
    }
}
