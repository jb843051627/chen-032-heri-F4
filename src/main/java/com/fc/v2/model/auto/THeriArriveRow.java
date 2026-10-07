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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 到港提醒条目对象 t_heri_arr_row（append-only，往年入册的老提醒一条不减）。
 *
 * <p>同单多条提醒只认一条：先比改定得晚的，一样晚看到口更近的；认死那一条其余不吭声，
 * 同一天不许连发两回。日子往前挪只动还没发出去的，已发的留在原处。发不顺标发不顺留痕、
 * 跳过不堵后头；前一条退了，后一条带“退件后续”照走。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_arr_row")
@ApiModel(value = "THeriArriveRow", description = "到港提醒条目")
public class THeriArriveRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 行状态：0待发 */
    public static final int ST_PENDING = 0;
    /** 行状态：1已发 */
    public static final int ST_SENT = 1;
    /** 行状态：2发不顺(自己没发出去，留痕跳过；不是没轮到) */
    public static final int ST_FAILED = 2;
    /** 行状态：3已退回 */
    public static final int ST_RETURNED = 3;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 单号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "单号")
    private String billNo;

    /** 条目编号(同单内唯一) */
    @TableField("item_no")
    @ApiModelProperty(value = "条目编号")
    private String itemNo;

    /** 到口(到港/滞期)时刻 */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "到口时刻")
    private Date dueAt;

    /** 排定提醒时刻=到口往前挪提前量 */
    @TableField("remind_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "排定提醒时刻")
    private Date remindAt;

    /** 本条提前量(册上誊本) */
    @TableField("lead_days")
    @ApiModelProperty(value = "本条提前量")
    private Integer leadDays;

    /** 改定时刻(认条先比它：晚的先) */
    @TableField("revised_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "改定时刻")
    private Date revisedAt;

    /** 提醒方式(只记不判) */
    @TableField("notify_type")
    @ApiModelProperty(value = "提醒方式")
    private String notifyType;

    /** 滞期费(元) */
    @TableField("amount")
    @ApiModelProperty(value = "滞期费(元)")
    private BigDecimal amount;

    /** 船名航次 */
    @TableField("ship_name")
    @ApiModelProperty(value = "船名航次")
    private String shipName;

    /** 0待发 1已发 2发不顺 3已退回 */
    @TableField("status")
    @ApiModelProperty(value = "0待发 1已发 2发不顺 3已退回")
    private Integer status;

    /** 真正发出时刻 */
    @TableField("send_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "真正发出时刻")
    private Date sendTime;

    /** 发不顺/退件缘由留痕 */
    @TableField("fail_reason")
    @ApiModelProperty(value = "发不顺/退件缘由留痕")
    private String failReason;

    /** 是不是接着一桩退件往下走的 0否1是 */
    @TableField("after_return")
    @ApiModelProperty(value = "退件后续标记")
    private Integer afterReturn;

    /** 删除标记 恒0(条目只许追加，老条一条不减) */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
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

    public Date getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(Date remindAt) {
        this.remindAt = remindAt;
    }

    public Integer getLeadDays() {
        return leadDays;
    }

    public void setLeadDays(Integer leadDays) {
        this.leadDays = leadDays;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getSendTime() {
        return sendTime;
    }

    public void setSendTime(Date sendTime) {
        this.sendTime = sendTime;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getAfterReturn() {
        return afterReturn;
    }

    public void setAfterReturn(Integer afterReturn) {
        this.afterReturn = afterReturn;
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
        return this.updateTime;
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
