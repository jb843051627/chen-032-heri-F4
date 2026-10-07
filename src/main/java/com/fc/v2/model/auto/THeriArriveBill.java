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
 * 到港提醒单册对象 t_heri_arr_bill（一单一册）。
 *
 * <p>注册时按港里判据把提前天数誊死在头上：三处看法（判据册/单册/条目）落到同一处时，
 * 以册上这处为准。往后判据再改，老册老提醒一条不减、不重排。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_arr_bill")
@ApiModel(value = "THeriArriveBill", description = "到港提醒单册")
public class THeriArriveBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 单号(册号，一单一号) */
    @TableField("bill_no")
    @ApiModelProperty(value = "单号(册号)")
    private String billNo;

    /** 船名航次 */
    @TableField("ship_name")
    @ApiModelProperty(value = "船名航次")
    private String shipName;

    /** 注册时认的判据编号(誊本来源) */
    @TableField("rule_code")
    @ApiModelProperty(value = "注册时认的判据编号")
    private String ruleCode;

    /** 提前天数(注册时誊死，判据改了不追老册) */
    @TableField("lead_days")
    @ApiModelProperty(value = "提前天数(注册誊死)")
    private Integer leadDays;

    /** 册态 0在册 1已发齐 */
    @TableField("status")
    @ApiModelProperty(value = "册态 0在册 1已发齐")
    private Integer status;

    /** 入册提醒条数(往年老条一条不减) */
    @TableField("total_rows")
    @ApiModelProperty(value = "入册提醒条数")
    private Integer totalRows;

    /** 累计发出条数 */
    @TableField("sent_rows")
    @ApiModelProperty(value = "累计发出条数")
    private Integer sentRows;

    /** 累计发不顺条数(已留痕) */
    @TableField("fail_rows")
    @ApiModelProperty(value = "累计发不顺条数")
    private Integer failRows;

    /** 累计退件条数 */
    @TableField("return_rows")
    @ApiModelProperty(value = "累计退件条数")
    private Integer returnRows;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
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

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Integer getLeadDays() {
        return leadDays;
    }

    public void setLeadDays(Integer leadDays) {
        this.leadDays = leadDays;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getSentRows() {
        return sentRows;
    }

    public void setSentRows(Integer sentRows) {
        this.sentRows = sentRows;
    }

    public Integer getFailRows() {
        return failRows;
    }

    public void setFailRows(Integer failRows) {
        this.failRows = failRows;
    }

    public Integer getReturnRows() {
        return returnRows;
    }

    public void setReturnRows(Integer returnRows) {
        this.returnRows = returnRows;
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
