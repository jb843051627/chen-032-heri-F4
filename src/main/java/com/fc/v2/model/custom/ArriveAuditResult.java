package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽验倒核结果：随机抓一条，把“派发时认上的那条”跟“这趟真发出去的那条”对一对。
 * 对不齐的逐条点名（认上的没发 / 发了的不是认上的 / 同一天连发两回 等），不许含糊。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "ArriveAuditResult", description = "提醒抽验倒核结果")
public class ArriveAuditResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "被随机抓到的单号")
    private String billNo;

    @ApiModelProperty(value = "派发时认上的条目编号")
    private String recognizedItemNo;

    @ApiModelProperty(value = "这趟真发出去的条目编号")
    private String sentItemNo;

    @ApiModelProperty(value = "认上的与真发的对不对得上")
    private boolean match;

    @ApiModelProperty(value = "对不齐/不合口径处逐一点名")
    private List<String> problems = new ArrayList<String>();

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getRecognizedItemNo() {
        return recognizedItemNo;
    }

    public void setRecognizedItemNo(String recognizedItemNo) {
        this.recognizedItemNo = recognizedItemNo;
    }

    public String getSentItemNo() {
        return sentItemNo;
    }

    public void setSentItemNo(String sentItemNo) {
        this.sentItemNo = sentItemNo;
    }

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }
}
