package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 一条提醒入册项：到口时刻、改定时刻交进来；提醒时刻册上按单册誊死的提前量算，不交单方填。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "ArriveItemInput", description = "提醒入册项")
public class ArriveItemInput implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "条目编号(同单内唯一，认条/退件都认它)")
    private String itemNo;

    @ApiModelProperty(value = "到口(到港/滞期)时刻")
    private Date dueAt;

    @ApiModelProperty(value = "改定时刻(认条先比它：晚的先)；空按入册时刻")
    private Date revisedAt;

    @ApiModelProperty(value = "提醒方式(只记不判)")
    private String notifyType;

    @ApiModelProperty(value = "滞期费(元，只记不判)")
    private java.math.BigDecimal amount;

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public Date getRevisedAt() {
        return revisedAt;
    }

    public void setRevisedAt(Date revisedAt) {
        this.revisedAt = revisedAt;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public void setNotifyType(String notifyType) {
        this.notifyType = notifyType;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal amount) {
        this.amount = amount;
    }
}
