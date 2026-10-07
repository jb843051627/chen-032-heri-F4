package com.fc.v2.service;

import com.fc.v2.model.auto.THeriRemindDispatch;
import com.fc.v2.model.auto.THeriRemindRow;
import com.fc.v2.model.auto.THeriRemindRun;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.RemindAuditResult;
import com.fc.v2.model.custom.RemindRowCmd;
import com.fc.v2.model.custom.RemindRunReceipt;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 到港与滞期提醒排期 Service接口。
 *
 * <p>这趟值班交接单上的口径，一条不许添：</p>
 * <ul>
 *   <li>提前量从港里判据来：判据说几天就几天；那格空着按一日；
 *       写负数、写说不上来的大数都按空着办——不是零，也不是一年半载；</li>
 *   <li>同单多条提醒两个次序：先改定得晚的，一样晚看到口更近的，再平看册上行次；
 *       定死就是那一条，其余不吭声；同一天不许连发两回；</li>
 *   <li>日子要往前挪只动还没发出去的；已发出去的留在原处，不追回来重发、不挪到后头；</li>
 *   <li>中间一条发失败：跳过它接着走下一条，别堵住后头；跳过的留一笔痕——它自己不顺，不是没轮到；</li>
 *   <li>前头一条退了：后头几条照走，只在留痕上点一句这趟是接着一桩退件往下走的；</li>
 *   <li>册上压根没有对应条目：整趟跳空，不许拿别的单顶替、不许顺手造一条，跳空写一个字：无；</li>
 *   <li>一趟要认的条数多按批处理走，分页拉，拉到一半超时就停下留一笔，续的时候接着拉；</li>
 *   <li>抽验随机抓一条倒着核：认上的跟真发出去的对不对得上，对不齐的几条按条点名。
 *       往年入册的老提醒一条不减。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITHeriRemindService {

    /** 提醒条目入册：行次册上排，提前量按判据口径誊死。 */
    THeriRemindRow register(RemindRowCmd cmd, String operator);

    /** 同一单在册条目 */
    List<THeriRemindRow> listRows(String billNo);

    /**
     * 日子要往前挪：只动还没发出去的；已发出去的留在原处，
     * 不追回来重发，也不挪到后头去（newDueAt 必须早于现到口，否则原样顶回）。
     */
    THeriRemindRow moveEarlier(String billNo, Integer rowNo, Date newDueAt, String operator);

    /** 前头一条退了：退的是还没发出去的那条；退了留痕，后头几条照走。 */
    THeriRemindRow cancel(String billNo, Integer rowNo, String operator);

    /**
     * 跑一趟排期。
     *
     * @param billNos  这趟要认的那批单；册上压根没有对应条目，整趟跳空
     * @param at       排期时刻(空=此刻)，按天认到口
     * @param pageSize 分页拉取每页几条(空给册上默认)
     */
    RemindRunReceipt runOnce(Collection<String> billNos, Date at, Integer pageSize, String operator);

    /** 趟头 */
    THeriRemindRun findRun(String runNo);

    /** 这一趟真发出去的流水（发失败的痕也在） */
    List<THeriRemindDispatch> listDispatches(String runNo);

    /** 一条提醒的留痕（入册/前挪/退件/发不顺/接着退件往下走都在里头） */
    List<TSysOperLog> listTrail(String billNo);

    /** 抽验：随机抓一条真发流水倒着核，对不齐的几条按条点名 */
    RemindAuditResult auditBackward(String runNo);

    /** 抽验：指定抓哪条真发流水倒着核（验随机口径用，生产调随机版） */
    RemindAuditResult auditBackward(String runNo, Long sampledDispatchId);
}
