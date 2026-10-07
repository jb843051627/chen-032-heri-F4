package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 提醒入册命令：一单连同多条提醒一起入册。提前量不在这里填——册上按注册时认的港里判据
 * 誊死（判据说几天就几天；那格空着/负数/说不上来的大数按一日）。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ApiModel(value = "ArriveRegisterCmd", description = "提醒入册命令")
public class ArriveRegisterCmd implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "单号(册号，一单一号；空着册上发号)")
    private String billNo;

    @ApiModelProperty(value = "船名航次")
    private String shipName;

    @ApiModelProperty(value = "注册时认的判据编号(提前量照它誊死)")
    private String ruleCode;

    @ApiModelProperty(value = "同单多条提醒(入册当候选，派发时只认一条发)")
    private List<ArriveItemInput> items;

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

    public List<ArriveItemInput> getItems() {
        return items;
    }

    public void setItems(List<ArriveItemInput> items) {
        this.items = items;
    }
}
