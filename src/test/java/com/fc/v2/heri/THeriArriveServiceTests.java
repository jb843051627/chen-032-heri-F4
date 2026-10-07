package com.fc.v2.heri;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THeriArriveBill;
import com.fc.v2.model.auto.THeriArriveRow;
import com.fc.v2.model.auto.THeriArriveRun;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.ArriveAuditResult;
import com.fc.v2.model.custom.ArriveDispatchCmd;
import com.fc.v2.model.custom.ArriveDispatchReceipt;
import com.fc.v2.model.custom.ArriveItemInput;
import com.fc.v2.model.custom.ArriveItemResult;
import com.fc.v2.model.custom.ArriveRegisterCmd;
import com.fc.v2.model.custom.ArriveRescheduleCmd;
import com.fc.v2.service.impl.THeriArriveServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 到港与滞期提醒排期规矩集成测试（H2 + 真实 mapper）。口径全照值班交接单。
 *
 * @author fuce
 * @date 2026-10-07
 */
class THeriArriveServiceTests extends ArriveITSupport {

    private static final String OP = "调度室乙";
    private static final String RULE = "AR01"; // 提前 3 天

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void init() throws Exception {
        setUp();
    }

    private static Date t(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ArriveItemInput item(String no, String due, String revised) {
        ArriveItemInput in = new ArriveItemInput();
        in.setItemNo(no);
        in.setDueAt(t(due));
        in.setRevisedAt(t(revised));
        in.setNotifyType("短信");
        return in;
    }

    private THeriArriveBill register(String billNo, String ruleCode, ArriveItemInput... items) {
        ArriveRegisterCmd cmd = new ArriveRegisterCmd();
        cmd.setBillNo(billNo);
        cmd.setShipName("远春河/2610E");
        cmd.setRuleCode(ruleCode);
        List<ArriveItemInput> list = new ArrayList<ArriveItemInput>();
        for (ArriveItemInput in : items) {
            list.add(in);
        }
        cmd.setItems(list);
        return arriveService.register(cmd, OP);
    }

    private ArriveDispatchReceipt dispatchOne(String billNo, String at) {
        ArriveDispatchCmd c = new ArriveDispatchCmd();
        c.setBillNo(billNo);
        c.setAt(t(at));
        return arriveService.dispatch(c, OP);
    }

    private ArriveDispatchCmd batchCmd(String runNo, String at, Integer pageSize, Long timeoutMs) {
        ArriveDispatchCmd c = new ArriveDispatchCmd();
        c.setRunNo(runNo);
        c.setAt(t(at));
        c.setPageSize(pageSize);
        c.setTimeoutMs(timeoutMs);
        return c;
    }

    private THeriArriveRow row(String billNo, String itemNo) {
        return rowMapper.selectOne(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", billNo).eq("item_no", itemNo).last("limit 1"));
    }

    private List<TSysOperLog> trail(String billNo) {
        return arriveService.listTrail(billNo);
    }

    private boolean trailHas(String billNo, String action) {
        for (TSysOperLog l : trail(billNo)) {
            if (action.equals(l.getMethod())) {
                return true;
            }
        }
        return false;
    }

    /** 造一个真发会对指定条目抛错的 service（通道抖了一下），其余走正常；共用同一批 mapper。 */
    private THeriArriveServiceImpl brokenDeliver(final Set<String> failItems) {
        THeriArriveServiceImpl s = new THeriArriveServiceImpl() {
            @Override
            protected void deliver(THeriArriveRow r) {
                if (failItems.contains(r.getItemNo())) {
                    throw new RuntimeException("通道抖了一下");
                }
            }
        };
        ReflectionTestUtils.setField(s, "arrRuleMapper", ruleMapper);
        ReflectionTestUtils.setField(s, "arrBillMapper", billMapper);
        ReflectionTestUtils.setField(s, "arrRowMapper", rowMapper);
        ReflectionTestUtils.setField(s, "arrRunMapper", runMapper);
        ReflectionTestUtils.setField(s, "sysOperLogMapper", operLogMapper);
        return s;
    }

    // ---------------- 提前量从哪儿来：港里判据说了算 ----------------

    @Test
    @DisplayName("提前量照判据：判据写 3 天，提醒时刻=到口往前挪 3 天")
    void leadDays_fromRule() {
        register("B01", RULE, item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00"));
        THeriArriveRow r = row("B01", "I1");
        assertEquals(Integer.valueOf(3), r.getLeadDays());
        assertEquals(t("2026-10-07 09:00:00"), r.getRemindAt());
        assertEquals(Integer.valueOf(3), arriveService.selectByBillNo("B01").getLeadDays(),
                "提前量誊死在单册头上");
    }

    @Test
    @DisplayName("那格空着/负数/说不上来的大数：都按一日，不是 0、也不是一年半载")
    void leadDays_blankNegHuge_defaultOne() {
        register("B10", "AR99", item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00")); // 空
        register("B11", "AR77", item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00")); // 负
        register("B12", "AR88", item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00")); // 大数
        for (String b : new String[]{"B10", "B11", "B12"}) {
            THeriArriveRow r = row(b, "I1");
            assertEquals(Integer.valueOf(1), r.getLeadDays(), b + " 应按一日");
            assertEquals(t("2026-10-09 09:00:00"), r.getRemindAt(), b + " 提前一天");
        }
    }

    @Test
    @DisplayName("判据撤下/过期/没写：不注册，系统绝不自己挑个提前量")
    void ruleMissing_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> register("B20", "AR09", item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00")));
        assertThrows(IllegalArgumentException.class,
                () -> register("B21", "AR08", item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00")));
        assertThrows(IllegalArgumentException.class,
                () -> register("B22", null, item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00")));
        assertNull(arriveService.selectByBillNo("B20"));
        assertNull(arriveService.selectByBillNo("B21"));
        assertNull(arriveService.selectByBillNo("B22"));
    }

    // ---------------- 同单多条，先跑哪条 ----------------

    @Test
    @DisplayName("认条次序：先改定得晚的（到口更晚也认它），认死那一条其余不吭声")
    void order_latestRevisedFirst() {
        register("B30", RULE,
                item("EARLY", "2026-10-09 09:00:00", "2026-10-01 08:00:00"),
                item("LATE", "2026-10-11 09:00:00", "2026-10-02 09:00:00"));
        ArriveDispatchReceipt rc = dispatchOne("B30", "2026-10-08 10:00:00");
        assertEquals(1, rc.getSentRows());
        assertEquals("LATE", rc.getResults().get(0).getItemNo());
        assertEquals(THeriArriveRow.ST_SENT, row("B30", "LATE").getStatus().intValue());
        assertEquals(THeriArriveRow.ST_PENDING, row("B30", "EARLY").getStatus().intValue(),
                "其余不吭声，仍待发");
    }

    @Test
    @DisplayName("改定一样晚：看到口更近的（到口早的先）")
    void order_tieRevised_closerDueFirst() {
        register("B31", RULE,
                item("NEAR", "2026-10-09 09:00:00", "2026-10-02 09:00:00"),
                item("FAR", "2026-10-11 09:00:00", "2026-10-02 09:00:00"));
        ArriveDispatchReceipt rc = dispatchOne("B31", "2026-10-08 10:00:00");
        assertEquals("NEAR", rc.getResults().get(0).getItemNo());
    }

    @Test
    @DisplayName("同一天不许连发两回：当天剩下那条按住；第二天才发")
    void noTwoSendsSameDay_nextDayOk() {
        register("B32", RULE,
                item("EARLY", "2026-10-09 09:00:00", "2026-10-01 08:00:00"),
                item("LATE", "2026-10-11 09:00:00", "2026-10-02 09:00:00"));
        dispatchOne("B32", "2026-10-08 10:00:00"); // 发 LATE
        ArriveDispatchReceipt again = dispatchOne("B32", "2026-10-08 18:00:00"); // 同天
        assertEquals(0, again.getSentRows(), "同一天不连发第二回");
        assertEquals(THeriArriveRow.ST_PENDING, row("B32", "EARLY").getStatus());

        ArriveDispatchReceipt nextDay = dispatchOne("B32", "2026-10-09 08:00:00");
        assertEquals(1, nextDay.getSentRows(), "第二天可以发下一条");
        assertEquals("EARLY", nextDay.getResults().get(0).getItemNo());
    }

    // ---------------- 日子要往前挪：只动还没发出去的 ----------------

    @Test
    @DisplayName("改期只动没发的：到口改早、提醒时刻随之挪；已发的留在原处不追不挪")
    void reschedule_onlyPending() {
        register("B40", RULE,
                item("P", "2026-10-11 09:00:00", "2026-10-01 08:00:00"),
                item("S", "2026-10-09 09:00:00", "2026-10-02 09:00:00"));
        dispatchOne("B40", "2026-10-08 10:00:00"); // 发 S（改定晚）
        Date sentDue = row("B40", "S").getDueAt();
        Date sentRemind = row("B40", "S").getRemindAt();

        // 待发的 P 往前挪
        ArriveRescheduleCmd cmd = new ArriveRescheduleCmd();
        cmd.setBillNo("B40");
        cmd.setItemNo("P");
        cmd.setNewDueAt(t("2026-10-10 09:00:00"));
        cmd.setRevisedAt(t("2026-10-03 10:00:00"));
        THeriArriveRow moved = arriveService.reschedule(cmd, OP);
        assertEquals(t("2026-10-10 09:00:00"), moved.getDueAt());
        assertEquals(t("2026-10-07 09:00:00"), moved.getRemindAt(), "仍按册上誊死的提前 3 天重算");
        assertEquals(Integer.valueOf(3), moved.getLeadDays(), "提前量以册上为准，不另起");

        // 已发的 S 想挪：原样留在原处
        ArriveRescheduleCmd again = new ArriveRescheduleCmd();
        again.setBillNo("B40");
        again.setItemNo("S");
        again.setNewDueAt(t("2026-10-01 09:00:00"));
        THeriArriveRow untouched = arriveService.reschedule(again, OP);
        assertEquals(sentDue, untouched.getDueAt());
        assertEquals(sentRemind, untouched.getRemindAt());
        assertEquals(THeriArriveRow.ST_SENT, untouched.getStatus().intValue());
        assertTrue(trailHas("B40", "改期"));
    }

    @Test
    @DisplayName("往后挪/平移不许；册上没那条不许顺手造一条")
    void reschedule_notEarlierOrMissing_rejected() {
        register("B41", RULE, item("P", "2026-10-11 09:00:00", "2026-10-01 08:00:00"));
        ArriveRescheduleCmd same = new ArriveRescheduleCmd();
        same.setBillNo("B41");
        same.setItemNo("P");
        same.setNewDueAt(t("2026-10-11 09:00:00"));
        assertThrows(IllegalArgumentException.class, () -> arriveService.reschedule(same, OP));

        ArriveRescheduleCmd later = new ArriveRescheduleCmd();
        later.setBillNo("B41");
        later.setItemNo("P");
        later.setNewDueAt(t("2026-10-20 09:00:00"));
        assertThrows(IllegalArgumentException.class, () -> arriveService.reschedule(later, OP));

        ArriveRescheduleCmd ghost = new ArriveRescheduleCmd();
        ghost.setBillNo("B41");
        ghost.setItemNo("GHOST");
        ghost.setNewDueAt(t("2026-10-01 09:00:00"));
        assertThrows(IllegalArgumentException.class, () -> arriveService.reschedule(ghost, OP));
    }

    // ---------------- 中间一条发失败：跳过，不堵后头 ----------------

    @Test
    @DisplayName("发不顺那条留痕(自己不顺不是没轮到)，跳过不堵：下一趟把后头的发出去")
    void fail_skipsAndContinues() {
        register("B50", RULE,
                item("F1", "2026-10-11 09:00:00", "2026-10-02 09:00:00"), // 改定晚，先认它
                item("G1", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        THeriArriveServiceImpl broken = brokenDeliver(setOf("F1"));

        ArriveDispatchCmd c1 = new ArriveDispatchCmd();
        c1.setBillNo("B50");
        c1.setAt(t("2026-10-08 10:00:00"));
        ArriveDispatchReceipt r1 = broken.dispatch(c1, OP);
        assertEquals(0, r1.getSentRows());
        assertEquals(1, r1.getFailRows());
        assertEquals(ArriveItemResult.FAILED, r1.getResults().get(0).getOutcome());
        THeriArriveRow f1 = row("B50", "F1");
        assertEquals(THeriArriveRow.ST_FAILED, f1.getStatus().intValue());
        assertNotNull(f1.getFailReason());
        assertTrue(f1.getFailReason().contains("发不顺"));
        assertTrue(trailHas("B50", "发不顺"), "跳过那条留一笔痕");

        // 下一趟（第二天，避开同日不连发）：后头的 G1 照发，F1 没把路堵死
        ArriveDispatchCmd c2 = new ArriveDispatchCmd();
        c2.setBillNo("B50");
        c2.setAt(t("2026-10-09 10:00:00"));
        ArriveDispatchReceipt r2 = broken.dispatch(c2, OP);
        assertEquals(1, r2.getSentRows());
        assertEquals("G1", r2.getResults().get(0).getItemNo());
        assertEquals(THeriArriveRow.ST_SENT, row("B50", "G1").getStatus().intValue());
        assertEquals(Integer.valueOf(1), arriveService.selectByBillNo("B50").getFailRows());
    }

    // ---------------- 前头一条退了：后头照走，点一句退件后续 ----------------

    @Test
    @DisplayName("前一条退了：后头那条照走，带退件后续标记，留痕点一句")
    void return_thenLaterProceeds() {
        register("B60", RULE,
                item("X", "2026-10-09 09:00:00", "2026-10-02 09:00:00"),
                item("Y", "2026-10-11 09:00:00", "2026-10-01 08:00:00"));
        dispatchOne("B60", "2026-10-08 10:00:00"); // 发 X
        assertEquals(THeriArriveRow.ST_SENT, row("B60", "X").getStatus().intValue());

        THeriArriveRow ret = arriveService.returnItem("B60", "X", OP);
        assertEquals(THeriArriveRow.ST_RETURNED, ret.getStatus().intValue());
        assertTrue(trailHas("B60", "退件"));
        assertEquals(Integer.valueOf(1), arriveService.selectByBillNo("B60").getReturnRows());

        // 退件不堵路：Y 这趟照走，且点一句是接着一桩退件往下走的
        ArriveDispatchReceipt rc = dispatchOne("B60", "2026-10-08 12:00:00");
        assertEquals(1, rc.getSentRows());
        ArriveItemResult rr = rc.getResults().get(0);
        assertEquals("Y", rr.getItemNo());
        assertTrue(rr.isAfterReturn());
        assertEquals(Integer.valueOf(1), row("B60", "Y").getAfterReturn());
        boolean noted = false;
        for (TSysOperLog l : trail("B60")) {
            if ("发出".equals(l.getMethod()) && l.getOperParam() != null
                    && l.getOperParam().contains("接着一桩退件")) {
                noted = true;
            }
        }
        assertTrue(noted, "留痕点一句退件后续");
    }

    @Test
    @DisplayName("没真发出去的条不能退")
    void return_onlySent() {
        register("B61", RULE, item("P", "2026-10-11 09:00:00", "2026-10-01 08:00:00"));
        assertThrows(IllegalStateException.class, () -> arriveService.returnItem("B61", "P", OP));
    }

    // ---------------- 册上压根没有对应条目：整趟跳空写“无” ----------------

    @Test
    @DisplayName("册上空空的跑一趟：整趟跳空写一个字“无”，不顶替、不造条")
    void dispatch_emptyWritesWu() {
        ArriveDispatchReceipt rc = arriveService.dispatch(batchCmd(null, "2026-10-08 10:00:00", null, null), OP);
        assertTrue(rc.isEmpty());
        assertEquals("无", rc.getResultText());
        assertEquals(0, rc.getSentRows());
        THeriArriveRun run = runMapper.selectOne(new QueryWrapper<THeriArriveRun>()
                .eq("run_no", rc.getRunNo()));
        assertEquals("无", run.getResultText());
        // 留痕里也只有“跳空/无”，没顺手造单造条
        assertEquals(0, billMapper.selectCount(new QueryWrapper<THeriArriveBill>()));
    }

    @Test
    @DisplayName("点一个册上没有的单：整趟跳空写“无”，不许拿别的单顶替")
    void dispatch_missingBill_wu() {
        register("B70", RULE, item("I1", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        ArriveDispatchCmd c = new ArriveDispatchCmd();
        c.setBillNo("B_GHOST");
        c.setAt(t("2026-10-08 10:00:00"));
        ArriveDispatchReceipt rc = arriveService.dispatch(c, OP);
        assertTrue(rc.isEmpty());
        assertEquals("无", rc.getResultText());
        // B70 没被顺手拉来顶替
        assertEquals(THeriArriveRow.ST_PENDING, row("B70", "I1").getStatus().intValue());
    }

    // ---------------- 按批分页拉，超时停半、续时接着拉 ----------------

    @Test
    @DisplayName("按批分页：超时停在半路留断点，同趟号续拉接着走，已发的不重发")
    void paging_timeoutThenResume() {
        for (int i = 1; i <= 5; i++) {
            String b = String.format("B%02d", i);
            register(b, RULE, item("I1", "2026-10-11 09:00:00", "2026-10-01 08:00:00"));
        }
        // 每页 2 单、时限 0：拉满整页即判超时停半
        ArriveDispatchReceipt p1 = arriveService.dispatch(
                batchCmd(null, "2026-10-08 10:00:00", 2, 0L), OP);
        assertEquals(ArriveDispatchReceipt.PAGING, p1.getStatus());
        assertEquals(2, p1.getSentRows());
        assertEquals("B02", p1.getCursorBill(), "停在第 2 单之后");
        String runNo = p1.getRunNo();

        ArriveDispatchReceipt p2 = arriveService.dispatch(
                batchCmd(runNo, "2026-10-08 10:00:00", 2, 0L), OP);
        assertEquals(ArriveDispatchReceipt.PAGING, p2.getStatus());
        assertEquals(4, p2.getSentRows(), "累计 4 单");
        assertEquals("B04", p2.getCursorBill());

        ArriveDispatchReceipt p3 = arriveService.dispatch(
                batchCmd(runNo, "2026-10-08 10:00:00", 2, 0L), OP);
        assertEquals(ArriveDispatchReceipt.DONE, p3.getStatus(), "末页拉完走完");
        assertEquals(5, p3.getSentRows(), "累计 5 单，一条不重");

        // 每单真就发了一条，没重发
        for (int i = 1; i <= 5; i++) {
            String b = String.format("B%02d", i);
            assertEquals(THeriArriveRow.ST_SENT, row(b, "I1").getStatus().intValue(), b);
        }
        THeriArriveRun run = runMapper.selectOne(new QueryWrapper<THeriArriveRun>().eq("run_no", runNo));
        assertEquals(THeriArriveRun.ST_DONE, run.getStatus().intValue());
        assertNotNull(run.getNote());
        assertTrue(run.getNote().contains("超时"), "停半时留过一笔：" + run.getNote());
    }

    @Test
    @DisplayName("走完的趟再叫一遍：照面，不重发")
    void dispatch_finishedRun_faceOnly() {
        register("B80", RULE, item("I1", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        ArriveDispatchReceipt first = arriveService.dispatch(
                batchCmd(null, "2026-10-08 10:00:00", null, null), OP);
        ArriveDispatchReceipt again = arriveService.dispatch(
                batchCmd(first.getRunNo(), "2026-10-08 11:00:00", null, null), OP);
        assertEquals(1, first.getSentRows());
        assertEquals(0, again.getResults().size(), "走完的趟照面，不重发");
        assertEquals(1, rowMapper.selectCount(new QueryWrapper<THeriArriveRow>()
                .eq("bill_no", "B80").eq("status", THeriArriveRow.ST_SENT)));
    }

    // ---------------- 三处看法以册上为准；老提醒一条不减 ----------------

    @Test
    @DisplayName("三处看法以册上为准：注册后改判据，老册提前量/提醒时刻不追、不改")
    void ruleChange_doesNotTouchOld() {
        register("B90", RULE, item("I1", "2026-10-10 09:00:00", "2026-10-01 08:00:00"));
        com.fc.v2.model.auto.THeriArriveRule rule = ruleMapper.selectOne(
                new QueryWrapper<com.fc.v2.model.auto.THeriArriveRule>().eq("rule_code", RULE));
        rule.setLeadDays(9); // 后来港里改成提前 9 天
        ruleMapper.updateById(rule);

        THeriArriveBill bill = arriveService.selectByBillNo("B90");
        assertEquals(Integer.valueOf(3), bill.getLeadDays(), "老册头上誊死的 3 天不改");
        THeriArriveRow r = row("B90", "I1");
        assertEquals(Integer.valueOf(3), r.getLeadDays());
        assertEquals(t("2026-10-07 09:00:00"), r.getRemindAt(), "老提醒时刻留在原处");
    }

    @Test
    @DisplayName("同号再注只照面：老条一条不减、不重排，新带的条不落册")
    void register_duplicate_faceOnly() {
        register("B91", RULE, item("OLD", "2026-10-10 09:00:00", "2026-10-01 08:00:00"));
        THeriArriveBill again = register("B91", RULE,
                item("NEW", "2026-10-20 09:00:00", "2026-10-05 08:00:00"));
        assertEquals(Integer.valueOf(1), again.getTotalRows(), "老条一条不减，新条不就地接进来");
        assertEquals(1, arriveService.listRows("B91").size());
        assertNull(row("B91", "NEW"));
    }

    // ---------------- 抽验：随机抓一条倒着核 ----------------

    @Test
    @DisplayName("倒核对得上：认上的就是真发的，无话说")
    void audit_match() {
        register("BA0", RULE,
                item("EARLY", "2026-10-09 09:00:00", "2026-10-01 08:00:00"),
                item("WIN", "2026-10-11 09:00:00", "2026-10-02 09:00:00"));
        dispatchOne("BA0", "2026-10-08 10:00:00");
        ArriveAuditResult a = arriveService.auditOne("BA0", t("2026-10-08 10:00:00"));
        assertTrue(a.isMatch());
        assertEquals("WIN", a.getRecognizedItemNo());
        assertEquals("WIN", a.getSentItemNo());
        assertTrue(a.getProblems().isEmpty());
    }

    @Test
    @DisplayName("倒核对不齐：认上的没发、发了的不是认上的，两条按条点名")
    void audit_mismatch_namesRows() {
        register("BA1", RULE,
                item("WIN", "2026-10-11 09:00:00", "2026-10-02 09:00:00"),
                item("LOSE", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        // 模拟账被弄花：该发的 WIN 还待发，不该先发的 LOSE 却落了已发
        THeriArriveRow win = row("BA1", "WIN");
        win.setStatus(THeriArriveRow.ST_PENDING);
        rowMapper.updateById(win);
        THeriArriveRow lose = row("BA1", "LOSE");
        lose.setStatus(THeriArriveRow.ST_SENT);
        lose.setSendTime(t("2026-10-08 10:00:00"));
        rowMapper.updateById(lose);

        ArriveAuditResult a = arriveService.auditOne("BA1", t("2026-10-08 11:00:00"));
        assertFalse(a.isMatch());
        String joined = join(a.getProblems());
        assertTrue(joined.contains("WIN"), "点名认上没发的 WIN：" + joined);
        assertTrue(joined.contains("LOSE"), "点名叫错发的 LOSE：" + joined);
    }

    @Test
    @DisplayName("倒核：同一天连发两回，逐条点名不许含糊")
    void audit_doubleSendSameDay_named() {
        register("BA2", RULE,
                item("A", "2026-10-11 09:00:00", "2026-10-02 09:00:00"),
                item("B", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        for (String no : new String[]{"A", "B"}) {
            THeriArriveRow r = row("BA2", no);
            r.setStatus(THeriArriveRow.ST_SENT);
            r.setSendTime(t("2026-10-08 10:00:00"));
            rowMapper.updateById(r);
        }
        ArriveAuditResult a = arriveService.auditOne("BA2", t("2026-10-08 11:00:00"));
        assertFalse(a.isMatch());
        String joined = join(a.getProblems());
        assertTrue(joined.contains("A") && joined.contains("B"), "两条都点名：" + joined);
        assertTrue(joined.contains("同一天连发两回"));
    }

    @Test
    @DisplayName("抽验随机抓一条：随机源固定时抓到指定那单倒核")
    void auditRandom_picksOne() {
        register("BA3", RULE, item("I1", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        register("BA4", RULE, item("I1", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        THeriArriveServiceImpl fixed = new THeriArriveServiceImpl() {
            @Override
            protected int randomIndex(int bound) {
                return 1; // 固定抓排序后第 2 单（BA4）
            }
        };
        ReflectionTestUtils.setField(fixed, "arrRuleMapper", ruleMapper);
        ReflectionTestUtils.setField(fixed, "arrBillMapper", billMapper);
        ReflectionTestUtils.setField(fixed, "arrRowMapper", rowMapper);
        ReflectionTestUtils.setField(fixed, "arrRunMapper", runMapper);
        ReflectionTestUtils.setField(fixed, "sysOperLogMapper", operLogMapper);

        // 都还没发，auditOne 对“没认上也没发”之外的情形：给它们先发一单
        dispatchOne("BA4", "2026-10-08 10:00:00");
        ArriveAuditResult a = fixed.auditRandom(t("2026-10-08 11:00:00"));
        assertNotNull(a);
        assertEquals("BA4", a.getBillNo());
        assertTrue(a.isMatch());
    }

    // ---------------- 留痕：谁、什么时候、走过哪几段 ----------------

    @Test
    @DisplayName("倒查：注册/认发/退件各段留痕，按时刻升序、记得是谁")
    void trail_recordsStages() {
        register("BB0", RULE, item("X", "2026-10-09 09:00:00", "2026-10-01 08:00:00"));
        dispatchOne("BB0", "2026-10-08 10:00:00");
        arriveService.returnItem("BB0", "X", OP);

        List<TSysOperLog> logs = trail("BB0");
        Set<String> actions = new HashSet<String>();
        Date prev = null;
        for (TSysOperLog l : logs) {
            actions.add(l.getMethod());
            assertEquals(OP, l.getOperName());
            assertNotNull(l.getOperTime());
            if (prev != null) {
                assertFalse(l.getOperTime().before(prev), "留痕按时刻升序");
            }
            prev = l.getOperTime();
        }
        assertTrue(actions.contains("入册"));
        assertTrue(actions.contains("发出"));
        assertTrue(actions.contains("退件"));
    }

    private static Set<String> setOf(String... xs) {
        Set<String> s = new HashSet<String>();
        for (String x : xs) {
            s.add(x);
        }
        return s;
    }

    private static String join(List<String> xs) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) {
            sb.append(x).append('；');
        }
        return sb.toString();
    }
}
