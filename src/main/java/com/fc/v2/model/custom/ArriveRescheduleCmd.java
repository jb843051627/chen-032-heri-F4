package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 日子往前挪命令：把某条提醒的到口改早。只动还没发出去的——已发的留在原处，
 * 不追回来重发，也不挪到后头去。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "ArriveRescheduleCmd", description = "提醒改期命令")
public class ArriveRescheduleCmd implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "单号")
    private String billNo;

    @ApiModelProperty(value = "条目编号")
    private String itemNo;

    @ApiModelProperty(value = "新的到口时刻(只许比原来早)")
    private Date newDueAt;

    @ApiModelProperty(value = "改定时刻(认条次序的晚先比它；空按当前)")
    private Date revisedAt;

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

    public Date getNewDueAt() {
        return newDueAt;
    }

    public void setNewDueAt(Date newDueAt) {
        this.newDueAt = newDueAt;
    }

    public Date getRevisedAt() {
        return revisedAt;
    }

    public void setRevisedAt(Date revisedAt) {
        this.revisedAt = revisedAt;
    }
}
