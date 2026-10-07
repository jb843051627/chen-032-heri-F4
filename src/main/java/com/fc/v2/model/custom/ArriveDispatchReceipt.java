package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一趟派发回执：走完没、是不是超时停在半路（断点在哪）、整趟跳空写“无”、
 * 真发/发不顺/退件后续各几条，每条下落都在 results 里。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "ArriveDispatchReceipt", description = "提醒派发回执")
public class ArriveDispatchReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 走完 */
    public static final String DONE = "DONE";
    /** 分页拉到一半超时，停在半路，留了断点 */
    public static final String PAGING = "PAGING";

    @ApiModelProperty(value = "趟号(续拉用它)")
    private String runNo;

    @ApiModelProperty(value = "本趟派的单(空=按批扫)")
    private String scopeBill;

    @ApiModelProperty(value = "DONE 走完 / PAGING 超时停半路")
    private String status;

    @ApiModelProperty(value = "续拉断点(停在/下一趟从哪一单往后)")
    private String cursorBill;

    @ApiModelProperty(value = "整趟跳空")
    private boolean empty;

    @ApiModelProperty(value = "整趟结果；跳空就一个字 无")
    private String resultText;

    @ApiModelProperty(value = "本趟真发出条数")
    private int sentRows;

    @ApiModelProperty(value = "本趟发不顺条数")
    private int failRows;

    @ApiModelProperty(value = "本趟退件后续条数")
    private int returnRows;

    @ApiModelProperty(value = "每条真有下落的条目(真发/发不顺)；被次序压下的不吭声不列")
    private List<ArriveItemResult> results = new ArrayList<ArriveItemResult>();

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCursorBill() {
        return cursorBill;
    }

    public void setCursorBill(String cursorBill) {
        this.cursorBill = cursorBill;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public int getSentRows() {
        return sentRows;
    }

    public void setSentRows(int sentRows) {
        this.sentRows = sentRows;
    }

    public int getFailRows() {
        return failRows;
    }

    public void setFailRows(int failRows) {
        this.failRows = failRows;
    }

    public int getReturnRows() {
        return returnRows;
    }

    public void setReturnRows(int returnRows) {
        this.returnRows = returnRows;
    }

    public List<ArriveItemResult> getResults() {
        return results;
    }

    public void setResults(List<ArriveItemResult> results) {
        this.results = results;
    }
}
