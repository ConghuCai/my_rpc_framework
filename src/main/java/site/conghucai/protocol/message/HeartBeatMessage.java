package site.conghucai.protocol.message;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public class HeartBeatMessage extends AbstractRpcMessage {
    public HeartBeatMessage() {
        super(null, null, false, true, null);
    }

    public HeartBeatMessage(Long sequence) {
        super(sequence, null, false, true, null);
    }

    public HeartBeatMessage(Long sequence, Long timeStamp) {
        super(sequence, timeStamp, false, true, null);
    }
}
