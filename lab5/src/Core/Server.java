package Core;

import Attachments.BaseAttachment;
import Attachments.BaseAttachment.KeyState;
import Attachments.DnsAttachment;
import DNS.DomainNameResolver;
import Exceptions.ServerException;
import Exceptions.SocksException;
import Handlers.Handler;
import Handlers.HandlerFactory;
import Logger.GlobalLogger;
import Logger.LogWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class Server {
    // get global loggers references:
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);
    private static GlobalLogger exceptionLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.EXCEPTION_LOGGER);

    private static boolean started = false; // is server started (to prevent repeating execution)
    private static boolean stopped = false; // is server stopped (flag of exiting from handle while loop)

    private Selector selector;
    private int port;

    private Server(int port, GlobalLogger.Mode mode) throws ServerException {
        this.port = port;
        workflowLogger.setMode(mode);
        exceptionLogger.setMode(mode);
        // create selector and register Server channel and datagram channel:
        initServer();
    }
    // server init methods:
    private void initServer() throws ServerException {
        try {
            selector = Selector.open();
        } catch (IOException e) {throw new ServerException(ServerException.Types.INIT, "Can't open the selector", "");}
        initServerSocketChannel();
        initDatagramChannel();
    }
    private void initServerSocketChannel() throws ServerException {
        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT, new BaseAttachment(KeyState.ACCEPT));
        }
        catch (IOException e) {
            throw new ServerException(ServerException.Types.INIT, "Can't init server socket channel", "");
        }
    }
    private void initDatagramChannel() throws ServerException {
        try {
            DatagramChannel udpChannel = DatagramChannel.open();
            udpChannel.configureBlocking(false);
            udpChannel.register(selector, SelectionKey.OP_READ, new DnsAttachment(KeyState.DNS_RESPONSE)); /////////////////////////////////////////////
            DomainNameResolver.createResolver(udpChannel);  // init domain name resolver////////////////////////////////////////////////////////////////
        }
        catch (IOException e) {
            throw new ServerException(ServerException.Types.INIT, "can't init datagram channel", "");
        }
    }

    // main handle loop
    private void handleClients() throws Exception {
        while(!Server.stopped) {
            try {
                if (selector.select() > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    System.err.println("");
                    System.err.println("/////////////////////////////////////////////////////////////////////////////////////");
                    System.err.println("");
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    SelectionKey curKey;
                    while (keyIterator.hasNext()) {
                        curKey = keyIterator.next();
                        // remove key from set to prevent handling it twice (selector doesn't remove key by itself)
                        keyIterator.remove();

                        // handle current key
                        try {handle(curKey);}
                        catch (Exception e) {
                            System.err.println("@@@");
                            if (!(e instanceof SocksException)) {
                                LogWriter.logException(e, exceptionLogger, "");
                                throw e;    // if not SOCKS exception was thrown: re-throw it
                            }
                            else {
                                LogWriter.logException(e, exceptionLogger, "");
                            }
                        }
                    }
                }
            }
            catch(IOException e) {
                LogWriter.logException(e, exceptionLogger, "");
                throw new ServerException(ServerException.Types.SELECT, "exception while selector's selection method", "");
            }
        }
        closeChannels();
        // update flags
        Server.stopped = false;
        Server.started = false;
    }
    // handle single key method
    private void handle(SelectionKey key) throws Exception {
        Handler keyHandler = HandlerFactory.getHandler(key);
        keyHandler.handle(key);
    }
    // close all channels (server interrupt case)
    private void closeChannels() {
        LogWriter.logWorkflow("close channels and selector..", workflowLogger);

        Set<SelectionKey> keys = selector.keys();
        // close all valid channels:
        for (SelectionKey key: keys) {
            if (key.isValid()) {
                SelectableChannel channel = key.channel();
                try {
                    channel.close();
                } catch (IOException e) {
                    LogWriter.logException(e, exceptionLogger, "can't close channel");
                }
            }
        }
        // close the selector:
        try {
            selector.close();
        }
        catch (IOException e) {
            LogWriter.logException(e, exceptionLogger, "can't close selector");
        }
        LogWriter.logWorkflow("channels and selector are closed", workflowLogger);
    }

    // main server start method
    public static void start(int port, GlobalLogger.Mode mode) {
        if (!Server.started) {
            Server.started = true;
            Server instance = null;

            // create server instance:
            try {
                instance = new Server(port, mode);
            } catch (Exception e) {
                LogWriter.logException(e, exceptionLogger, "");
                System.exit(1);
            }
            DomainNameResolver resolver = DomainNameResolver.getResolver();/////////////////////////////////////////////////////////////
            LogWriter.logWorkflow("server started at the port " + port, workflowLogger);
            // handle clients and start dns resolver:
            try {
                resolver.start();//////////////////////////////////////////////////////////////////////////////////////////////////
                instance.handleClients();
            }
            catch (Exception e) {
                e.printStackTrace();
                // stop the resolver; log exception; close the channels
                resolver.stopResolver();/////////////////////////////////////////////////////////////////////////////////////////////////
                LogWriter.logException(e, exceptionLogger, "");
                instance.closeChannels();
            }
        }
        // update flags
        Server.stopped = false;
        Server.started = false;
    }
    // stopping the server
    public static void stop() {
        LogWriter.logWorkflow("server stopped", workflowLogger);
        stopped = true;
    }
}
