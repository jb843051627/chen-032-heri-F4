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
 * 提醒派发趟次对象 t_heri_arr_run（按批分页拉，超时留笔、续时接着拉）。
 *
 * <p>一趟要认的条数多就按批处理走、分页拉；拉到一半超时停下留一笔（status=0 + cursor_bill），
 * 续的时候用同趟号接着拉。册上压根没有对应条目，整趟跳空，result_text 就写一个字“无”。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_arr_run")
@ApiModel(value = "THeriArriveRun", description = "提醒派发趟次")
public class THeriArriveRun implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 趟态：0拉取中(超时停在半路，留了断点) */
    public static final int ST_PAGING = 0;
    /** 趟态：1走完 */
    public static final int ST_DONE = 1;

    /** 整趟跳空写的那一个字 */
    public static final String EMPTY_TEXT = "无";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 趟号(分页续拉同一趟共用) */
    @TableField("run_no")
    @ApiModelProperty(value = "趟号")
    private String runNo;

    /** 本趟派发的单(空=按批扫所有在册单) */
    @TableField("scope_bill")
    @ApiModelProperty(value = "本趟派发的单")
    private String scopeBill;

    /** 0拉取中(超时停半路) 1走完 */
    @TableField("status")
    @ApiModelProperty(value = "趟态 0拉取中 1走完")
    private Integer status;

    /** 分页断点：续拉从这一单往后接着拉 */
    @TableField("cursor_bill")
    @ApiModelProperty(value = "分页断点(续拉起点)")
    private String cursorBill;

    /** 每页拉几单(按批处理走) */
    @TableField("page_size")
    @ApiModelProperty(value = "每页几单")
    private Integer pageSize;

    /** 整趟结果；整趟跳空就写一个字 无 */
    @TableField("result_text")
    @ApiModelProperty(value = "整趟结果；跳空写 无")
    private String resultText;

    /** 本趟真发出条数 */
    @TableField("sent_rows")
    @ApiModelProperty(value = "本趟发出条数")
    private Integer sentRows;

    /** 本趟发不顺条数 */
    @TableField("fail_rows")
    @ApiModelProperty(value = "本趟发不顺条数")
    private Integer failRows;

    /** 本趟带退件后续的条数 */
    @TableField("return_rows")
    @ApiModelProperty(value = "本趟退件后续条数")
    private Integer returnRows;

    /** 超时停半/续拉/退件后续等留一笔 */
    @TableField("note")
    @ApiModelProperty(value = "留笔")
    private String note;

    /** 删除标记 0正常 1删除 */
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

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
    }

    public String getScopeBill() {
        return scopeBill;
    }

    public void setScopeBill(String scopeBill) {
        this.scopeBill = scopeBill;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCursorBill() {
        return cursorBill;
    }

    public void setCursorBill(String cursorBill) {
        this.cursorBill = cursorBill;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
