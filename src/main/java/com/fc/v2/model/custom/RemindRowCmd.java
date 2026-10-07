package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 提醒条目入册命令：一条到港/滞期提醒。
 * 提前量那格（leadDaysRaw）原始写法交进来——写几天就几天；空着/负数/说不上来的大数
 * 都按空着办（按一日），册上只记誊定后的 leadDays，行次由册上排、交单方不填。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "RemindRowCmd", description = "提醒条目入册命令")
public class RemindRowCmd implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "单据号(同一单多条提醒按它归并)")
    private String billNo;

    @ApiModelProperty(value = "船名航次(只记不判)")
    private String shipName;

    @ApiModelProperty(value = "到口时刻(到港/滞期点)")
    private Date dueAt;

    @ApiModelProperty(value = "提前量那格原样写法：空/负数/说不上来的大数按空着办(按一日)")
    private String leadDaysRaw;

    @ApiModelProperty(value = "提前量取自哪条判据")
    private String ruleCode;

    @ApiModelProperty(value = "改定时刻(先改定得晚的先跑)")
    private Date revisedAt;

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public String getLeadDaysRaw() {
        return leadDaysRaw;
    }

    public void setLeadDaysRaw(String leadDaysRaw) {
        this.leadDaysRaw = leadDaysRaw;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getRevisedAt() {
        return revisedAt;
    }

    public void setRevisedAt(Date revisedAt) {
        this.revisedAt = revisedAt;
    }
}
