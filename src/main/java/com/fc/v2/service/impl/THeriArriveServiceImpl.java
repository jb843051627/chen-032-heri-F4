package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THeriArriveBillMapper;
import com.fc.v2.mapper.auto.THeriArriveRowMapper;
import com.fc.v2.mapper.auto.THeriArriveRuleMapper;
import com.fc.v2.mapper.auto.THeriArriveRunMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriArriveBill;
import com.fc.v2.model.auto.THeriArriveRow;
import com.fc.v2.model.auto.THeriArriveRule;
import com.fc.v2.model.auto.THeriArriveRun;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.ArriveAuditResult;
import com.fc.v2.model.custom.ArriveDispatchCmd;
import com.fc.v2.model.custom.ArriveDispatchReceipt;
import com.fc.v2.model.custom.ArriveItemInput;
import com.fc.v2.model.custom.ArriveItemResult;
import com.fc.v2.model.custom.ArriveRegisterCmd;
import com.fc.v2.model.custom.ArriveRescheduleCmd;
import com.fc.v2.service.ITHeriArriveService;
import com.fc.v2.util.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 到港与滞期提醒排期 Service实现。口径全照值班交接单，单里没有的一律不加。
 *
 * <p>两道账本：提醒条目落 t_heri_arr_row（append-only，往年老条一条不减），经手留痕落
 * t_sys_oper_log；派发趟次落 t_heri_arr_run（分页续拉的断点/整趟结果在这）。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class THeriArriveServiceImpl implements ITHeriArriveService {

    /** 那格空着（含负数/说不上来的大数）按一日，不是 0、也不是一年半载 */
    public static final int DEFAULT_LEAD_DAYS = 1;
    /** 说不上来的大数：比一年还多的提前量不当数，按空着办 */
    public static final int MAX_SANE_LEAD_DAYS = 365;

    /** 册态 0在册 1已发齐 */
    private static final int BILL_OPEN = 0;
    private static final int BILL_ALL_DONE = 1;

    private static final int RULE_ON = 0;

    /** 按批分页拉的默认每页单数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final SnowflakeIdWorker ID_WORKER = new SnowflakeIdWorker(2, 3);

    /** 一单一段锁：同单派发/改期/退件排队错开，异单互不踩 */
    private final ConcurrentHashMap<String, Object> billLocks = new ConcurrentHashMap<String, Object>();

    @Resource
    private THeriArriveRuleMapper arrRuleMapper;
    @Resource
    private THeriArriveBillMapper arrBillMapper;
    @Resource
    private THeriArriveRowMapper arrRowMapper;
    @Resource
    private THeriArriveRunMapper arrRunMapper;
    @Resource
    private TSysOperLogMapper sysOperLogMapper;

    // ================= 入册 =================

    @Override
    public THeriArriveBill selectByBillNo(String billNo) {
        if (billNo == null) {
            return null;
        }
        return this.arrBillMapper.selectOne(new QueryWrapper<THeriArriveBill>()
                .eq("bill_no", billNo).eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public THeriArriveBill register(ArriveRegisterCmd cmd, String operator) {
        if (cmd == null) {
            throw new IllegalArgumentException("入册命令空着，没法注册");
        }
        String ruleCode = trim(cmd.getRuleCode());
        if (ruleCode == null) {
            throw new IllegalArgumentException("没写认哪条港里判据，提前量定不了，不许自己挑");
        }
        Date now = new Date();
        // 提前量港里判据说了算：判据得在册、启用、在有效期内；否则不注册，绝不自己挑天数
        THeriArriveRule rule = usableRule(ruleCode, now);
        if (rule == null) {
            throw new IllegalArgumentException("判据 " + ruleCode + " 撤下了或不在有效期内，提前量定不了，退回重定");
        }
        final int leadDays = resolveLeadDays(rule.getLeadDays());

        String billNo = trim(cmd.getBillNo());
        if (billNo != null) {
            THeriArriveBill face = selectByBillNo(billNo);
            if (face != null) {
                // 同号再注只照面：往年入册的老提醒一条不减、不重排
                return face;
            }
        }
        if (billNo == null) {
            billNo = "A" + new java.text.SimpleDateFormat("yyyyMMdd").format(now)
                    + ID_WORKER.nextId();
        }
        final String no = billNo;
        List<ArriveItemInput> items = cmd.getItems() == null
                ? new ArrayList<ArriveItemInput>() : cmd.getItems();

        synchronized (lockOf(no)) {
            THeriArriveBill again = selectByBillNo(no);
            if (again != null) {
                return again;
            }
            THeriArriveBill bill = new THeriArriveBill();
            bill.setBillNo(no);
            bill.setShipName(trim(cmd.getShipName()));
            bill.setRuleCode(ruleCode);
            // 三处看法（判据册/单册/条目）落在同一处以册上为准：提前量注册这一刻誊死
            bill.setLeadDays(leadDays);
            bill.setStatus(BILL_OPEN);
            bill.setTotalRows(items.size());
            bill.setSentRows(0);
            bill.setFailRows(0);
            bill.setReturnRows(0);
            bill.setDelFlag(0);
            bill.setCreateBy(operator);
            bill.setCreateTime(now);

            // 先逐行校验，别落半册：条目号要有、不重；到口时刻要有（没它算不了提醒时刻，不许造）
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (ArriveItemInput in : items) {
                String itemNo = in == null ? null : trim(in.getItemNo());
                if (itemNo == null) {
                    throw new IllegalArgumentException("有条提醒没写条目编号，认不住条，整单退回");
                }
                if (!seen.add(itemNo)) {
                    throw new IllegalArgumentException("条目编号 " + itemNo + " 在本单里重复，认不住落哪条");
                }
                if (in.getDueAt() == null) {
                    throw new IllegalArgumentException("条目 " + itemNo + " 没写到口时刻，提醒时刻算不了，不许自己造");
                }
            }
            this.arrBillMapper.insert(bill);

            for (ArriveItemInput in : items) {
                Date revised = in.getRevisedAt() == null ? now : in.getRevisedAt();
                THeriArriveRow r = new THeriArriveRow();
                r.setBillNo(no);
                r.setItemNo(trim(in.getItemNo()));
                r.setDueAt(in.getDueAt());
                r.setLeadDays(leadDays);
                // 提醒时刻=到口往前挪提前量
                r.setRemindAt(minusDays(in.getDueAt(), leadDays));
                r.setRevisedAt(revised);
                r.setNotifyType(trim(in.getNotifyType()));
                r.setAmount(in.getAmount());
                r.setShipName(bill.getShipName());
                r.setStatus(THeriArriveRow.ST_PENDING);
                r.setAfterReturn(0);
                r.setDelFlag(0);
                r.setCreateBy(operator);
                r.setCreateTime(now);
                this.arrRowMapper.insert(r);
            }
            if (items.isEmpty()) {
                bill.setStatus(BILL_ALL_DONE); // 空册没有待发条，头上不挂在办
                this.arrBillMapper.updateById(bill);
            }
            writeTrail(no, "入册", operator,
                    "认判据 " + ruleCode + "，提前量誊死 " + leadDays
                            + " 天；入册提醒 " + items.size() + " 条，往年老条一条不减");
            return bill;
        }
    }

    /**
     * 提前量口径：判据说几天就几天；那格空着、写负数、写说不上来的大数，
     * 都按空着办——按一日，不是 0、也不是一年半载。
     */
    public static int resolveLeadDays(Integer raw) {
        if (raw == null || raw < 0 || raw > MAX_SANE_LEAD_DAYS) {
            return DEFAULT_LEAD_DAYS;
        }
        return raw;
    }

    private THeriArriveRule usableRule(String code, Date at) {
        THeriArriveRule rule = this.arrRuleMapper.selectOne(new QueryWrapper<THeriArriveRule>()
                .eq("rule_code", code).eq("status", RULE_ON).eq("del_flag", 0).last("limit 1"));
        if (rule == null) {
            return null;
        }
        if (rule.getEffStart() != null && at.before(rule.getEffStart())) {
            return null;
        }
        // 截止时刻不含，到点即过期
        if (rule.getEffEnd() != null && !at.before(rule.getEffEnd())) {
            return null;
        }
        return rule;
    }

    // ================= 派发（按批分页拉） =================

    @Override
    public ArriveDispatchReceipt dispatch(ArriveDispatchCmd cmd, String operator) {
        ArriveDispatchCmd c = cmd == null ? new ArriveDispatchCmd() : cmd;
        final Date at = c.getAt() == null ? new Date() : c.getAt();
        final int pageSize = c.getPageSize() == null || c.getPageSize().intValue() < 1
                ? DEFAULT_PAGE_SIZE : c.getPageSize().intValue();
        final Long timeoutMs = c.getTimeoutMs();
        final long startWall = System.currentTimeMillis();

        // 续拉：同趟号接着断点走；新开：立一趟
        THeriArriveRun run;
        String scopeBill = trim(c.getBillNo());
        if (trim(c.getRunNo()) != null) {
            run = this.arrRunMapper.selectOne(new QueryWrapper<THeriArriveRun>()
                    .eq("run_no", trim(c.getRunNo())).eq("del_flag", 0).last("limit 1"));
            if (run == null) {
                throw new IllegalArgumentException("趟号 " + c.getRunNo() + " 不在册上，没东西可续拉");
            }
            if (run.getStatus() != null && run.getStatus() == THeriArriveRun.ST_DONE) {
                // 走完的趟再叫一遍：照面，不重发
                return receiptOf(run, new ArrayList<ArriveItemResult>(), THeriArriveRun.ST_DONE);
            }
            scopeBill = run.getScopeBill(); // 续拉范围以原趟为准，不许半路换单顶替
        } else {
            run = new THeriArriveRun();
            run.setRunNo("R" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                    + ID_WORKER.nextId());
            run.setScopeBill(scopeBill);
            run.setStatus(THeriArriveRun.ST_PAGING);
            run.setCursorBill(null);
            run.setPageSize(scopeBill == null ? pageSize : null);
            run.setResultText(null);
            run.setSentRows(0);
            run.setFailRows(0);
            run.setReturnRows(0);
            run.setDelFlag(0);
            run.setCreateBy(operator);
            run.setCreateTime(new Date());
            this.arrRunMapper.insert(run);
        }

        List<ArriveItemResult> batchResults = new ArrayList<ArriveItemResult>();
        String cursor = run.getCursorBill();

        if (scopeBill != null) {
            // 派指定一单：册上压根没有这单/没条目，整趟跳空写“无”，不许拿别的单顶替
            THeriArriveBill bill = selectByBillNo(scopeBill);
            if (bill != null) {
                ArriveItemResult r = dispatchBill(bill, at, run.getRunNo(), operator);
                if (r != null) {
                    batchResults.add(r);
                }
            }
            finishRun(run, batchResults, operator, null, THeriArriveRun.ST_DONE);
            return receiptOf(run, batchResults, THeriArriveRun.ST_DONE);
        }

        // 按批处理走、分页拉
        boolean timedOut = false;
        while (true) {
            List<THeriArriveBill> page = listBillPage(cursor, pageSize);
            if (page.isEmpty()) {
                break; // 拉到头，走完
            }
            for (THeriArriveBill bill : page) {
                cursor = bill.getBillNo();
                ArriveItemResult r = dispatchBill(bill, at, run.getRunNo(), operator);
                if (r != null) {
                    batchResults.add(r);
                }
            }
            // 末页（不足一页）拉到即走完；整页拉完再看时限，超时就停在这一页之后留一笔
            if (page.size() < pageSize) {
                break;
            }
            if (timeoutMs != null && System.currentTimeMillis() - startWall >= timeoutMs.longValue()) {
                timedOut = true;
                break;
            }
        }

        if (timedOut) {
            // 停在半路：记断点 + 留一笔，已发的落账不回滚
            run.setStatus(THeriArriveRun.ST_PAGING);
            run.setCursorBill(cursor);
            run.setSentRows(nz(run.getSentRows()) + count(batchResults, true, false));
            run.setFailRows(nz(run.getFailRows()) + count(batchResults, false, true));
            run.setReturnRows(nz(run.getReturnRows()) + countAfterReturn(batchResults));
            run.setNote("按批分页拉到一半超时，停在 " + cursor + " 之后；续的时候用本趟号接着拉，已发的不重发");
            run.setUpdateBy(operator);
            run.setUpdateTime(new Date());
            this.arrRunMapper.updateById(run);
            writeRunTrail(run.getRunNo(), "超时停半", operator, run.getNote());
            return receiptOf(run, batchResults, THeriArriveRun.ST_PAGING);
        }

        finishRun(run, batchResults, operator, cursor, THeriArriveRun.ST_DONE);
        return receiptOf(run, batchResults, THeriArriveRun.ST_DONE);
    }

    /** 认条目按 bill_no 排序分页：从断点（不含）往后拉一页。 */
    private List<THeriArriveBill> listBillPage(String cursor, int pageSize) {
        QueryWrapper<THeriArriveBill> q = new QueryWrapper<THeriArriveBill>()
                .eq("del_flag", 0).orderByAsc("bill_no").last("limit " + pageSize);
        if (cursor != null) {
            q.gt("bill_no", cursor);
        }
        return this.arrBillMapper.selectList(q);
    }

    /**
     * 派这一单这一趟：到点的待发条里，先改定得晚的、一样晚看到口更近的，认死一条；
     * 其余不吭声。同一天已发过就不连发。发不顺跳过留痕，不堵后头。无候选回 null（不吭声）。
     */
    private ArriveItemResult dispatchBill(THeriArriveBill bill, Date at, String runNo, String operator) {
        final String billNo = bill.getBillNo();
        synchronized (lockOf(billNo)) {
            List<THeriArriveRow> due = this.arrRowMapper.selectList(new QueryWrapper<THeriArriveRow>()
                    .eq("bill_no", billNo).eq("status", THeriArriveRow.ST_PENDING)
                    .le("remind_at", at).eq("del_flag", 0));
            if (due.isEmpty()) {
                return null; // 这单这趟没有到口该认的条，不吭声
            }
            // 同一天不许连发两回：今天已有成功发出（且没被退回）的，这趟其余按住不发
            if (sentToday(billNo, at)) {
                return null;
            }
            // 认条次序：先改定得晚的(revised_at desc)；一样晚，看到口更近的(due_at asc)；
            // 再一样按条目号定个死次序，免得两条飘忽
            Collections.sort(due, new Comparator<THeriArriveRow>() {
                @Override
                public int compare(THeriArriveRow a, THeriArriveRow b) {
                    int c = b.getRevisedAt().compareTo(a.getRevisedAt());
                    if (c != 0) {
                        return c;
                    }
                    c = a.getDueAt().compareTo(b.getDueAt());
                    if (c != 0) {
                        return c;
                    }
                    return a.getItemNo().compareTo(b.getItemNo());
                }
            });
            THeriArriveRow chosen = due.get(0); // 认死这一条，其余不吭声

            // 前头一条退了：后头这一条照走，只点一句是接着一桩退件往下走的
            boolean afterReturn = hasReturned(billNo);

            try {
                deliver(chosen); // 真发动作（缝：测试可覆写捅故障）
            } catch (RuntimeException ex) {
                // 发不顺：跳过它接着走下一条，别堵住后头；留一笔痕——它自己不顺，不是没轮到
                chosen.setStatus(THeriArriveRow.ST_FAILED);
                chosen.setFailReason("发不顺，跳过不堵后头：" + ex.getMessage());
                chosen.setUpdateBy(operator);
                chosen.setUpdateTime(new Date());
                this.arrRowMapper.updateById(chosen);
                bill.setFailRows(nz(bill.getFailRows()) + 1);
                refreshBillStatus(bill, operator);
                writeTrail(billNo, "发不顺", operator,
                        "条目 " + chosen.getItemNo() + " 自己没发出去（" + ex.getMessage()
                                + "），留痕跳过，后头几条接着走");
                return new ArriveItemResult(billNo, chosen.getItemNo(), ArriveItemResult.FAILED,
                        false, afterReturn, chosen.getFailReason());
            }

            chosen.setStatus(THeriArriveRow.ST_SENT);
            chosen.setSendTime(at);
            chosen.setAfterReturn(afterReturn ? 1 : 0);
            chosen.setUpdateBy(operator);
            chosen.setUpdateTime(new Date());
            this.arrRowMapper.updateById(chosen);
            bill.setSentRows(nz(bill.getSentRows()) + 1);
            refreshBillStatus(bill, operator);
            writeTrail(billNo, "发出", operator,
                    "认上条目 " + chosen.getItemNo() + "（改定 " + fmt(chosen.getRevisedAt())
                            + "、到口 " + fmt(chosen.getDueAt()) + "），提前 "
                            + chosen.getLeadDays() + " 天，这趟真发出去"
                            + (afterReturn ? "；这趟是接着一桩退件往下走的" : ""));
            return new ArriveItemResult(billNo, chosen.getItemNo(), ArriveItemResult.SENT,
                    true, afterReturn,
                    afterReturn ? "接着一桩退件往下走" : null);
        }
    }

    /** 同一天（按业务时刻 at 那天）是否已有成功发出且未退回的条：有就不连发第二回。 */
    private boolean sentToday(String billNo, Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date dayStart = c.getTime();
        c.add(Calendar.DAY_OF_MONTH, 1);
        Date dayEnd = c.getTime();
        Integer cnt = this.arrRowMapper.selectCount(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("status", THeriArriveRow.ST_SENT)
                .ge("send_time", dayStart).lt("send_time", dayEnd));
        return cnt != null && cnt > 0;
    }

    private boolean hasReturned(String billNo) {
        Integer cnt = this.arrRowMapper.selectCount(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("status", THeriArriveRow.ST_RETURNED));
        return cnt != null && cnt > 0;
    }

    /**
     * 真发动作的缝：默认什么外路都不走（落库即发出凭证）；
     * 单测可覆写它让某条抛错，模拟通道抖了一下发不顺。
     */
    protected void deliver(THeriArriveRow row) {
        // no-op：本地排期不接真网关，try 不抛即视为真发出去
    }

    /** 一趟收尾：累计计数；一条都没真有下落（没发出也没发不顺）就整趟跳空写“无”。 */
    private void finishRun(THeriArriveRun run, List<ArriveItemResult> batch, String operator,
                          String cursor, int status) {
        int sent = nz(run.getSentRows()) + count(batch, true, false);
        int fail = nz(run.getFailRows()) + count(batch, false, true);
        int retr = nz(run.getReturnRows()) + countAfterReturn(batch);
        run.setSentRows(sent);
        run.setFailRows(fail);
        run.setReturnRows(retr);
        run.setStatus(status);
        run.setCursorBill(cursor);
        boolean empty = batch.isEmpty() && nz(run.getSentRows()) == 0 && nz(run.getFailRows()) == 0;
        if (empty) {
            // 册上压根没有对应条目：整趟跳空，写一个字“无”，不拿别的单顶替、不造条
            run.setResultText(THeriArriveRun.EMPTY_TEXT);
            writeRunTrail(run.getRunNo(), "跳空", operator,
                    "册上没有对应条目，整趟跳空，不顶替、不造条：无");
        } else {
            run.setResultText("发出 " + sent + " 条，发不顺 " + fail + " 条"
                    + (retr > 0 ? "，其中退件后续 " + retr + " 条" : ""));
            writeRunTrail(run.getRunNo(), "走完", operator, run.getResultText());
        }
        run.setUpdateBy(operator);
        run.setUpdateTime(new Date());
        this.arrRunMapper.updateById(run);
    }

    // ================= 日子往前挪 =================

    @Override
    public THeriArriveRow reschedule(ArriveRescheduleCmd cmd, String operator) {
        if (cmd == null) {
            throw new IllegalArgumentException("改期命令空着");
        }
        String billNo = trim(cmd.getBillNo());
        String itemNo = trim(cmd.getItemNo());
        if (billNo == null || itemNo == null) {
            throw new IllegalArgumentException("改期得点清单号和条目号");
        }
        if (cmd.getNewDueAt() == null) {
            throw new IllegalArgumentException("没写新日子，没法往前挪");
        }
        THeriArriveBill bill = selectByBillNo(billNo);
        if (bill == null) {
            throw new IllegalArgumentException("册上没有这一单: " + billNo);
        }
        synchronized (lockOf(billNo)) {
            THeriArriveRow row = findRow(billNo, itemNo);
            if (row == null) {
                throw new IllegalArgumentException("册上没有条目 " + itemNo + "，不许顺手造一条");
            }
            if (row.getStatus() != null && row.getStatus() != THeriArriveRow.ST_PENDING) {
                // 已经发出去的（及已终态的）留在原处：不追回来重发，也不挪到后头去
                return row;
            }
            if (!cmd.getNewDueAt().before(row.getDueAt())) {
                throw new IllegalArgumentException(
                        "日子只许往前挪：新到口 " + fmt(cmd.getNewDueAt())
                                + " 不比原来 " + fmt(row.getDueAt()) + " 早，不退后也不平移");
            }
            Date revised = cmd.getRevisedAt() == null ? new Date() : cmd.getRevisedAt();
            row.setDueAt(cmd.getNewDueAt());
            // 提前量仍以册上誊死的为准，三处看法不另起
            int lead = bill.getLeadDays() == null ? DEFAULT_LEAD_DAYS : bill.getLeadDays();
            row.setLeadDays(lead);
            row.setRemindAt(minusDays(cmd.getNewDueAt(), lead));
            row.setRevisedAt(revised);
            row.setUpdateBy(operator);
            row.setUpdateTime(new Date());
            this.arrRowMapper.updateById(row);
            writeTrail(billNo, "改期", operator,
                    "条目 " + itemNo + " 还没发出去，到口往前挪到 " + fmt(cmd.getNewDueAt())
                            + "，提醒时刻随之挪到 " + fmt(row.getRemindAt())
                            + "；已发的几条留在原处，不追回、不挪后头");
            return row;
        }
    }

    // ================= 前头一条退了 =================

    @Override
    public THeriArriveRow returnItem(String billNo, String itemNo, String operator) {
        String b = trim(billNo);
        String it = trim(itemNo);
        THeriArriveBill bill = selectByBillNo(b);
        if (bill == null) {
            throw new IllegalArgumentException("册上没有这一单: " + b);
        }
        synchronized (lockOf(b)) {
            THeriArriveRow row = findRow(b, it);
            if (row == null) {
                throw new IllegalArgumentException("册上没有条目 " + it + "，退件无从退起");
            }
            if (row.getStatus() == null || row.getStatus() != THeriArriveRow.ST_SENT) {
                throw new IllegalStateException(
                        "只有真发出去的条能退；条目 " + it + " 现在不是已发，状态=" + row.getStatus());
            }
            row.setStatus(THeriArriveRow.ST_RETURNED);
            row.setFailReason("前头一条退了：记为已退回，后头几条照走不堵");
            row.setUpdateBy(operator);
            row.setUpdateTime(new Date());
            this.arrRowMapper.updateById(row);
            bill.setReturnRows(nz(bill.getReturnRows()) + 1);
            // 退回一条，这单又有要办的条了：头上回到在册
            bill.setStatus(BILL_OPEN);
            bill.setUpdateBy(operator);
            bill.setUpdateTime(new Date());
            this.arrBillMapper.updateById(bill);
            writeTrail(b, "退件", operator,
                    "条目 " + it + " 退回；后头几条照走，下趟发的条点一句“接着一桩退件往下走的”，退件不堵路");
            return row;
        }
    }

    // ================= 抽验：随机抓一条倒着核 =================

    @Override
    public ArriveAuditResult auditOne(String billNo, Date at) {
        ArriveAuditResult a = new ArriveAuditResult();
        a.setBillNo(billNo);
        THeriArriveBill bill = selectByBillNo(billNo);
        if (bill == null) {
            a.setMatch(false);
            a.getProblems().add("册上没有这一单: " + billNo);
            return a;
        }
        Date when = at == null ? new Date() : at;

        // 派发时“该认上”的那条：到点的候选里按 改定晚先、到口近先 排第一
        List<THeriArriveRow> candidates = this.arrRowMapper.selectList(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).le("remind_at", when)
                .in("status", THeriArriveRow.ST_PENDING, THeriArriveRow.ST_SENT,
                        THeriArriveRow.ST_FAILED)
                .eq("del_flag", 0));
        Collections.sort(candidates, new Comparator<THeriArriveRow>() {
            @Override
            public int compare(THeriArriveRow x, THeriArriveRow y) {
                int c = y.getRevisedAt().compareTo(x.getRevisedAt());
                if (c != 0) {
                    return c;
                }
                c = x.getDueAt().compareTo(y.getDueAt());
                if (c != 0) {
                    return c;
                }
                return x.getItemNo().compareTo(y.getItemNo());
            }
        });
        THeriArriveRow expected = candidates.isEmpty() ? null : candidates.get(0);

        // 这趟真发出去的那条：最近发出且当前仍是已发的
        List<THeriArriveRow> sent = this.arrRowMapper.selectList(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("status", THeriArriveRow.ST_SENT)
                .le("send_time", when).eq("del_flag", 0)
                .orderByDesc("send_time").orderByAsc("item_no"));
        THeriArriveRow actual = sent.isEmpty() ? null : sent.get(0);

        if (expected != null) {
            a.setRecognizedItemNo(expected.getItemNo());
        }
        if (actual != null) {
            a.setSentItemNo(actual.getItemNo());
        }

        // 同一天连发两回：逐一点名
        List<THeriArriveRow> sentToday = sentOnDay(billNo, when);
        if (sentToday.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (THeriArriveRow r : sentToday) {
                if (names.length() > 0) {
                    names.append("、");
                }
                names.append(r.getItemNo());
            }
            a.getProblems().add("同一天连发两回，违反“同单同一天只发一条”：" + names);
        }

        if (expected == null && actual == null) {
            // 这趟没认上也没发出：整趟跳空，无对错可核
            a.setMatch(a.getProblems().isEmpty());
            return a;
        }
        if (expected != null && actual == null) {
            // 认上的没真发出去：对不齐就得点名。发不顺那条说清是它自己不顺（有痕），不是没轮到
            if (expected.getStatus() != null && expected.getStatus() == THeriArriveRow.ST_FAILED) {
                a.getProblems().add("认上的 " + expected.getItemNo()
                        + " 自己发不顺、这趟没真发出去（有痕："
                        + expected.getFailReason() + "），跟真发的对不齐，已按条点名");
            } else {
                a.getProblems().add("派发时认上的是 " + expected.getItemNo()
                        + "，这趟却没真发出去，对不上");
            }
        } else if (expected == null) {
            a.getProblems().add("按口径这趟没有该认的条，却真发出了 " + actual.getItemNo()
                    + "，是多发/抢发，对不上");
        } else if (!expected.getItemNo().equals(actual.getItemNo())) {
            a.getProblems().add("认上的那条(" + expected.getItemNo() + ")跟这趟真发出去的那条("
                    + actual.getItemNo() + ")对不齐");
        }

        // 发不顺的条逐一点名：它是自己不顺，不是没轮到；得有痕
        for (THeriArriveRow r : this.arrRowMapper.selectList(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("status", THeriArriveRow.ST_FAILED).eq("del_flag", 0))) {
            if (r.getFailReason() == null || r.getFailReason().trim().isEmpty()) {
                a.getProblems().add("发不顺条目 " + r.getItemNo() + " 没留痕，不许含糊成“没轮到”");
            }
        }

        a.setMatch(a.getProblems().isEmpty()
                && expected != null && actual != null
                && expected.getItemNo().equals(actual.getItemNo()));
        return a;
    }

    private List<THeriArriveRow> sentOnDay(String billNo, Date at) {        Calendar c = Calendar.getInstance();
        c.setTime(at);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date dayStart = c.getTime();
        c.add(Calendar.DAY_OF_MONTH, 1);
        Date dayEnd = c.getTime();
        return this.arrRowMapper.selectList(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("status", THeriArriveRow.ST_SENT)
                .ge("send_time", dayStart).lt("send_time", dayEnd)
                .orderByAsc("item_no"));
    }

    // ================= 抽验：随机抓一条 =================

    @Override
    public ArriveAuditResult auditRandom(Date at) {
        List<THeriArriveBill> all = this.arrBillMapper.selectList(new QueryWrapper<THeriArriveBill>()
                .eq("del_flag", 0).orderByAsc("bill_no"));
        if (all.isEmpty()) {
            return null; // 册上一单都没有：这趟无单可抽，不顶替、不造单
        }
        int idx = randomIndex(all.size());
        THeriArriveBill picked = all.get(idx);
        return auditOne(picked.getBillNo(), at);
    }

    /** 随机源缝：默认真随机，测试可覆写固定抓第几条。 */
    protected int randomIndex(int bound) {
        return new java.util.Random().nextInt(bound);
    }

    // ================= 留痕 / 查询 =================

    @Override
    public List<THeriArriveRow> listRows(String billNo) {
        if (billNo == null) {
            return new ArrayList<THeriArriveRow>();
        }
        return this.arrRowMapper.selectList(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("del_flag", 0)
                .orderByAsc("item_no"));
    }

    private THeriArriveRow findRow(String billNo, String itemNo) {
        return this.arrRowMapper.selectOne(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("item_no", itemNo)
                .eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public List<TSysOperLog> listTrail(String billNo) {
        if (billNo == null) {
            return new ArrayList<TSysOperLog>();
        }
        return this.sysOperLogMapper.selectList(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", trailKey(billNo)).orderByAsc("oper_time"));
    }

    /** 重新算册态：还有待发条就在册，全终态（已发/发不顺/退回）才发齐。 */
    private void refreshBillStatus(THeriArriveBill bill, String operator) {
        Integer pending = this.arrRowMapper.selectCount(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", bill.getBillNo()).eq("status", THeriArriveRow.ST_PENDING));
        bill.setStatus(pending != null && pending > 0 ? BILL_OPEN : BILL_ALL_DONE);
        bill.setUpdateBy(operator);
        bill.setUpdateTime(new Date());
        this.arrBillMapper.updateById(bill);
    }

    /** 留痕缝：单测可覆写捅故障。 */
    protected void writeTrail(String billNo, String action, String operator, String param) {
        TSysOperLog log = new TSysOperLog();
        log.setTitle("到港提醒");
        log.setMethod(action);
        log.setOperName(operator);
        log.setOperUrl(trailKey(billNo));
        log.setOperParam(param);
        log.setOperTime(new Date());
        log.setCreateBy(operator);
        log.setCreateTime(new Date());
        this.sysOperLogMapper.insert(log);
    }

    private void writeRunTrail(String runNo, String action, String operator, String param) {
        TSysOperLog log = new TSysOperLog();
        log.setTitle("到港提醒");
        log.setMethod(action);
        log.setOperName(operator);
        log.setOperUrl("/heri/arrive/run/" + runNo);
        log.setOperParam(param);
        log.setOperTime(new Date());
        log.setCreateBy(operator);
        log.setCreateTime(new Date());
        this.sysOperLogMapper.insert(log);
    }

    // ---------------- 零碎 ----------------

    private ArriveDispatchReceipt receiptOf(THeriArriveRun run, List<ArriveItemResult> batch, int status) {
        ArriveDispatchReceipt rc = new ArriveDispatchReceipt();
        rc.setRunNo(run.getRunNo());
        rc.setScopeBill(run.getScopeBill());
        rc.setStatus(status == THeriArriveRun.ST_DONE
                ? ArriveDispatchReceipt.DONE : ArriveDispatchReceipt.PAGING);
        rc.setCursorBill(status == THeriArriveRun.ST_DONE ? null : run.getCursorBill());
        rc.setResults(batch);
        rc.setSentRows(nz(run.getSentRows()));
        rc.setFailRows(nz(run.getFailRows()));
        rc.setReturnRows(nz(run.getReturnRows()));
        boolean empty = THeriArriveRun.EMPTY_TEXT.equals(run.getResultText());
        rc.setEmpty(empty);
        rc.setResultText(run.getResultText());
        return rc;
    }

    private int count(List<ArriveItemResult> rs, boolean sent, boolean failed) {
        int n = 0;
        for (ArriveItemResult r : rs) {
            if (sent && r.isSent()) {
                n++;
            }
            if (failed && ArriveItemResult.FAILED.equals(r.getOutcome())) {
                n++;
            }
        }
        return n;
    }

    private int countAfterReturn(List<ArriveItemResult> rs) {
        int n = 0;
        for (ArriveItemResult r : rs) {
            if (r.isAfterReturn() && r.isSent()) {
                n++;
            }
        }
        return n;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private Object lockOf(String billNo) {
        Object exist = this.billLocks.get(billNo);
        if (exist != null) {
            return exist;
        }
        Object fresh = new Object();
        Object old = this.billLocks.putIfAbsent(billNo, fresh);
        return old == null ? fresh : old;
    }

    private static Date minusDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DAY_OF_MONTH, -days);
        return c.getTime();
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

    private static String trailKey(String billNo) {
        return "/heri/arrive/" + billNo;
    }
}
