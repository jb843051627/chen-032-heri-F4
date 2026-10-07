package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽验倒核结果：随机抓一条倒着核——认上的那条，跟这趟真发出去的那条对不对得上；
 * 对不齐的几条按条点名，不许含糊过去。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "RemindAuditResult", description = "提醒趟抽验倒核结果")
public class RemindAuditResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 趟号 */
    private String runNo;

    /** 随机抓到的那一条：真发流水上的那条（册上是第几行） */
    private String sampledBillNo;
    private Integer sampledRowNo;

    /** 抓的那条倒着核对不对得上 */
    private boolean sampleMatched;

    /** 全趟真发条逐条倒核：对不齐的按条点名 */
    private List<String> mismatches = new ArrayList<String>();

    /** 抽验过不过：抓的那条对得上，且全趟没有对不齐的条 */
    private boolean pass;

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
    }

    public String getSampledBillNo() {
        return sampledBillNo;
    }

    public void setSampledBillNo(String sampledBillNo) {
        this.sampledBillNo = sampledBillNo;
    }

    public Integer getSampledRowNo() {
        return sampledRowNo;
    }

    public void setSampledRowNo(Integer sampledRowNo) {
        this.sampledRowNo = sampledRowNo;
    }

    public boolean isSampleMatched() {
        return sampleMatched;
    }

    public void setSampleMatched(boolean sampleMatched) {
        this.sampleMatched = sampleMatched;
    }

    public List<String> getMismatches() {
        return mismatches;
    }

    public void setMismatches(List<String> mismatches) {
        this.mismatches = mismatches;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }
}
