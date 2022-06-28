package site.conghucai.protocol.message;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString(callSuper = true)
public abstract class AbstractRpcMessage implements Serializable {

    protected Long sequence;
    protected Long timeStamp;
    protected boolean isRequest;
    protected boolean isHeartBeat;
    protected String mapping;


    public AbstractRpcMessage() {
    }

    public AbstractRpcMessage(Long sequence, Long timeStamp, boolean isRequest, boolean isHeartBeat, String mapping) {
        this.sequence = sequence;
        this.timeStamp = timeStamp;
        this.isRequest = isRequest;
        this.isHeartBeat = isHeartBeat;
        this.mapping = mapping;
    }
}
