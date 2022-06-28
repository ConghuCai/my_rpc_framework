package site.conghucai.protocol.message;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public class RpcRequestMessage extends AbstractRpcMessage {

    private Object[] args;

    public RpcRequestMessage(Long sequence, Long timeStamp, String mapping, Object[] args) {
        super(sequence, timeStamp, true, false, mapping);
        this.args = args;
    }

    public RpcRequestMessage(Long sequence) {
        super(sequence, null, true, false, null);
    }
}
