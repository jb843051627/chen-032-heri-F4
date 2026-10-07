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
 * 提醒排期趟头对象 t_heri_remind_run
 *
 * <p>一趟要认的条数多，按批处理走，分页拉，拉到一半超时就停下留一笔，续的时候接着拉。
 * 每个排期时刻一趟：已有的趟若还在进行中（半道超时停下），同一批 billNo 续这一趟；
 * 一批全走完才另起一趟。</p>
 *
 * @author fuce
* @date 2026-10-07
 */
@TableName("t_heri_remind_run")
@ApiModel(value = "THeriRemindRun", description = "提醒排期趟头")
public class THeriRemindRun implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 趟态：0进行中(拉到一半超时停下) */
    public static final int ST_RUNNING = 0;
    /** 趟态：1走完 */
    public static final int ST_DONE = 1;
    /** 趟态：2整趟跳空(册上压根没有对应条目，写一个字：无) */
    public static final int ST_EMPTY = 2;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 趟号(一趟一号，续跑沿用同一个号) */
    @TableField("run_no")
    @ApiModelProperty(value = "趟号")
    private String runNo;

    /** 本趟认的那批单(逗号隔开，续跑照它) */
    @TableField("bill_nos")
    @ApiModelProperty(value = "本趟认的那批单")
    private String billNos;

    /** 趟态 0进行中 1走完 2整趟跳空 */
    @TableField("status")
    @ApiModelProperty(value = "趟态")
    private Integer status;

    /** 累计已拉条数 */
    @TableField("fetched_rows")
    @ApiModelProperty(value = "累计已拉条数")
    private Integer fetchedRows;

    /** 累计真发数 */
    @TableField("sent_rows")
    @ApiModelProperty(value = "累计真发数")
    private Integer sentRows;

    /** 累计发失败跳过数 */
    @TableField("failed_rows")
    @ApiModelProperty(value = "累计发失败跳过数")
    private Integer failedRows;

    /** 累计没轮到(被同单先跑的占了/同天已发)条数 */
    @TableField("skipped_rows")
    @ApiModelProperty(value = "累计没轮到条数")
    private Integer skippedRows;

    /** 整趟是否跳空 0否 1是(跳空写一个字：无) */
    @TableField("empty_flag")
    @ApiModelProperty(value = "整趟是否跳空")
    private Integer emptyFlag;

    /** 每页拉几条(批处理分页大小) */
    @TableField("page_size")
    @ApiModelProperty(value = "每页拉几条")
    private Integer pageSize;

    /** 续拉游标：上一页最后一条册目主键，接着它往后拉 */
    @TableField("last_row_id")
    @ApiModelProperty(value = "续拉游标")
    private Long lastRowId;

    /** 留笔(半道超时/退件接续等) */
    @TableField("remark")
    @ApiModelProperty(value = "留笔")
    private String remark;

    /** 删除标记 0正常 1删除 */
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

    public String getBillNos() {
        return billNos;
    }

    public void setBillNos(String billNos) {
        this.billNos = billNos;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getFetchedRows() {
        return fetchedRows;
    }

    public void setFetchedRows(Integer fetchedRows) {
        this.fetchedRows = fetchedRows;
    }

    public Integer getSentRows() {
        return sentRows;
    }

    public void setSentRows(Integer sentRows) {
        this.sentRows = sentRows;
    }

    public Integer getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(Integer failedRows) {
        this.failedRows = failedRows;
    }

    public Integer getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(Integer skippedRows) {
        this.skippedRows = skippedRows;
    }

    public Integer getEmptyFlag() {
        return emptyFlag;
    }

    public void setEmptyFlag(Integer emptyFlag) {
        this.emptyFlag = emptyFlag;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getLastRowId() {
        return lastRowId;
    }

    public void setLastRowId(Long lastRowId) {
        this.lastRowId = lastRowId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
}
