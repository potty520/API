package com.example.apisystem.entity;

public class TableInfo {

    private String tableName;
    private String tableComment;
    private String engine;
    private Long dataLength;
    private Long indexLength;
    private Long rowCount;
    private String createTime;
    private String updateTime;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public Long getDataLength() { return dataLength; }
    public void setDataLength(Long dataLength) { this.dataLength = dataLength; }
    public Long getIndexLength() { return indexLength; }
    public void setIndexLength(Long indexLength) { this.indexLength = indexLength; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
