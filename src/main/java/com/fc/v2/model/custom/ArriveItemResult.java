package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 一条提醒在一趟里的下落：真发出 / 发不顺跳过 / 自己没到点没轮到，分得清。
 * 发不顺那条要记得是“它自己不顺”，不是没轮到；退件后续要点一句。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "ArriveItemResult", description = "单条提醒派发结果")
public class ArriveItemResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 真发出 */
    public static final String SENT = "SENT";
    /** 发不顺：自己没发出去，留痕跳过，不堵后头 */
    public static final String FAILED = "FAILED";
    /** 没轮到：被认条次序压下/还没到点/已发过（不吭声，不算发不顺） */
    public static final String HELD = "HELD";
    /** 已退回：前一条退了，这条作为退件后续接着走（真走了另记 SENT） */
    public static final String RETURNED = "RETURNED";

    @ApiModelProperty(value = "单号")
    private String billNo;

    @ApiModelProperty(value = "条目编号")
    private String itemNo;

    @ApiModelProperty(value = "下落 SENT/FAILED/HELD/RETURNED")
    private String outcome;

    @ApiModelProperty(value = "这趟真发出去没有")
    private boolean sent;

    @ApiModelProperty(value = "是不是接着一桩退件往下走的")
    private boolean afterReturn;

    @ApiModelProperty(value = "缘由(发不顺/压下/退件后续等)")
    private String reason;

    public ArriveItemResult() {
    }

    public ArriveItemResult(String billNo, String itemNo, String outcome, boolean sent,
                            boolean afterReturn, String reason) {
        this.billNo = billNo;
        this.itemNo = itemNo;
        this.outcome = outcome;
        this.sent = sent;
        this.afterReturn = afterReturn;
        this.reason = reason;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public boolean isAfterReturn() {
        return afterReturn;
    }

    public void setAfterReturn(boolean afterReturn) {
        this.afterReturn = afterReturn;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
