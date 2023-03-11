package Attachments;

import Core.Constants.ResolverConstants;
import java.nio.ByteBuffer;

public class DnsAttachment extends BaseAttachment {
    private ByteBuffer buffer;

    public DnsAttachment(KeyState state) {
        super(state);
        buffer = ByteBuffer.allocate(ResolverConstants.BUFF_SIZE);
    }
    public final ByteBuffer getBuffer() {return buffer;}
}
