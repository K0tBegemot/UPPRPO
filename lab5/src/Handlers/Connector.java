package Handlers;

import Exceptions.HandlerException;

import java.nio.channels.SelectionKey;

// provides establishing the connection to the remote channel
// the host address should be resolved
public interface Connector {
    void connectToChannel(SelectionKey key);
}
