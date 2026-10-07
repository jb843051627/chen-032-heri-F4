package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.THeriRemindDispatchMapper;
import com.fc.v2.mapper.auto.THeriRemindRowMapper;
import com.fc.v2.mapper.auto.THeriRemindRuleMapper;
import com.fc.v2.mapper.auto.THeriRemindRunMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriRemindDispatch;
import com.fc.v2.model.auto.THeriRemindRow;
import com.fc.v2.model.auto.THeriRemindRule;
import com.fc.v2.model.auto.THeriRemindRun;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.RemindAuditResult;
import com.fc.v2.model.custom.RemindRowCmd;
import com.fc.v2.model.custom.RemindRunReceipt;
import com.fc.v2.service.ITHeriRemindService;
import com.fc.v2.util.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 到港与滞期提醒排期 Service实现。
 *
 * <p>口径全在值班交接单上，交接单里没有的一律不加：</p>
 * <ol>
 *   <li>提前量港里判据说了算：写几天就几天；那格空着按一日；负数/说不上来的大数按空着办；</li>
 *   <li>同单多条：先改定得晚的(revised_at)，一样晚看到口(due_at)更近的，再平看册上行次(row_no)；
 *       定死一条，其余不吭声；同一天同一条不许连发两回；</li>
 *   <li>日子往前挪只动没发出去的；已发出去的留在原处，不追回重发、不挪到后头；</li>
 *   <li>中间一条发失败：跳过接着走下一条，留痕记它自己不顺，不是没轮到；</li>
 *   <li>前头一条退了：后头照走，留痕只点一句接着一桩退件往下走；</li>
 *   <li>册上压根没有对应条目：整趟跳空，不顶替不造一条，跳空写一个字：无；</li>
 *   <li>条数多按批分页拉，拉到一半超时停下留一笔，续的时候接着拉；</li>
 *   <li>抽验随机抓一条倒着核，认上的跟真发出去的对不对得上，对不齐的按条点名。
 *       往年入册的老提醒一条不减。</li>
 * </ol>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class THeriRemindServiceImpl implements ITHeriRemindService {

    /** 那格空着按一日 */
    public static final int DEFAULT_LEAD_DAYS = 1;
    /** 一年的口径压在这张单子上：比一年还大的数就是“说不上来的大数”，按空着办 */
    public static final int MAX_LEAD_DAYS = 365;
    /** 默认每页拉几条 */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /** 行状态：0待提醒 1已提醒(已发终态) 2已退(退的是还没发出去的) */
    private static final int ROW_PENDING = 0;
    private static final int ROW_REMINDED = 1;
    private static final int ROW_CANCELED = 2;

    private static final SnowflakeIdWorker ID_WORKER = new SnowflakeIdWorker(3, 3);
    private static final Random RANDOM = new Random();

    /** 一批单一趟锁：同批复发串行，异批各跑各的 */
    private final ConcurrentHashMap<String, Object> batchLocks = new ConcurrentHashMap<String, Object>();

    @Resource
    private THeriRemindRuleMapper remindRuleMapper;
    @Resource
    private THeriRemindRowMapper remindRowMapper;
    @Resource
    private THeriRemindDispatchMapper remindDispatchMapper;
    @Resource
    private THeriRemindRunMapper remindRunMapper;
    @Resource
    private TSysOperLogMapper sysOperLogMapper;

    // ---------------- 入册 / 前挪 / 退件 ----------------

    @Override
    public THeriRemindRow register(RemindRowCmd cmd, String operator) {
        if (cmd == null || trim(cmd.getBillNo()) == null) {
            throw new IllegalArgumentException("单据号空着，提醒入不了册");
        }
        if (cmd.getDueAt() == null) {
            throw new IllegalArgumentException("到口时刻空着，提醒入不了册");
        }
        final String billNo = cmd.getBillNo().trim();
        synchronized (lockOf(billNo)) {
            int nextRowNo = listRows(billNo).size() + 1;
            Date now = new Date();
            THeriRemindRow r = new THeriRemindRow();
            r.setBillNo(billNo);
            r.setRowNo(nextRowNo); // 行次册上排，交单方不插手
            r.setShipName(trim(cmd.getShipName()));
            r.setDueAt(cmd.getDueAt());
            r.setLeadDays(resolveLeadDays(cmd.getRuleCode(), cmd.getLeadDaysRaw()));
            r.setRuleCode(trim(cmd.getRuleCode()));
            // 改定时刻：头一趟入册就按这趟时刻；空着后续没法比“改得晚”
            r.setRevisedAt(cmd.getRevisedAt() != null ? cmd.getRevisedAt() : now);
            r.setSentFlag(0);
            r.setSkipFlag(0);
            r.setAfterCancelFlag(0);
            r.setStatus(ROW_PENDING);
            r.setDelFlag(0);
            r.setCreateBy(operator);
            r.setCreateTime(now);
            this.remindRowMapper.insert(r);
            writeTrail(billNo, "入册", operator,
                    "第 " + nextRowNo + " 行入册，到口=" + fmt(r.getDueAt())
                            + "，提前量判据说 " + r.getLeadDays() + " 天");
            return r;
        }
    }

    @Override
    public List<THeriRemindRow> listRows(String billNo) {
        if (billNo == null) {
            return new ArrayList<THeriRemindRow>();
        }
        return this.remindRowMapper.selectList(new QueryWrapper<THeriRemindRow>()
                .eq("bill_no", billNo).eq("del_flag", 0).orderByAsc("row_no"));
    }

    @Override
    public THeriRemindRow moveEarlier(String billNo, Integer rowNo, Date newDueAt, String operator) {
        THeriRemindRow r = requireRow(billNo, rowNo);
        // 只动还没发出去的；已发出去的留在原处，不追回来重发，也不挪到后头去
        if (r.getSentFlag() != null && r.getSentFlag() == 1) {
            throw new IllegalStateException(
                    "第 " + rowNo + " 行已经发出去了，留在原处，不追回重发也不挪到后头");
        }
        if (newDueAt == null || !newDueAt.before(r.getDueAt())) {
            throw new IllegalArgumentException("日子只能往前挪，新日子空着或没早于原到口，原样不动");
        }
        synchronized (lockOf(billNo)) {
            Date old = r.getDueAt();
            r.setDueAt(newDueAt);
            // 挪了日子就是这趟改定的：改定时刻跟着这趟走，先改定得晚的先跑
            r.setRevisedAt(new Date());
            r.setUpdateBy(operator);
            r.setUpdateTime(new Date());
            this.remindRowMapper.updateById(r);
            writeTrail(billNo, "日子前挪", operator,
                    "第 " + rowNo + " 行还没发出去，到口 " + fmt(old) + " 往前挪到 "
                            + fmt(newDueAt) + "；已发出去的不动");
            return r;
        }
    }

    @Override
    public THeriRemindRow cancel(String billNo, Integer rowNo, String operator) {
        THeriRemindRow r = requireRow(billNo, rowNo);
        if (r.getSentFlag() != null && r.getSentFlag() == 1) {
            throw new IllegalStateException(
                    "第 " + rowNo + " 行已经发出去了，追不回来，不能退");
        }
        if (r.getStatus() != null && r.getStatus() == ROW_CANCELED) {
            return r; // 退过就退过，不重复记
        }
        synchronized (lockOf(billNo)) {
            r.setStatus(ROW_CANCELED);
            r.setUpdateBy(operator);
            r.setUpdateTime(new Date());
            this.remindRowMapper.updateById(r);
            writeTrail(billNo, "退件", operator,
                    "第 " + rowNo + " 行还没发出去，退了；同单后头几条照走，不退件堵路");
            return r;
        }
    }

    // ---------------- 跑一趟排期 ----------------

    @Override
    public RemindRunReceipt runOnce(java.util.Collection<String> billNos, Date at,
                                    Integer pageSize, String operator) {
        final Date runAt = at != null ? at : new Date();
        final int size = pageSize != null && pageSize.intValue() > 0
                ? pageSize.intValue() : DEFAULT_PAGE_SIZE;
        List<String> bills = normalizeBills(billNos);
        if (bills.isEmpty()) {
            throw new IllegalArgumentException("这趟没有要点的单，跑不了");
        }
        final String batchKey = joinBills(bills);
        RemindRunReceipt rc = new RemindRunReceipt();
        rc.setPageSize(size);
        synchronized (lockOf(batchKey)) {
            // 续跑：这一批单还有半道超时停下的趟，接着它往下走，不另起一趟
            THeriRemindRun run = findRunning(bills);
            boolean continued = run != null;
            if (run == null) {
                run = newRun(bills, size, operator);
            }
            rc.setRunNo(run.getRunNo());
            rc.setContinued(continued);

            // 1) 分页拉，拉到一半超时就停下留一笔，续的时候接着拉
            Long afterId = run.getLastRowId();
            List<THeriRemindRow> pulled = new ArrayList<THeriRemindRow>();
            int alreadyFetched = run.getFetchedRows() == null ? 0 : run.getFetchedRows();
            while (true) {
                List<THeriRemindRow> page;
                try {
                    page = fetchPage(bills, afterId, size);
                } catch (RuntimeException e) {
                    // 拉到一半超时：本趟停下，已拉的留着，一条都不发，续跑从这个断点接着拉
                    run.setStatus(THeriRemindRun.ST_RUNNING);
                    run.setFetchedRows(alreadyFetched + pulled.size());
                    run.setLastRowId(afterId);
                    run.setRemark("拉到一半超时停下，从行 id=" + afterId + " 之后接着拉：" + e.getMessage());
                    run.setUpdateBy(operator);
                    run.setUpdateTime(new Date());
                    this.remindRunMapper.updateById(run);
                    writeTrail("/heri/remind/run/" + run.getRunNo(), "半道超时", operator,
                            "分页拉取拉到一半超时，已拉 " + (alreadyFetched + pulled.size())
                                    + " 条，停下留笔，续的时候从断点接着拉");
                    rc.setRunStatus(THeriRemindRun.ST_RUNNING);
                    rc.setFetchedThisCall(pulled.size());
                    rc.setPauseNote(run.getRemark());
                    return rc;
                }
                pulled.addAll(page);
                if (page.size() < size) {
                    break; // 拉到尾页
                }
                afterId = page.get(page.size() - 1).getId();
            }
            int fetchedTotal = alreadyFetched + pulled.size();
            run.setFetchedRows(fetchedTotal);
            clearLastRowId(run); // 拉齐了，续拉游标清掉（null 字段 updateById 不落，得显式置空）

            // 2) 册上压根没有对应条目：整趟跳空。不许拿别的单顶替，也不许顺手造一条。
            //    分页全拉完了累计一条没有（含半道超时后续跑拉完），才叫跳空；跳空写一个字：无
            if (fetchedTotal == 0) {
                run.setStatus(THeriRemindRun.ST_EMPTY);
                run.setEmptyFlag(1);
                run.setRemark("无");
                run.setUpdateBy(operator);
                run.setUpdateTime(new Date());
                this.remindRunMapper.updateById(run);
                writeTrail("/heri/remind/run/" + run.getRunNo(), "整趟跳空", operator, "无");
                rc.setRunStatus(THeriRemindRun.ST_EMPTY);
                rc.setEmpty(true);
                rc.setFetchedThisCall(0);
                return rc;
            }

            // 3) 分页只认“把在册条目拉到”；认条再把全批待提醒条目一次看齐：
            //    半道超时停下那趟已发过的条已转已提醒不会再进，没发成的仍是待提醒，
            //    续跑时头几页得重新看齐，不能只认本趟新拉的几页，不然漏掉前几页。
            List<THeriRemindRow> pendingAll = listPending(bills);
            // 只点这一天到口的：提醒时刻=到口往前挪提前量，落在跑这趟这一天
            List<THeriRemindRow> due = new ArrayList<THeriRemindRow>();
            for (THeriRemindRow r : pendingAll) {
                if (isSameDay(remindAt(r), runAt)) {
                    due.add(r);
                }
            }

            // 4) 同单多条：先改定得晚的；一样晚看到口更近的；再平看册上行次。定死一条
            Map<String, THeriRemindRow> winnerByBill = pickWinners(due);

            // 5) 按册上单序、行次逐趟发；同单只发认上的那一条，其余不吭声
            List<THeriRemindRow> winners = new ArrayList<THeriRemindRow>(winnerByBill.values());
            Collections.sort(winners, new Comparator<THeriRemindRow>() {
                @Override
                public int compare(THeriRemindRow a, THeriRemindRow b) {
                    int c = a.getBillNo().compareTo(b.getBillNo());
                    if (c != 0) {
                        return c;
                    }
                    return a.getRowNo().compareTo(b.getRowNo());
                }
            });

            int sent = run.getSentRows() == null ? 0 : run.getSentRows();
            int failed = run.getFailedRows() == null ? 0 : run.getFailedRows();
            int skipped = run.getSkippedRows() == null ? 0 : run.getSkippedRows();
            boolean anyAfterCancel = false;
            for (THeriRemindRow w : winners) {
                // 同一天不许连发两回：已发过的不重发（发失败的不算发出去，下趟还能走）
                if (sentTodayAlready(w, runAt)) {
                    skipped++;
                    w.setSkipFlag(1);
                    w.setUpdateBy(operator);
                    w.setUpdateTime(new Date());
                    this.remindRowMapper.updateById(w);
                    rc.getOutcomes().add(new RemindRunReceipt.Item(w.getBillNo(), w.getRowNo(),
                            RemindRunReceipt.OUT_ALREADY_TODAY, "这一条今天已经发过一回，同一天不许连发两回", false));
                    continue;
                }
                // 前头一条退了：后头几条照走，只在留痕上点一句是接着退件往下走的
                boolean afterCancel = hasCanceledSiblingBefore(w, runAt);
                if (afterCancel) {
                    anyAfterCancel = true;
                }
                if (dispatchOne(run, w, runAt, operator, afterCancel, rc)) {
                    sent++;
                } else {
                    failed++;
                }
            }
            // 认上的只有一条，同单其余不吭声——它们计入“没轮到”，但不留痕不发
            skipped += due.size() - winners.size();

            if (anyAfterCancel) {
                String note = "这趟是接着一桩退件往下走的，退件没把后头的路堵了";
                run.setRemark(note);
                rc.setCancelNote(note);
                writeTrail("/heri/remind/run/" + run.getRunNo(), "接退件", operator, note);
            }

            run.setStatus(THeriRemindRun.ST_DONE);
            run.setSentRows(sent);
            run.setFailedRows(failed);
            run.setSkippedRows(skipped);
            run.setUpdateBy(operator);
            run.setUpdateTime(new Date());
            this.remindRunMapper.updateById(run);
            writeTrail("/heri/remind/run/" + run.getRunNo(), "跑完", operator,
                    "拉到 " + fetchedTotal + " 条，本趟到口 " + due.size() + " 条，真发 "
                            + rc.sentCount() + " 条，发不顺跳过 " + rc.failedCount()
                            + " 条，没轮到 " + (due.size() - rc.sentCount() - rc.failedCount()) + " 条");
            rc.setRunStatus(THeriRemindRun.ST_DONE);
            rc.setFetchedThisCall(pulled.size());
            return rc;
        }
    }

    /** 同单多条的次序：先改定得晚的；一样晚看到口更近的；再平看册上行次。 */
    private Map<String, THeriRemindRow> pickWinners(List<THeriRemindRow> due) {
        Map<String, List<THeriRemindRow>> byBill =
                new LinkedHashMap<String, List<THeriRemindRow>>();
        for (THeriRemindRow r : due) {
            List<THeriRemindRow> list = byBill.get(r.getBillNo());
            if (list == null) {
                list = new ArrayList<THeriRemindRow>();
                byBill.put(r.getBillNo(), list);
            }
            list.add(r);
        }
        Map<String, THeriRemindRow> winners = new LinkedHashMap<String, THeriRemindRow>();
        for (Map.Entry<String, List<THeriRemindRow>> e : byBill.entrySet()) {
            List<THeriRemindRow> list = e.getValue();
            Collections.sort(list, new Comparator<THeriRemindRow>() {
                @Override
                public int compare(THeriRemindRow a, THeriRemindRow b) {
                    // 先改定得晚的（晚的在前）
                    int c = b.getRevisedAt().compareTo(a.getRevisedAt());
                    if (c != 0) {
                        return c;
                    }
                    // 一样晚，看到口更近的（早的在前）
                    c = a.getDueAt().compareTo(b.getDueAt());
                    if (c != 0) {
                        return c;
                    }
                    // 再平，看册上行次
                    return a.getRowNo().compareTo(b.getRowNo());
                }
            });
            winners.put(e.getKey(), list.get(0));
        }
        return winners;
    }

    /** 发一条：发失败跳过它接着走下一条，别让它把后头几条堵住；跳过的留痕：它自己不顺，不是没轮到。 */
    private boolean dispatchOne(THeriRemindRun run, THeriRemindRow w, Date runAt,
                              String operator, boolean afterCancel, RemindRunReceipt rc) {
        Date remindAt = remindAt(w);
        String dispatchNo = run.getRunNo() + ":" + w.getBillNo() + ":" + w.getRowNo();
        THeriRemindDispatch d = new THeriRemindDispatch();
        d.setDispatchNo(dispatchNo);
        d.setRunNo(run.getRunNo());
        d.setBillNo(w.getBillNo());
        d.setRowNo(w.getRowNo());
        d.setShipName(w.getShipName());
        d.setDueAt(w.getDueAt());
        d.setRemindAt(remindAt);
        d.setAfterCancelFlag(afterCancel ? 1 : 0);
        d.setDelFlag(0);
        d.setCreateBy(operator);
        d.setCreateTime(new Date());
        boolean ok;
        String failReason = null;
        try {
            sendOne(w, remindAt);
            ok = true;
            d.setStatus(THeriRemindDispatch.ST_SENT);
        } catch (Exception e) {
            ok = false;
            failReason = e.getMessage() == null ? "发送端没说缘由" : e.getMessage();
            d.setStatus(THeriRemindDispatch.ST_FAILED);
            d.setFailReason(failReason);
        }
        this.remindDispatchMapper.insert(d);

        if (ok) {
            // 真发出去才标已发：已发终态，日子再挪不动它，同一天也不会发第二回
            w.setSentFlag(1);
            w.setStatus(ROW_REMINDED);
            w.setLastDispatchNo(run.getRunNo());
            w.setFailReason(null);
            w.setSkipFlag(0);
            w.setAfterCancelFlag(afterCancel ? 1 : 0);
            w.setUpdateBy(operator);
            w.setUpdateTime(new Date());
            this.remindRowMapper.updateById(w);
            writeTrail(w.getBillNo(), "发出提醒", operator,
                    "第 " + w.getRowNo() + " 行于 " + fmt(remindAt) + " 真发出去"
                            + (afterCancel ? "（接着一桩退件往下走）" : ""));
            rc.getOutcomes().add(new RemindRunReceipt.Item(w.getBillNo(), w.getRowNo(),
                    RemindRunReceipt.OUT_SENT, "真发出去", afterCancel));
            return true;
        } else {
            // 跳过的那条要留一笔痕：它自己不顺，不是没轮到
            w.setSkipFlag(1);
            w.setFailReason(failReason);
            w.setAfterCancelFlag(afterCancel ? 1 : 0);
            w.setUpdateBy(operator);
            w.setUpdateTime(new Date());
            this.remindRowMapper.updateById(w);
            writeTrail(w.getBillNo(), "发不顺跳过", operator,
                    "第 " + w.getRowNo() + " 行自己发不顺（" + failReason
                            + "），跳过接着走下一条，不是没轮到"
                            + (afterCancel ? "；这趟接着一桩退件往下走" : ""));
            rc.getOutcomes().add(new RemindRunReceipt.Item(w.getBillNo(), w.getRowNo(),
                    RemindRunReceipt.OUT_FAILED, failReason, afterCancel));
            return false;
        }
    }

    // ---------------- 抽验：随机抓一条倒着核 ----------------

    @Override
    public RemindAuditResult auditBackward(String runNo) {
        return auditBackward(runNo, null);
    }

    @Override
    public RemindAuditResult auditBackward(String runNo, Long sampledDispatchId) {
        THeriRemindRun run = findRun(runNo);
        RemindAuditResult a = new RemindAuditResult();
        a.setRunNo(runNo);
        if (run == null) {
            a.getMismatches().add("趟头册上没有这一趟: " + runNo);
            a.setPass(false);
            return a;
        }
        List<THeriRemindDispatch> sent = this.remindDispatchMapper.selectList(
                new QueryWrapper<THeriRemindDispatch>()
                        .eq("run_no", runNo).eq("status", THeriRemindDispatch.ST_SENT)
                        .orderByDesc("id"));
        if (sent.isEmpty()) {
            a.getMismatches().add("这一趟没有真发出去的条可核: " + runNo);
            a.setPass(false);
            return a;
        }
        // 随机抓一条倒着核：认上的那条，跟这趟真发出去的那条对不对得上
        THeriRemindDispatch picked;
        if (sampledDispatchId != null) {
            picked = null;
            for (THeriRemindDispatch d : sent) {
                if (sampledDispatchId.equals(d.getId())) {
                    picked = d;
                    break;
                }
            }
            if (picked == null) {
                a.getMismatches().add("指定抽的那条不在这趟真发流水里: id=" + sampledDispatchId);
                a.setPass(false);
                return a;
            }
        } else {
            picked = sent.get(pickIndex(sent.size()));
        }
        a.setSampledBillNo(picked.getBillNo());
        a.setSampledRowNo(picked.getRowNo());

        // 先把抓的那条倒着核
        a.setSampleMatched(matchBook(picked, a));
        // 对不齐的几条按条点名，不许含糊过去：真发流水逐条倒核
        for (THeriRemindDispatch d : sent) {
            matchBook(d, a);
        }
        a.setPass(a.isSampleMatched() && a.getMismatches().isEmpty());
        return a;
    }

    /** 一条真发流水倒着核回条目册：对不上就按条点名（一条有几处不齐都并在这一条上） */
    private boolean matchBook(THeriRemindDispatch d, RemindAuditResult a) {
        THeriRemindRow r = findRow(d.getBillNo(), d.getRowNo());
        String where = "条[" + d.getBillNo() + " 第" + d.getRowNo() + "行]";
        List<String> problems = new ArrayList<String>();
        if (r == null) {
            a.getMismatches().add(where + "真发出去了，条目册上反倒没有这一条，对不上");
            return false;
        }
        if (r.getSentFlag() == null || r.getSentFlag() != 1) {
            problems.add("真发流水在，册上却没标已发出去");
        }
        if (r.getStatus() == null || r.getStatus() != ROW_REMINDED) {
            problems.add("真发流水在，册上状态却不是已提醒");
        }
        if (r.getDueAt() == null || d.getDueAt() == null
                || r.getDueAt().getTime() != d.getDueAt().getTime()) {
            problems.add("到口时刻跟真发流水对不上");
        }
        Date expect = remindAt(r);
        if (d.getRemindAt() == null || expect.getTime() != d.getRemindAt().getTime()) {
            problems.add("提醒时刻跟到口减提前量对不上");
        }
        if (!problems.isEmpty()) {
            a.getMismatches().add(where + String.join("、", problems.toArray(new String[0])));
            return false;
        }
        return true;
    }

    // ---------------- 趟头 / 流水 / 留痕 ----------------

    @Override
    public THeriRemindRun findRun(String runNo) {
        if (runNo == null) {
            return null;
        }
        return this.remindRunMapper.selectOne(new QueryWrapper<THeriRemindRun>()
                .eq("run_no", runNo).last("limit 1"));
    }

    @Override
    public List<THeriRemindDispatch> listDispatches(String runNo) {
        if (runNo == null) {
            return new ArrayList<THeriRemindDispatch>();
        }
        return this.remindDispatchMapper.selectList(new QueryWrapper<THeriRemindDispatch>()
                .eq("run_no", runNo).orderByAsc("id"));
    }

    @Override
    public List<TSysOperLog> listTrail(String billNo) {
        if (billNo == null) {
            return new ArrayList<TSysOperLog>();
        }
        return this.sysOperLogMapper.selectList(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", trailKey(billNo)).orderByAsc("oper_time").orderByAsc("id"));
    }

    // ---------------- 接缝（测试可覆写） ----------------

    /** 分页拉：id 比 afterId 大的往后拉，按批处理走。退了的/已发的也在册上（老提醒一条不减），拉取超时会抛异常，半道停下。 */
    protected List<THeriRemindRow> fetchPage(List<String> billNos, Long afterId, int pageSize) {
        QueryWrapper<THeriRemindRow> qw = new QueryWrapper<THeriRemindRow>()
                .eq("del_flag", 0)
                .in("bill_no", billNos)
                // 册上有这条就算数（待提醒/已提醒/已退都在册），整趟跳空只认真没条
                .in("status", ROW_PENDING, ROW_REMINDED, ROW_CANCELED)
                .gt("id", afterId == null ? 0L : afterId)
                .orderByAsc("id").last("limit " + pageSize);
        return this.remindRowMapper.selectList(qw);
    }

    /** 全批还没发出去、还没退的在册条目（认条用，分页拉的是“在册存在”） */
    protected List<THeriRemindRow> listPending(List<String> billNos) {
        return this.remindRowMapper.selectList(new QueryWrapper<THeriRemindRow>()
                .eq("del_flag", 0)
                .in("bill_no", billNos)
                .eq("status", ROW_PENDING)
                .orderByAsc("id"));
    }

    /** 真发一条提醒：默认空发（落痕用）；测试覆写它让某几条发不顺。 */
    protected void sendOne(THeriRemindRow row, Date remindAt) throws Exception {
        // 交接单没规定发送渠道，不自行添加；生产由下游覆写接入。
    }

    /** 随机抓一条：抽验口径用，测试可覆写成定死抓第几条。 */
    protected int pickIndex(int bound) {
        return RANDOM.nextInt(bound);
    }

    protected void writeTrail(String key, String action, String operator, String param) {
        TSysOperLog log = new TSysOperLog();
        log.setTitle("到港滞期提醒");
        log.setMethod(action);
        log.setOperName(operator);
        log.setOperUrl(trailKey(key));
        log.setOperParam(param);
        log.setOperTime(new Date());
        log.setCreateBy(operator);
        log.setCreateTime(new Date());
        this.sysOperLogMapper.insert(log);
    }

    // ---------------- 零碎口径 ----------------

    /**
     * 提前量从哪儿来：港里判据说了算。判据说几天就几天；那格空着按一日；
     * 写负数、写一个说不上来的大数，都按空着办——不是零，也不是一年半载。
     */
    private int resolveLeadDays(String ruleCode, String raw) {
        THeriRemindRule rule = null;
        String code = trim(ruleCode);
        if (code != null) {
            rule = this.remindRuleMapper.selectOne(new QueryWrapper<THeriRemindRule>()
                    .eq("rule_code", code).eq("status", THeriRemindRule.ST_ON)
                    .eq("del_flag", 0).last("limit 1"));
            if (rule != null && saneLead(rule.getLeadDays())) {
                return rule.getLeadDays();
            }
            // 判据撤了/找不到/那格空着或说不上来：都按空着办
            return DEFAULT_LEAD_DAYS;
        }
        Integer parsed = parseLead(raw);
        return parsed == null ? DEFAULT_LEAD_DAYS : parsed;
    }

    /** 在合理口径内的提前量才算数：0..365（一年压在这张单上，更大就是说不上来的大数） */
    private static boolean saneLead(Integer lead) {
        return lead != null && lead >= 0 && lead <= MAX_LEAD_DAYS;
    }

    /** 交单格原始写法：空/非数/负数/说不上来的大数 → 空(null，按一日)；不是零。 */
    private static Integer parseLead(String raw) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            return saneLead(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 提醒时刻 = 到口往前挪提前量那一天的同一时刻 */
    private static Date remindAt(THeriRemindRow r) {
        int lead = r.getLeadDays() == null ? DEFAULT_LEAD_DAYS : r.getLeadDays();
        Calendar c = Calendar.getInstance();
        c.setTime(r.getDueAt());
        c.add(Calendar.DAY_OF_MONTH, -lead);
        return c.getTime();
    }

    private static boolean isSameDay(Date a, Date b) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(a);
        Calendar cb = Calendar.getInstance();
        cb.setTime(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    /** 同一天同一条已真发过一回（发失败的不算） */
    private boolean sentTodayAlready(THeriRemindRow w, Date runAt) {
        List<THeriRemindDispatch> ds = this.remindDispatchMapper.selectList(
                new QueryWrapper<THeriRemindDispatch>()
                        .eq("bill_no", w.getBillNo()).eq("row_no", w.getRowNo())
                        .eq("status", THeriRemindDispatch.ST_SENT));
        for (THeriRemindDispatch d : ds) {
            if (isSameDay(d.getRemindAt(), runAt)) {
                return true;
            }
        }
        return false;
    }

    /** 同单前头是否有一条在本趟之前退了（退件不堵后头的路，只点一句） */
    private boolean hasCanceledSiblingBefore(THeriRemindRow w, Date runAt) {
        Integer cnt = this.remindRowMapper.selectCount(new QueryWrapper<THeriRemindRow>()
                .eq("bill_no", w.getBillNo())
                .eq("status", ROW_CANCELED)
                .lt("row_no", w.getRowNo()));
        return cnt != null && cnt > 0;
    }

    private THeriRemindRow requireRow(String billNo, Integer rowNo) {
        THeriRemindRow r = findRow(billNo, rowNo);
        if (r == null) {
            throw new IllegalArgumentException(
                    "册上没有这一条：" + billNo + " 第 " + rowNo + " 行");
        }
        return r;
    }

    private THeriRemindRow findRow(String billNo, Integer rowNo) {
        if (billNo == null || rowNo == null) {
            return null;
        }
        List<THeriRemindRow> rs = this.remindRowMapper.selectList(new QueryWrapper<THeriRemindRow>()
                .eq("bill_no", billNo).eq("row_no", rowNo).eq("del_flag", 0));
        return rs.isEmpty() ? null : rs.get(0);
    }

    private THeriRemindRun findRunning(List<String> bills) {
        List<THeriRemindRun> runs = this.remindRunMapper.selectList(new QueryWrapper<THeriRemindRun>()
                .eq("status", THeriRemindRun.ST_RUNNING)
                .eq("bill_nos", joinBills(bills)).orderByAsc("id"));
        return runs.isEmpty() ? null : runs.get(0);
    }

    /** 续拉游标清掉：MyBatis-Plus 更新默认不带 null 字段，得显式置空 */
    private void clearLastRowId(THeriRemindRun run) {
        run.setLastRowId(null);
        this.remindRunMapper.update(null, new UpdateWrapper<THeriRemindRun>()
                .set("last_row_id", null).eq("id", run.getId()));
    }

    private THeriRemindRun newRun(List<String> bills, int pageSize, String operator) {
        Date now = new Date();
        THeriRemindRun run = new THeriRemindRun();
        run.setRunNo("R" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(now)
                + ID_WORKER.nextId());
        run.setBillNos(joinBills(bills));
        run.setStatus(THeriRemindRun.ST_RUNNING);
        run.setFetchedRows(0);
        run.setSentRows(0);
        run.setFailedRows(0);
        run.setSkippedRows(0);
        run.setEmptyFlag(0);
        run.setPageSize(pageSize);
        run.setLastRowId(null);
        run.setDelFlag(0);
        run.setCreateBy(operator);
        run.setCreateTime(now);
        this.remindRunMapper.insert(run);
        return run;
    }

    private static List<String> normalizeBills(java.util.Collection<String> billNos) {
        List<String> bills = new ArrayList<String>();
        if (billNos != null) {
            for (String b : billNos) {
                String t = trim(b);
                if (t != null && !bills.contains(t)) {
                    bills.add(t);
                }
            }
        }
        Collections.sort(bills);
        return bills;
    }

    private static String joinBills(List<String> bills) {
        StringBuilder sb = new StringBuilder();
        for (String b : bills) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(b);
        }
        return sb.toString();
    }

    private Object lockOf(String key) {
        Object exist = this.batchLocks.get(key);
        if (exist != null) {
            return exist;
        }
        Object fresh = new Object();
        Object old = this.batchLocks.putIfAbsent(key, fresh);
        return old == null ? fresh : old;
    }

    private static String trailKey(String key) {
        if (key.startsWith("/heri/")) {
            return key;
        }
        return "/heri/remind/" + key;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String fmt(Date d) {
        return d == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}
