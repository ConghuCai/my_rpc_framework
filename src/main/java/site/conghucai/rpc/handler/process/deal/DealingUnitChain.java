package site.conghucai.rpc.handler.process.deal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 处理链
 * 可添加处理单元，顺序执行各个处理单元的流程。
 * 由于Netty的线程-channel绑定  所有不需要考虑这里的线程安全问题。
 */
public class DealingUnitChain {

    private List<DealingUnit> dealingUnits = new ArrayList<>(4);

    /**
     * 添加一个处理单元到处理链中
     * @param deal
     */
    public void addDealingUnit(DealingUnit deal) {
        dealingUnits.add(deal);
    }

    /**
     * 按照dealingUnits的顺序，依次处理上下文。
     * @param context 业务上下文信息
     */
    public void dealAll(DealingContext context) {
        Iterator<DealingUnit> iterator = dealingUnits.iterator();
        while(iterator.hasNext()) { //遍历arr
            DealingUnit unit = iterator.next();
            unit.deal(context); //执行处理
        }
    }

}
