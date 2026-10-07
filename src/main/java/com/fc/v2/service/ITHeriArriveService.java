package com.fc.v2.service;

import com.fc.v2.model.auto.THeriArriveBill;
import com.fc.v2.model.auto.THeriArriveRow;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.ArriveAuditResult;
import com.fc.v2.model.custom.ArriveDispatchCmd;
import com.fc.v2.model.custom.ArriveDispatchReceipt;
import com.fc.v2.model.custom.ArriveRegisterCmd;
import com.fc.v2.model.custom.ArriveRescheduleCmd;

import java.util.Date;
import java.util.List;

/**
 * 到港与滞期提醒排期 Service接口。口径全照值班交接单，单里没有的一律不加：
 * <ul>
 *   <li>提前量港里判据说了算：写几天就几天；那格空着、写负数、写说不上来的大数，
 *       都按空着办——按一日，不是 0、也不是一年半载；</li>
 *   <li>同单多条提醒只认一条发：先改定得晚的，一样晚看到口更近的；认死那一条其余不吭声，
 *       同一天不许连发两回；</li>
 *   <li>日子要往前挪只动还没发出去的；已发的留在原处，不追回重发、也不挪到后头；</li>
 *   <li>中间一条发失败：跳过它接着走下一条，别堵住后头；跳过那条留痕——它自己不顺，不是没轮到；</li>
 *   <li>前头一条退了：后头几条照走，留痕点一句“接着一桩退件往下走”，退件不堵后头的路；</li>
 *   <li>册上压根没有对应条目：整趟跳空，不许拿别的单顶替、也不许顺手造一条，写一个字“无”；</li>
 *   <li>认哪一条过三处看（判据册/单册/条目），落在同一处以册上为准（注册时誊死，改判据不追老册）；</li>
 *   <li>一趟要认的条数多按批处理走、分页拉；拉到一半超时停下留一笔，续的时候接着拉；</li>
 *   <li>往年入册的老提醒一条不减；</li>
 *   <li>抽验随机抓一条倒着核：认上的那条跟这趟真发出去的那条对不对得上，对不齐逐条点名。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITHeriArriveService {

    /** 按单号查单册 */
    THeriArriveBill selectByBillNo(String billNo);

    /**
     * 入册：一单连同多条提醒注册。提前量照注册时认的港里判据誊死在单册/条目上；
     * 提醒时刻=到口往前挪提前量。同号再注只照面（老条一条不减、不重排），原样回册子。
     * 判据撤下/过期/编号空 → 抛 IllegalArgumentException，不许自己挑个提前量。
     */
    THeriArriveBill register(ArriveRegisterCmd cmd, String operator);

    /** 单册上的提醒条目（按条目号排，往年老条都在） */
    List<THeriArriveRow> listRows(String billNo);

    /**
     * 跑一趟派发：按批分页拉要认的单，认一条发一条。
     * 发不顺跳过留痕不堵后头；册上无条目整趟跳空写“无”；超时停半路留断点，同趟号续拉。
     */
    ArriveDispatchReceipt dispatch(ArriveDispatchCmd cmd, String operator);

    /**
     * 日子往前挪：只动还没发出去的那条（改早到口、重算提醒时刻）；
     * 已发的留在原处（原样返回、不追不挪）；新日子不更早或条目不在册 → 抛异常。
     */
    THeriArriveRow reschedule(ArriveRescheduleCmd cmd, String operator);

    /**
     * 前头一条退了：把已发的那条记为已退回；后头待发条不堵，下趟带着“退件后续”照走。
     * 只能退在册且已发的条。
     */
    THeriArriveRow returnItem(String billNo, String itemNo, String operator);

    /** 倒着核一单：派发时认上的那条跟这趟真发出去的那条对不对得上，对不齐逐条点名。 */
    ArriveAuditResult auditOne(String billNo, Date at);

    /** 抽验：随机抓在册的一单倒着核（随机源在实现里可覆写，便于验）。册上无单回 null。 */
    ArriveAuditResult auditRandom(Date at);

    /** 一单的经手留痕（注册/派发/发不顺/改期/退件，按时刻升序） */
    List<TSysOperLog> listTrail(String billNo);
}
