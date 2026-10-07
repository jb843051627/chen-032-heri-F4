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
 * 提醒真发流水对象 t_heri_remind_dispatch
 *
 * <p>append-only：真发出去的提醒一笔一道痕，发失败也留一笔（它自己不顺，不是没轮到），
 * 谁都不许悄悄抹。同一天同一条不许连发两回。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_remind_dispatch")
@ApiModel(value = "THeriRemindDispatch", description = "提醒真发流水")
public class THeriRemindDispatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 流水态：1已发 */
    public static final int ST_SENT = 1;
    /** 流水态：2发失败(跳过它接着走下一条，别让它把后头几条堵住) */
    public static final int ST_FAILED = 2;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 趟内发送流水号(唯一，幂等不重发) */
    @TableField("dispatch_no")
    @ApiModelProperty(value = "趟内发送流水号")
    private String dispatchNo;

    /** 哪一趟认的(抽验倒核对趟) */
    @TableField("run_no")
    @ApiModelProperty(value = "哪一趟认的")
    private String runNo;

    /** 单据号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "单据号")
    private String billNo;

    /** 册上行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "册上行次")
    private Integer rowNo;

    /** 船名航次 */
    @TableField("ship_name")
    @ApiModelProperty(value = "船名航次")
    private String shipName;

    /** 到口时刻 */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "到口时刻")
    private Date dueAt;

    /** 提醒时刻(到口往前挪提前量那一刻) */
    @TableField("remind_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提醒时刻")
    private Date remindAt;

    /** 流水态 1已发 2发失败 */
    @TableField("status")
    @ApiModelProperty(value = "流水态")
    private Integer status;

    /** 发不顺缘由 */
    @TableField("fail_reason")
    @ApiModelProperty(value = "发不顺缘由")
    private String failReason;

    /** 是否接着一桩退件往下走的 0否1是 */
    @TableField("after_cancel_flag")
    @ApiModelProperty(value = "是否接在退件后")
    private Integer afterCancelFlag;

    /** 删除标记 恒0(流水只许追加) */
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

    public String getDispatchNo() {
        return dispatchNo;
    }

    public void setDispatchNo(String dispatchNo) {
        this.dispatchNo = dispatchNo;
    }

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
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

    public Date getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(Date remindAt) {
        this.remindAt = remindAt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getAfterCancelFlag() {
        return afterCancelFlag;
    }

    public void setAfterCancelFlag(Integer afterCancelFlag) {
        this.afterCancelFlag = afterCancelFlag;
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
