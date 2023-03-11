package DNS;

import Attachments.BaseAttachment;
import Attachments.CompleteAttachment;
import Core.Constants.ResolverConstants;
import Core.Server;
import Exceptions.HandlerException;
import Exceptions.ResolverException;
import Exceptions.ResolverException.Types;
import Handlers.HandlerFactory;
import Logger.LogWriter;
import Logger.GlobalLogger;
import com.google.common.net.InetAddresses;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

/*
 Resolver provides methods for Main Thread which handle channels, and methods for it's own thread
 Main Thread: put new domain name on resolving and parse dns-server response
 Own thread: send datagrams with domain name resolve requests with pre set time interval
*/
public class DomainNameResolver extends Thread {
    private static GlobalLogger exceptionLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.EXCEPTION_LOGGER);
    private static GlobalLogger workflowLogger = GlobalLogger.LoggerCreator.getLogger(GlobalLogger.LoggerType.WORKFLOW_LOGGER);

    public static final class Const{
        public static final String INCORRECT_DOMAIN_NAME = "INCORRECT DOMAIN NAME";
    }

    // port + channel key
    public static class KeyData {
        private final int port;
        private final SelectionKey key;

        public KeyData(int port, SelectionKey key) {
            this.port = port;
            this.key = key;
        }
    }

    public static class NRRequestInfo {
        public static final class Const
        {
            //if we catch NUMBER_FOR_NEW_REQ or more DNS requests about concrete address and response is not received yet, when we send new request
            public static final int NUMBER_FOR_NEW_REQ = 3;
            //if numberOfRequests will be equal to NUMBER_REQ_SEND_ATTEMPT then server will send CONNECT_RESPONSE_UNAVAILABLE to client host
            public static final int NUMBER_REQ_SEND_ATTEMPT = 3;
        }
        //if true then request was send earlier, if false then request will be sent after
        private boolean isRequestSend;
        //number of new requests about concrete address that was taken into account after sending last request
        private int numberOfNewRegistrations;
        //number of attempting send DNS request about concrete address
        private int numberOfRequests;
        //

        public NRRequestInfo()
        {
            this.isRequestSend = false;
            this.numberOfNewRegistrations = 0;
            this.numberOfRequests = 0;
        }
        public void recordRequest()
        {
            if(!this.isRequestSend) {
                this.isRequestSend = true;
            }
            this.numberOfRequests += 1;
            this.numberOfNewRegistrations = 0;
        }
        public void addRegistration()
        {
            this.numberOfNewRegistrations += 1;
        }
        public boolean getIsRequestSend()
        {
            return this.isRequestSend;
        }
        public int getNumberOfNewRegistrations()
        {
            return this.numberOfNewRegistrations;
        }
        public int getNumberOfRequests()
        {
            return this.numberOfRequests;
        }
    }
    // key - domain name; value - list of key data
    private HashMap<String, List<KeyData>> unresolvedDomainNameMap;
    // key - domain name; value - resolved ip address (string representation)
    private HashMap<String, String> resolvedDomainNameMap;
    // key - domain name; value -  NRRequestInfo
    private HashMap<String, NRRequestInfo> requestConditionMap;

    // channel for sending and receiving dns datagrams
    private DatagramChannel resolverChannel;
    // address of available dns server
    private final InetSocketAddress dnsServerAddress;

    private boolean stopped = false;

    // private constructor for single-ton
    private DomainNameResolver(DatagramChannel channel){
        resolverChannel = channel;
        resolvedDomainNameMap = new HashMap<>(ResolverConstants.CACHE_CAPACITY);
        unresolvedDomainNameMap = new HashMap<>();
        requestConditionMap = new HashMap<>();

        dnsServerAddress = ResolverConfig.getCurrentConfig().servers().get(0);
    }
    // single-ton helper:
    private static DomainNameResolver resolver;
    public static void createResolver(DatagramChannel channel) {
        if (resolver == null) {
            resolver = new DomainNameResolver(channel);
        }
    }
    public static DomainNameResolver getResolver() {
        return resolver;
    }

//methods called by Own Thread:
    // sending dn-resolving requests for all unresolved names
    private synchronized void sendRequests() throws HandlerException {
        if (unresolvedDomainNameMap.size() == 0) {
            try {
                wait();
            }catch (Exception e)
            {
                LogWriter.logWorkflow("Interrupted Exception was catched while sendRequest in RequesterThread. This exception will be rethrowned", workflowLogger);
                //throw e;
            }
        }
        Set<String> keySet = unresolvedDomainNameMap.keySet();
            for (String key : keySet) {
                sendRequest(key);
            }
    }

    // method of sending domain name resolving datagrams
    private synchronized void sendRequest(String domainName) throws HandlerException {
        NRRequestInfo domainRequestInfo = requestConditionMap.get(domainName);
        if(domainRequestInfo != null) {
            if (domainRequestInfo.getIsRequestSend() && ((domainRequestInfo.getNumberOfRequests() == NRRequestInfo.Const.NUMBER_REQ_SEND_ATTEMPT
                    && domainRequestInfo.numberOfNewRegistrations >= NRRequestInfo.Const.NUMBER_FOR_NEW_REQ) || domainRequestInfo.getNumberOfRequests() > NRRequestInfo.Const.NUMBER_REQ_SEND_ATTEMPT) ) {
                connectUnavailable(domainName);
                return;
            }
            if (domainRequestInfo.getIsRequestSend() && domainRequestInfo.getNumberOfRequests() == NRRequestInfo.Const.NUMBER_REQ_SEND_ATTEMPT
                    && domainRequestInfo.numberOfNewRegistrations < NRRequestInfo.Const.NUMBER_FOR_NEW_REQ) {
                return;
            }
            if (domainRequestInfo.getIsRequestSend() && domainRequestInfo.getNumberOfRequests() < NRRequestInfo.Const.NUMBER_REQ_SEND_ATTEMPT
                    && domainRequestInfo.numberOfNewRegistrations >= NRRequestInfo.Const.NUMBER_FOR_NEW_REQ) {
                sendChannelRequest(domainName);
                domainRequestInfo.recordRequest();
                return;
            }
            if (domainRequestInfo.getIsRequestSend() && domainRequestInfo.getNumberOfRequests() < NRRequestInfo.Const.NUMBER_REQ_SEND_ATTEMPT
                    && domainRequestInfo.numberOfNewRegistrations < NRRequestInfo.Const.NUMBER_FOR_NEW_REQ) {
                return;
            }
            //if we are here then domainRequestInfo.getIsRequestSend() == false so we make the first send request
            sendChannelRequest(domainName);
            domainRequestInfo.recordRequest();
        }else {
            LogWriter.logWorkflow("FATAL ERROR: NRRequestInfo is null while sending DNS Request. Server will be terminated.", workflowLogger);
            throw new HandlerException(null, HandlerException.Types.DNS_RESOLVER_ERROR, "FATAL ERROR: NRRequestInfo is null while sending DNS Request.", "");
        }
    }

    private void sendChannelRequest(String domainName) throws HandlerException
    {
        try {
            Message request = createDNSmessage(domainName);
            byte[] requestBytes = request.toWire();
            resolverChannel.send(ByteBuffer.wrap(requestBytes), dnsServerAddress);
        }
        catch (IOException e) {
            LogWriter.logWorkflow("FATAL ERROR. IOException was catched while send request to DNS is proceed.", workflowLogger);
            throw new HandlerException(e, HandlerException.Types.DNS_CHANNEL_WRITE_IMPOSSIBLE, "FATAL ERROR. Can't send DNS Request. Server will be terminated", domainName);
        }
        catch (Exception e) {
            LogWriter.logWorkflow("ERROR. Incorrect DomainName format. This connection will be closed", workflowLogger);
            List<KeyData> list = unresolvedDomainNameMap.get(domainName);
            for(int i = 0; i < list.size(); i++)
            {
                KeyData tmp = list.get(i);
                ((CompleteAttachment)tmp.key.attachment()).setState(BaseAttachment.KeyState.CONNECT_RESPONSE_FAILED);
                tmp.key.interestOps(SelectionKey.OP_WRITE);
            }
            unresolvedDomainNameMap.remove(domainName);
            requestConditionMap.remove(domainName);
        }
    }

    private Message createDNSmessage(String name) throws TextParseException {
        Message result = new Message();

        Header header = new Header();
        header.setFlag(Flags.RD);                   // recursion available
        header.setOpcode(Opcode.QUERY);             // standard dns-query
        Record record = Record.newRecord(new Name(name + "."), Type.A, DClass.IN);  // Type - 'Address', Class - 'Internet' //////////////////////////////// + "."
        result.setHeader(header);
        result.addRecord(record, Section.QUESTION); // write record in 'Question' section of the message
        return result;
    }

    // Resolver Thread send's dns datagrams for unresolved names
    @Override
    public void run() {
        while(!stopped) {
            try {
                sendRequests();
            }
            catch (Exception e) {
                // stop server log the exception and stop the resolver
                LogWriter.logWorkflow("FATAL ERROR. Exception was catched while sending DNS requests. Stop requester", workflowLogger);
                LogWriter.logWorkflow(e.getMessage(), workflowLogger);
                Server.stop();
                stopResolver();
            }
        }
    }
//methods called by Main Thread:
    // main method
    public void resolveDomainName(String name, KeyData keyData) throws HandlerException{
        LogWriter.logWorkflow("Resolving domain name: " + name, workflowLogger);

        if (resolvedDomainNameMap.containsKey(name)) {
            String address = resolvedDomainNameMap.get(name);
            assert keyData.key.attachment() instanceof CompleteAttachment;

            LogWriter.logWorkflow(name + " Is already resolved: " + address, workflowLogger);

            // address is resolved so can establish the connection to remote channel
            CompleteAttachment attachment = (CompleteAttachment) keyData.key.attachment();

            try {attachment.setRemoteAddress(AddressGetter.getAddress(address, keyData.port));}
            catch (Exception e) {
                LogWriter.logWorkflow("Unknown host exception was catched in ConnectionRequestHandler. Server will be terminated", workflowLogger);
                throw new HandlerException(e, HandlerException.Types.DNS_RESOLVER_ERROR, "Uncorrect IP address" + address + ":" + Integer.toString(keyData.port), "");
            }
            ((CompleteAttachment) keyData.key.attachment()).setState(BaseAttachment.KeyState.CONNECT_REQUEST);
            HandlerFactory.getConnector().connectToChannel(keyData.key);
        }
        else {
            LogWriter.logWorkflow(name + " - Unresolved yet; wait dns-server response", workflowLogger);
            putUnresolvedItem(name, keyData);
        }
    }

    // put new KeyData item for resolving
    private synchronized void putUnresolvedItem(String dn, KeyData keyData) {
        if (!unresolvedDomainNameMap.containsKey(dn)) {
            List<KeyData> list = new LinkedList<>();
            list.add(keyData);
            unresolvedDomainNameMap.put(dn, list);
        }
        else {
            unresolvedDomainNameMap.get(dn).add(keyData);
        }
        if(!requestConditionMap.containsKey(dn))
        {
            NRRequestInfo newInfo = new NRRequestInfo();
            newInfo.addRegistration();
            requestConditionMap.put(dn, newInfo);
        }else{
            requestConditionMap.get(dn).addRegistration();
        }
        notify();
    }

    // parse dns-server response
    public synchronized void parseResponse(byte[] responseData) throws HandlerException {
        try {
            LogWriter.logWorkflow("Parse dns-server response.", workflowLogger);
            Message response = new Message(responseData);
            List<Record> answers = response.getSection(Section.ANSWER);
            if (answers.size() == 0) {
                return;////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            }
            // check is host name has already been resolved
            String hostName = response.getQuestion().getName().toString();
            if(hostName.length() != 0)
            {
                hostName = hostName.substring(0, hostName.length() - 1); //In this string i delete dot in the end of the string
            }
            //
            if (resolvedDomainNameMap.containsKey(hostName)) {
                LogWriter.logWorkflow(hostName + " Already resolved - " + resolvedDomainNameMap.get(hostName), workflowLogger);
                return;// just drop datagram
            }
            for(int i = 0; i < answers.size(); i++) {
                String address = answers.get(i).rdataToString();
                if (InetAddresses.isInetAddress(address)) {
                    LogWriter.logWorkflow(hostName + " Resolved - " + address, workflowLogger);
                    // domain name is resolved so connect channels, remove it from 'unresolved' map and add to 'resolved' map:
                    connectAllChannels(hostName, address);
                    unresolvedDomainNameMap.remove(hostName);
                    requestConditionMap.remove(hostName);
                    resolvedDomainNameMap.put(hostName, address);
                    return;
                }
            }
            LogWriter.logWorkflow("ERROR. Not a query DNS request. This connection will be closed", workflowLogger);
            connectUnavailable(hostName);
        }
        catch (IOException e) {
            LogWriter.logWorkflow("IOException was catched while DNSResponseHandler work. Server will be terminated", workflowLogger);
            throw new HandlerException(e, HandlerException.Types.DNS_RESOLVER_ERROR, "DNS resolver error. Server will be terminated", "");
        }
    }

    // connect all channels with resolved domain name
    private void connectAllChannels(String domainName, String address){
        List<KeyData> list = unresolvedDomainNameMap.get(domainName);
        if(list != null) {
            for (KeyData item : list) {
                // set remote address in key attachment and connect channel:
                InetSocketAddress dstAddress = new InetSocketAddress(address, item.port);
                CompleteAttachment attachment = (CompleteAttachment) item.key.attachment();
                attachment.setRemoteAddress(dstAddress);

                HandlerFactory.getConnector().connectToChannel(item.key);
            }
        }else {
            LogWriter.logWorkflow("DNS Responce: " + domainName + " . Address: " + address + " wasn't send by this socks server. Result will be treated is resolved value", workflowLogger);
        }
    }

    private void connectUnavailable(String domainName)
    {
        List<DomainNameResolver.KeyData> unresolvedList = unresolvedDomainNameMap.get(domainName);
        for (KeyData data : unresolvedList) {
            ((CompleteAttachment) data.key.attachment()).setState(BaseAttachment.KeyState.CONNECT_RESPONSE_UNAVAILABLE);
            data.key.interestOps(SelectionKey.OP_WRITE);
        }
        unresolvedDomainNameMap.remove(domainName);
        requestConditionMap.remove(domainName);
    }

    public void stopResolver() {
        this.stopped = true;
    }
}
