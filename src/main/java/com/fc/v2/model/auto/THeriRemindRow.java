package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 到港滞期提醒条目册对象 t_heri_remind_row
 *
 * <p>往年入册的老提醒一条不减，这条读法定死不许改出口（del_flag 恒 0）。
 * 同一单多条，先比改定得晚的（revised_at），一样晚看到口（due_at）更近，
 * 再平看册上行次（row_no）；定死一条，其余不吭声，同一天不许连发两回。</p>
 *
 * <p>日子要往前挪只动还没发出去的（sent_flag=0）；已经发出去的留在原处，
 * 不追回来重发，也不挪到后头去。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_remind_row")
@ApiModel(value = "THeriRemindRow", description = "到港滞期提醒条目册")
public class THeriRemindRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 条目状态 0待提醒 */
    public static final int ST_PENDING = 0;
    /** 条目状态 1已提醒（终态：已发出去，日子前挪不动它） */
    public static final int ST_REMINDED = 1;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 单据号(同一单多条提醒按它归并) */
    @TableField("bill_no")
    @ApiModelProperty(value = "单据号")
    private String billNo;

    /** 册上行次(次序最后看它) */
    @TableField("row_no")
    @ApiModelProperty(value = "册上行次")
    private Integer rowNo;

    /** 船名航次 */
    @TableField("ship_name")
    @ApiModelProperty(value = "船名航次")
    private String shipName;

    /** 到口时刻(滞期/到港点，提前量往前挪看它) */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "到口时刻")
    private Date dueAt;

    /** 本提醒提前量(天，立册时从判据誊死) */
    @TableField("lead_days")
    @ApiModelProperty(value = "提前量(天)")
    private Integer leadDays;

    /** 提前量取自哪条判据(立册誊本) */
    @TableField("rule_code")
    @ApiModelProperty(value = "判据编号")
    private String ruleCode;

    /** 改定时刻(先改定得晚的先跑) */
    @TableField("revised_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "改定时刻")
    private Date revisedAt;

    /** 最近真发出去那趟的趟号 */
    @TableField("last_dispatch_no")
    @ApiModelProperty(value = "最近真发趟号")
    private String lastDispatchNo;

    /** 是否已发出去 0没发 1已发(已发终态，日子前挪不动它) */
    @TableField("sent_flag")
    @ApiModelProperty(value = "是否已发出去")
    private Integer sentFlag;

    /** 发不顺缘由(它自己不顺，不是没轮到) */
    @TableField("fail_reason")
    @ApiModelProperty(value = "发不顺缘由")
    private String failReason;

    /** 本趟是否跳过(发失败跳过留痕) 0否1是 */
    @TableField("skip_flag")
    @ApiModelProperty(value = "本趟是否跳过")
    private Integer skipFlag;

    /** 本趟是否接在退件后 0否1是 */
    @TableField("after_cancel_flag")
    @ApiModelProperty(value = "本趟是否接在退件后")
    private Integer afterCancelFlag;

    /** 条目状态 0待提醒 1已提醒(终态) */
    @TableField("status")
    @ApiModelProperty(value = "条目状态")
    private Integer status;

    /** 删除标记 恒0(往年入册的老提醒一条不减) */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableField("remark")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getLeadDays() {
        return leadDays;
    }

    public void setLeadDays(Integer leadDays) {
        this.leadDays = leadDays;
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

    public String getLastDispatchNo() {
        return lastDispatchNo;
    }

    public void setLastDispatchNo(String lastDispatchNo) {
        this.lastDispatchNo = lastDispatchNo;
    }

    public Integer getSentFlag() {
        return sentFlag;
    }

    public void setSentFlag(Integer sentFlag) {
        this.sentFlag = sentFlag;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getSkipFlag() {
        return skipFlag;
    }

    public void setSkipFlag(Integer skipFlag) {
        this.skipFlag = skipFlag;
    }

    public Integer getAfterCancelFlag() {
        return afterCancelFlag;
    }

    public void setAfterCancelFlag(Integer afterCancelFlag) {
        this.afterCancelFlag = afterCancelFlag;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
