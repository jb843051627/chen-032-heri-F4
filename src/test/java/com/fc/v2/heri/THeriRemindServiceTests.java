package com.fc.v2.heri;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THeriRemindDispatch;
import com.fc.v2.model.auto.THeriRemindRow;
import com.fc.v2.model.auto.THeriRemindRun;
import com.fc.v2.model.auto.TSysOperLog;
import com.fc.v2.model.custom.RemindAuditResult;
import com.fc.v2.model.custom.RemindRowCmd;
import com.fc.v2.model.custom.RemindRunReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 到港与滞期提醒排期规矩集成测试（H2 + 真实 mapper）。
 * 蓝本就是值班交接单：提前量判据说了算；先改定得晚、同晚看到口近；
 * 只动没发出去的；发不顺跳过接着走；退件不堵路；册上没有整趟跳空写“无”；
 * 分页拉半道超时停下续拉；抽验随机抓一条倒着核。
 *
 * @author fuce
 * @date 2026-10-07
 */
class THeriRemindServiceTests extends RemindITSupport {

    private static final String OP = "调度室乙";
    private static final String RULE = "RR01";

    @BeforeEach
    void init() throws Exception {
        setUp();
    }

    private static Date t(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    private RemindRowCmd cmd(String bill, String due, String leadRaw, String ruleCode,
                               String revisedAt) throws Exception {
        RemindRowCmd c = new RemindRowCmd();
        c.setBillNo(bill);
        c.setShipName("远春河/2610E");
        c.setDueAt(t(due));
        c.setLeadDaysRaw(leadRaw);
        c.setRuleCode(ruleCode);
        if (revisedAt != null) {
            c.setRevisedAt(t(revisedAt));
        }
        return c;
    }

    private THeriRemindRow reg(String bill, String due, String leadRaw, String ruleCode,
                                 String revisedAt) throws Exception {
        return remindService.register(cmd(bill, due, leadRaw, ruleCode, revisedAt), OP);
    }

    private RemindRunReceipt runAt(String at, String... bills) throws Exception {
        return remindService.runOnce(Arrays.asList(bills), t(at), null, OP);
    }

    private THeriRemindRow row(String bill, int rowNo) {
        return remindService.listRows(bill).stream()
                .filter(r -> r.getRowNo() == rowNo).findFirst().orElse(null);
    }

    private List<THeriRemindDispatch> dispatchesOf(String runNo) {
        return remindService.listDispatches(runNo);
    }

    private int dispatchCount(String bill, int rowNo, int status) {
        return dispatchMapper.selectCount(new QueryWrapper<THeriRemindDispatch>()
                .eq("bill_no", bill).eq("row_no", rowNo).eq("status", status));
    }

    // ---------------- 提前量从哪儿来：港里判据说了算 ----------------

    @Test
    @DisplayName("提前量：判据说几天就几天，空着按一日")
    void leadDays_ruleSaysOrBlankOne() throws Exception {
        assertEquals(Integer.valueOf(3), reg("B1", "2026-10-20 09:00:00", null, RULE, null).getLeadDays());
        rule(2L, "RR10", "空格判据", null, 0);
        assertEquals(Integer.valueOf(1), reg("B2", "2026-10-20 09:00:00", null, "RR10", null).getLeadDays());
        // 没指判据：格上写几天就几天，空着按一日
        assertEquals(Integer.valueOf(5), reg("B3", "2026-10-20 09:00:00", "5", null, null).getLeadDays());
        assertEquals(Integer.valueOf(1), reg("B4", "2026-10-20 09:00:00", "  ", null, null).getLeadDays());
    }

    @Test
    @DisplayName("提前量：负数、说不上来的大数、非数，都按空着办——不是零，也不是一年半载")
    void leadDays_garbageTreatedAsBlank() throws Exception {
        // 交单格：负数/非数/超过一年的大数，按空着(一日)
        assertEquals(Integer.valueOf(1), reg("N1", "2026-10-20 09:00:00", "-2", null, null).getLeadDays());
        assertEquals(Integer.valueOf(1), reg("N2", "2026-10-20 09:00:00", "abc", null, null).getLeadDays());
        assertEquals(Integer.valueOf(1), reg("N3", "2026-10-20 09:00:00", "99999", null, null).getLeadDays());
        assertEquals(Integer.valueOf(1), reg("N4", "2026-10-20 09:00:00", "366", null, null).getLeadDays());
        // 0 和 365 都在一年口径内，判据说了算
        assertEquals(Integer.valueOf(0), reg("N5", "2026-10-20 09:00:00", "0", null, null).getLeadDays());
        assertEquals(Integer.valueOf(365), reg("N6", "2026-10-20 09:00:00", "365", null, null).getLeadDays());

        // 判据那格写负数/写大数：判据自己说不上来，也按空着办，不是零也不是一年半载
        rule(11L, "RR11", "负数判据", -5, 0);
        rule(12L, "RR12", "大数判据", 99999, 0);
        assertEquals(Integer.valueOf(1), reg("N7", "2026-10-20 09:00:00", "7", "RR11", null).getLeadDays());
        assertEquals(Integer.valueOf(1), reg("N8", "2026-10-20 09:00:00", "7", "RR12", null).getLeadDays());

        // 判据撤了/册上没有：照空着办；交单格自己写的数也顶替不上判据
        rule(13L, "RR13", "撤下判据", 3, 1);
        assertEquals(Integer.valueOf(1), reg("N9", "2026-10-20 09:00:00", "7", "RR13", null).getLeadDays());
        assertEquals(Integer.valueOf(1), reg("N10", "2026-10-20 09:00:00", "7", "RR404", null).getLeadDays());
    }

    // ---------------- 同单多条提醒，先跑哪条 ----------------

    @Test
    @DisplayName("同单三条：先改定得晚的；一样晚看到口更近的；定死一条其余不吭声")
    void sameBill_ordering_latestRevisedThenNearestDue() throws Exception {
        // 行1：改定 10-01；行2/行3：改定 10-05 打平，看到口——行3(10-18)比行2(10-20)更近
        reg("A", "2026-10-20 09:00:00", "3", RULE, "2026-10-01 09:00:00");
        reg("A", "2026-10-20 09:00:00", "3", RULE, "2026-10-05 09:00:00");
        reg("A", "2026-10-18 09:00:00", "3", RULE, "2026-10-05 09:00:00");

        // 10-15：三条的提醒时刻分别是 10-17 / 10-17 / 10-15，只有行3到口
        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "A");
        assertEquals(1, rc.sentCount());
        assertEquals("A", rc.getOutcomes().get(0).getBillNo());
        assertEquals(Integer.valueOf(3), rc.getOutcomes().get(0).getRowNo());
        assertEquals(1, dispatchCount("A", 3, THeriRemindDispatch.ST_SENT));
        assertEquals(0, dispatchCount("A", 1, THeriRemindDispatch.ST_SENT));
        assertEquals(0, dispatchCount("A", 2, THeriRemindDispatch.ST_SENT));
        assertEquals(1, dispatchesOf(rc.getRunNo()).size(), "只真发一条，其余不吭声");
        assertEquals(Integer.valueOf(1), row("A", 3).getSentFlag());
        assertEquals(Integer.valueOf(0), row("A", 1).getSentFlag());
    }

    @Test
    @DisplayName("次序再平：看册上行次，行1先跑；册上行次册上排 1..N")
    void sameBill_tieBrokenByBookRowNo() throws Exception {
        THeriRemindRow r1 = reg("A", "2026-10-18 09:00:00", "3", RULE, "2026-10-05 09:00:00");
        THeriRemindRow r2 = reg("A", "2026-10-18 09:00:00", "3", RULE, "2026-10-05 09:00:00");
        assertEquals(Integer.valueOf(1), r1.getRowNo());
        assertEquals(Integer.valueOf(2), r2.getRowNo());

        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "A");
        assertEquals(1, rc.sentCount());
        assertEquals(Integer.valueOf(1), rc.getOutcomes().get(0).getRowNo());
    }

    @Test
    @DisplayName("同一天不许连发两回：发过的那条当天再跑只照面，不发第二回；发不顺的不算发出去")
    void sameDay_noSecondSend() throws Exception {
        reg("A", "2026-10-18 09:00:00", "3", RULE, null);
        RemindRunReceipt first = runAt("2026-10-15 09:00:00", "A");
        assertEquals(1, first.sentCount());

        RemindRunReceipt second = runAt("2026-10-15 20:00:00", "A");
        assertEquals(0, second.sentCount());
        assertEquals(0, second.alreadyTodayCount(),
                "已发出去的是终态、不再进待提醒候选，压根不重发（不是轮到了再拦）");
        assertEquals(1, dispatchCount("A", 1, THeriRemindDispatch.ST_SENT), "真发流水只有一条");
        assertEquals(THeriRemindRow.ST_REMINDED, row("A", 1).getStatus(), "已提醒终态");

        // 发失败的那回不算发出去：当天修好后再跑，还能发
        remindService.fail("B", 1);
        reg("B", "2026-10-18 09:00:00", "3", RULE, null);
        RemindRunReceipt bad = runAt("2026-10-15 09:00:00", "B");
        assertEquals(0, bad.sentCount());
        assertEquals(1, bad.failedCount());
        // 发送端修好，当天再跑
        remindService.fixed("B", 1);
        RemindRunReceipt retry = runAt("2026-10-15 21:00:00", "B");
        assertEquals(1, retry.sentCount(), "发不顺没发出去，不算连发，重试照发");
        assertEquals(1, dispatchCount("B", 1, THeriRemindDispatch.ST_SENT));
    }

    @Test
    @DisplayName("只点这一天到口的：按天认，时刻不卡；没到提醒日的不吭声")
    void dueByDay_notByMinute() throws Exception {
        reg("A", "2026-10-18 09:00:00", "3", RULE, null); // 提醒时刻 10-15 09:00
        // 当天深夜也算这一天到口
        assertEquals(1, runAt("2026-10-15 23:00:00", "A").sentCount());
        // 前一天：还没到，一条不发，趟照跑（不是跳空）
        reg("C", "2026-10-18 09:00:00", "3", RULE, null);
        RemindRunReceipt early2 = runAt("2026-10-14 09:00:00", "C");
        assertEquals(0, early2.sentCount());
        assertFalse(early2.isEmpty(), "册上有条、只是没到日，不叫跳空");
        assertEquals(THeriRemindRun.ST_DONE, early2.getRunStatus());
    }

    // ---------------- 日子要往前挪：只动还没发出去的 ----------------

    @Test
    @DisplayName("日子前挪：没发的跟着挪；已发的留在原处，不追回重发、不挪到后头")
    void moveEarlier_onlyUnsented() throws Exception {
        THeriRemindRow r = reg("A", "2026-10-20 09:00:00", "3", RULE, null);
        // 没发：往前挪，改定时刻跟着这趟走
        THeriRemindRow moved = remindService.moveEarlier("A", 1, t("2026-10-10 09:00:00"), OP);
        assertEquals(t("2026-10-10 09:00:00"), moved.getDueAt());
        assertNotNull(moved.getRevisedAt());
        // 挪到 10-07 到口（10-10 提前 3 天）
        assertEquals(1, runAt("2026-10-07 09:00:00", "A").sentCount());

        // 已发：再挪顶回，日子留在原处
        assertThrows(IllegalStateException.class,
                () -> remindService.moveEarlier("A", 1, t("2026-10-09 09:00:00"), OP));
        assertEquals(t("2026-10-10 09:00:00"), row("A", 1).getDueAt());
        // 往后挪也顶回：日子只能往前挪
        THeriRemindRow r2 = reg("B", "2026-10-20 09:00:00", "3", RULE, null);
        assertThrows(IllegalArgumentException.class,
                () -> remindService.moveEarlier("B", 1, t("2026-10-21 09:00:00"), OP));
        assertEquals(t("2026-10-20 09:00:00"), row("B", 1).getDueAt());
    }

    @Test
    @DisplayName("同一天不许连发两回：条还挂待提醒、真发流水已在（落账不一致），也不重发，按没轮到点出")
    void sentTodayAlready_guardByDispatchLedger() throws Exception {
        THeriRemindRow r = reg("DUP", "2026-10-18 09:00:00", "3", RULE, null);
        // 造出不一致：真发流水已在（同一天），条目却还挂着待提醒
        THeriRemindDispatch d = new THeriRemindDispatch();
        d.setDispatchNo("MANUAL:DUP:1");
        d.setRunNo("MANUAL-RUN");
        d.setBillNo("DUP");
        d.setRowNo(1);
        d.setShipName(r.getShipName());
        d.setDueAt(r.getDueAt());
        d.setRemindAt(t("2026-10-15 08:00:00"));
        d.setStatus(THeriRemindDispatch.ST_SENT);
        d.setAfterCancelFlag(0);
        d.setDelFlag(0);
        d.setCreateBy(OP);
        d.setCreateTime(new Date());
        dispatchMapper.insert(d);

        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "DUP");
        assertEquals(0, rc.sentCount(), "认真发流水，不发第二回");
        assertEquals(1, rc.alreadyTodayCount());
        assertEquals(1, dispatchCount("DUP", 1, THeriRemindDispatch.ST_SENT), "不重发");
        assertEquals(Integer.valueOf(0), row("DUP", 1).getSentFlag(), "没动那条");
    }

    // ---------------- 中间一条发失败：跳过接着走下一条 ----------------

    @Test
    @DisplayName("中间一条发不顺：跳过它接着走下一条；痕上写明它自己不顺，不是没轮到")
    void failedRow_skippedAndDoesNotBlock() throws Exception {
        reg("A", "2026-10-18 09:00:00", "3", RULE, null);
        reg("B", "2026-10-18 09:00:00", "3", RULE, null);
        reg("C", "2026-10-18 09:00:00", "3", RULE, null);
        remindService.fail("B", 1);

        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "A", "B", "C");
        assertEquals(2, rc.sentCount());
        assertEquals(1, rc.failedCount());
        // 册上按单序：A 发、B 跳、C 接着发
        assertEquals("A", rc.getOutcomes().get(0).getBillNo());
        assertEquals(RemindRunReceipt.OUT_SENT, rc.getOutcomes().get(0).getOutcome());
        assertEquals("B", rc.getOutcomes().get(1).getBillNo());
        assertEquals(RemindRunReceipt.OUT_FAILED, rc.getOutcomes().get(1).getOutcome());
        assertEquals("C", rc.getOutcomes().get(2).getBillNo());
        assertEquals(RemindRunReceipt.OUT_SENT, rc.getOutcomes().get(2).getOutcome());

        THeriRemindRow bad = row("B", 1);
        assertEquals(Integer.valueOf(0), bad.getSentFlag(), "没发出去");
        assertEquals(Integer.valueOf(1), bad.getSkipFlag(), "跳过的留一笔痕");
        assertNotNull(bad.getFailReason());
        assertEquals(1, dispatchCount("B", 1, THeriRemindDispatch.ST_FAILED), "失败流水也在");
        // 留痕点得明白：它自己不顺，不是没轮到
        List<TSysOperLog> trail = remindService.listTrail("B");
        assertTrue(trail.stream().anyMatch(l -> "发不顺跳过".equals(l.getMethod())
                && l.getOperParam().contains("不是没轮到")));
        // C 没被堵上
        assertEquals(1, dispatchCount("C", 1, THeriRemindDispatch.ST_SENT));
    }

    // ---------------- 前头一条退了：后头几条照走 ----------------

    @Test
    @DisplayName("前头一条退了：后头照走，留痕点一句接着退件往下走；退了的追不回也不发")
    void canceledRow_doesNotBlock_laterRowsRun() throws Exception {
        reg("A", "2026-10-18 09:00:00", "3", RULE, "2026-10-01 09:00:00");
        reg("A", "2026-10-18 09:00:00", "3", RULE, "2026-10-02 09:00:00");
        reg("A", "2026-10-18 09:00:00", "3", RULE, "2026-10-03 09:00:00");
        // 行1 退了；按次序本该行1先跑，退了不堵，后头行3（改定最晚）认上
        THeriRemindRow canceled = remindService.cancel("A", 1, OP);
        assertEquals(2, canceled.getStatus(), "老提醒退了也留在册上，一条不减");

        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "A");
        assertEquals(1, rc.sentCount());
        assertEquals(Integer.valueOf(3), rc.getOutcomes().get(0).getRowNo(), "后头几条照走，认上的是行3");
        assertEquals(0, dispatchCount("A", 1, THeriRemindDispatch.ST_SENT)
                + dispatchCount("A", 1, THeriRemindDispatch.ST_FAILED),
                "退了的不发");
        // 留痕上点一句：这趟是接着一桩退件往下走的
        assertTrue(rc.getOutcomes().get(0).isAfterCancel());
        assertNotNull(rc.getCancelNote());
        THeriRemindRun run = remindService.findRun(rc.getRunNo());
        assertNotNull(run.getRemark());
        List<TSysOperLog> runTrail = operLogMapper.selectList(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", "/heri/remind/run/" + rc.getRunNo()));
        assertTrue(runTrail.stream().anyMatch(l -> "接退件".equals(l.getMethod())));
        // 已发的不能退
        assertThrows(IllegalStateException.class, () -> remindService.cancel("A", 3, OP));
    }

    // ---------------- 册上压根没有对应条目：整趟跳空 ----------------

    @Test
    @DisplayName("册上压根没有对应条目：整趟跳空写一个字：无，不顶替不造一条")
    void noRows_wholeRunBlank() throws Exception {
        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "NO-SUCH");
        assertTrue(rc.isEmpty());
        assertEquals("无", rc.emptyMark());
        assertEquals(THeriRemindRun.ST_EMPTY, rc.getRunStatus());
        assertEquals(0, rc.getOutcomes().size());
        THeriRemindRun run = remindService.findRun(rc.getRunNo());
        assertEquals(Integer.valueOf(1), run.getEmptyFlag());
        assertEquals("无", run.getRemark());
        List<TSysOperLog> trail = operLogMapper.selectList(new QueryWrapper<TSysOperLog>()
                .eq("oper_url", "/heri/remind/run/" + rc.getRunNo()));
        assertTrue(trail.stream().anyMatch(l -> "整趟跳空".equals(l.getMethod()) && "无".equals(l.getOperParam())));
        assertEquals(0, dispatchesOf(rc.getRunNo()).size());
        assertEquals(0, rowMapper.selectCount(new QueryWrapper<com.fc.v2.model.auto.THeriRemindRow>()
                .eq("bill_no", "NO-SUCH")), "不顺手造一条");
    }

    @Test
    @DisplayName("一批单里有空有实：不跳空，只认真实的；退干净也算有条")
    void mixedBills_notBlank() throws Exception {
        reg("REAL", "2026-10-18 09:00:00", "3", RULE, null);
        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "EMPTY", "REAL");
        assertFalse(rc.isEmpty());
        assertEquals(1, rc.sentCount());

        // 有单但条目全退了：册上有条，不叫跳空，只是不发
        reg("CANCELLED", "2026-10-18 09:00:00", "3", RULE, null);
        remindService.cancel("CANCELLED", 1, OP);
        RemindRunReceipt rc2 = runAt("2026-10-15 12:00:00", "CANCELLED");
        assertFalse(rc2.isEmpty());
        assertEquals(0, rc2.sentCount());
    }

    // ---------------- 按批处理：分页拉，半道超时停下，续的时候接着拉 ----------------

    @Test
    @DisplayName("条数多按批分页拉：拉到一半超时停下留笔，续跑同一趟接着拉，到头照发")
    void paging_timeoutPauseAndResume() throws Exception {
        for (int i = 1; i <= 5; i++) {
            reg("P" + i, "2026-10-18 09:00:00", "3", RULE, null);
        }
        final boolean[] blew = new boolean[]{false};
        com.fc.v2.service.impl.THeriRemindServiceImpl svc =
                new com.fc.v2.service.impl.THeriRemindServiceImpl() {
                    @Override
                    protected List<com.fc.v2.model.auto.THeriRemindRow> fetchPage(
                            List<String> billNos, Long afterId, int pageSize) {
                        List<com.fc.v2.model.auto.THeriRemindRow> page =
                                super.fetchPage(billNos, afterId, pageSize);
                        // 第二页拉取抖一回（超时）：只抖这一回，续跑就好
                        if (!blew[0] && afterId != null && !page.isEmpty()) {
                            blew[0] = true;
                            throw new RuntimeException("拉到一半超时了");
                        }
                        return page;
                    }
                };
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "remindRuleMapper", this.ruleMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "remindRowMapper", this.rowMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "remindDispatchMapper", this.dispatchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "remindRunMapper", this.runMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "sysOperLogMapper", this.operLogMapper);

        List<String> bills = new ArrayList<String>(Arrays.asList("P1", "P2", "P3", "P4", "P5"));
        // 第一趟：每页 2 条，第 2 页超时 → 停下留笔，已拉 2 条，一条都不发
        RemindRunReceipt first = svc.runOnce(bills, t("2026-10-15 09:00:00"), 2, OP);
        assertEquals(THeriRemindRun.ST_RUNNING, first.getRunStatus());
        assertEquals(2, first.getFetchedThisCall());
        assertEquals(0, first.sentCount());
        assertNotNull(first.getPauseNote());
        THeriRemindRun run = svc.findRun(first.getRunNo());
        assertEquals(Integer.valueOf(2), run.getPageSize());
        assertEquals(Integer.valueOf(2), run.getFetchedRows());
        assertNotNull(run.getLastRowId(), "断点留着");
        assertEquals(0, dispatchesOf(first.getRunNo()).size());

        // 续的时候接着拉：同一趟、不另起，把剩下 3 条拉完照发
        RemindRunReceipt again = svc.runOnce(bills, t("2026-10-15 10:00:00"), 2, OP);
        assertTrue(again.isContinued());
        assertEquals(first.getRunNo(), again.getRunNo());
        assertEquals(3, again.getFetchedThisCall());
        assertEquals(5, again.sentCount(), "5 条全拉完、全发");
        assertEquals(Integer.valueOf(5), svc.findRun(first.getRunNo()).getFetchedRows());
        assertEquals(THeriRemindRun.ST_DONE, again.getRunStatus());
        assertNull(svc.findRun(first.getRunNo()).getLastRowId(), "拉完了，断点清掉");
    }

    // ---------------- 抽验：随机抓一条倒着核 ----------------

    @Test
    @DisplayName("抽验倒核：认上的跟真发出去的对得上；对不齐的几条按条点名")
    void audit_backward_namesMismatches() throws Exception {
        reg("A", "2026-10-18 09:00:00", "3", RULE, null);
        reg("B", "2026-10-18 09:00:00", "3", RULE, null);
        reg("C", "2026-10-18 09:00:00", "3", RULE, null);
        RemindRunReceipt rc = runAt("2026-10-15 09:00:00", "A", "B", "C");
        List<THeriRemindDispatch> sent = dispatchesOf(rc.getRunNo()).stream()
                .filter(d -> d.getStatus() == THeriRemindDispatch.ST_SENT)
                .collect(java.util.stream.Collectors.toList());
        assertEquals(3, sent.size());

        // 指定抓第 2 条倒着核（验随机抓一条的口径）
        RemindAuditResult ok = remindService.auditBackward(rc.getRunNo(), sent.get(1).getId());
        assertTrue(ok.isPass());
        assertTrue(ok.isSampleMatched());
        assertEquals("B", ok.getSampledBillNo());

        // 把两条改花：一条册上没标已发、一条到口时刻对不上
        THeriRemindRow a = row("A", 1);
        a.setSentFlag(0);
        rowMapper.updateById(a);
        THeriRemindRow c = row("C", 1);
        c.setDueAt(t("2026-10-19 09:00:00"));
        rowMapper.updateById(c);

        RemindAuditResult bad = remindService.auditBackward(rc.getRunNo(), sent.get(1).getId());
        assertFalse(bad.isPass());
        assertTrue(bad.isSampleMatched(), "抓的 B 本身还是齐的，不齐的是别的条");
        // 对不齐的几条按条点名，不许含糊过去
        assertEquals(2, bad.getMismatches().size());
        assertTrue(bad.getMismatches().stream().anyMatch(n -> n.contains("A") && n.contains("已发")));
        assertTrue(bad.getMismatches().stream().anyMatch(n -> n.contains("C") && n.contains("到口")));
    }
}
