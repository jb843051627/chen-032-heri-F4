package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 提醒排期一趟回执：这趟拉了多少、真发几条、跳过哪几条、是不是半道超时停下、
 * 是不是整趟跳空（跳空只写一个字：无）。
 *
 * <p>逐行点名：本趟轮到的每一条都在 outcomes 里点得明白——
 * 真发/发不顺/被同单先跑的占了/同天已发不重发，不许含糊。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "RemindRunReceipt", description = "提醒排期一趟回执")
public class RemindRunReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 真发出去 */
    public static final String OUT_SENT = "SENT";
    /** 发不顺，跳过接着走下一条（它自己不顺，不是没轮到） */
    public static final String OUT_FAILED = "FAILED";
    /** 同单先跑的那条已定死，这一条不吭声 */
    public static final String OUT_RANK_SKIP = "RANK_SKIP";
    /** 同一天已发过这一条，不许连发两回 */
    public static final String OUT_ALREADY_TODAY = "ALREADY_TODAY";
    /** 这页拉到一半超时，本趟停下 */
    public static final String OUT_TIMEOUT = "TIMEOUT";

    /** 趟号 */
    private String runNo;

    /** 本趟是不是接着上回半道超时那趟往下跑 */
    private boolean continued;

    /** 每页几条 */
    private Integer pageSize;

    /** 趟态：0进行中(半道超时停下) 1走完 2整趟跳空(册上压根没有对应条目) */
    private Integer runStatus;

    /** 整趟跳空：跳空就写一个字——无 */
    private boolean empty;

    /** 本次调用拉到的候选条数 */
    private int fetchedThisCall;

    /** 逐行结果（本趟轮到的，逐条点名） */
    private List<Item> outcomes = new ArrayList<Item>();

    /** 半道超时留笔 */
    private String pauseNote;

    /** 接着退件往下走的留笔 */
    private String cancelNote;

    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        private String billNo;
        private Integer rowNo;
        private String outcome;
        private String reason;
        /** 是不是接着一桩退件往下走的那一条（同趟只点一句） */
        private boolean afterCancel;

        public Item() {
        }

        public Item(String billNo, Integer rowNo, String outcome, String reason, boolean afterCancel) {
            this.billNo = billNo;
            this.rowNo = rowNo;
            this.outcome = outcome;
            this.reason = reason;
            this.afterCancel = afterCancel;
        }

        public String getBillNo() {
            return billNo;
        }

        public void setBillNo(String billNo) {
            this.billNo = billNo;
        }

        public Integer getRowNo() {
            return rowNo;
        }

        public void setRowNo(Integer rowNo) {
            this.rowNo = rowNo;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public boolean isAfterCancel() {
            return afterCancel;
        }

        public void setAfterCancel(boolean afterCancel) {
            this.afterCancel = afterCancel;
        }
    }

    /** 整趟跳空的字据：就一个字，无 */
    public String emptyMark() {
        return this.empty ? "无" : null;
    }

    public int sentCount() {
        return count(OUT_SENT);
    }

    public int failedCount() {
        return count(OUT_FAILED);
    }

    public int rankSkipCount() {
        return count(OUT_RANK_SKIP);
    }

    public int alreadyTodayCount() {
        return count(OUT_ALREADY_TODAY);
    }

    private int count(String out) {
        int n = 0;
        for (Item it : this.outcomes) {
            if (out.equals(it.getOutcome())) {
                n++;
            }
        }
        return n;
    }

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
    }

    public boolean isContinued() {
        return continued;
    }

    public void setContinued(boolean continued) {
        this.continued = continued;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getRunStatus() {
        return runStatus;
    }

    public void setRunStatus(Integer runStatus) {
        this.runStatus = runStatus;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public int getFetchedThisCall() {
        return fetchedThisCall;
    }

    public void setFetchedThisCall(int fetchedThisCall) {
        this.fetchedThisCall = fetchedThisCall;
    }

    public List<Item> getOutcomes() {
        return outcomes;
    }

    public void setOutcomes(List<Item> outcomes) {
        this.outcomes = outcomes;
    }

    public String getPauseNote() {
        return pauseNote;
    }

    public void setPauseNote(String pauseNote) {
        this.pauseNote = pauseNote;
    }

    public String getCancelNote() {
        return cancelNote;
    }

    public void setCancelNote(String cancelNote) {
        this.cancelNote = cancelNote;
    }
}
