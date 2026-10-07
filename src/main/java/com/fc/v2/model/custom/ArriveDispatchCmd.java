package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 一趟提醒派发命令：认条目按批分页拉。billNo 空着=按批扫所有在册单；
 * runNo 空着=开一趟新的，填上=上趟超时停半后续拉（接着断点走）。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "ArriveDispatchCmd", description = "提醒派发命令")
public class ArriveDispatchCmd implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "趟号(空=开新趟；填上=同趟分页续拉)")
    private String runNo;

    @ApiModelProperty(value = "本趟派哪一单(空=按批扫所有在册单)")
    private String billNo;

    @ApiModelProperty(value = "派发判定时刻(空按当前)")
    private Date at;

    @ApiModelProperty(value = "每页拉几单(空按默认)")
    private Integer pageSize;

    @ApiModelProperty(value = "本趟时限毫秒：分页拉到一半超过它就停下留一笔(空=不限时)")
    private Long timeoutMs;

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

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
