package com.example.apisystem.entity;

public class ColumnInfo {

    private String columnName;
    private String columnType;
    private String columnComment;
    private String isNullable;
    private String columnKey;
    private String columnDefault;
    private String extra;
    private Integer ordinalPosition;

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getColumnType() { return columnType; }
    public void setColumnType(String columnType) { this.columnType = columnType; }
    public String getColumnComment() { return columnComment; }
    public void setColumnComment(String columnComment) { this.columnComment = columnComment; }
    public String getIsNullable() { return isNullable; }
    public void setIsNullable(String isNullable) { this.isNullable = isNullable; }
    public String getColumnKey() { return columnKey; }
    public void setColumnKey(String columnKey) { this.columnKey = columnKey; }
    public String getColumnDefault() { return columnDefault; }
    public void setColumnDefault(String columnDefault) { this.columnDefault = columnDefault; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public Integer getOrdinalPosition() { return ordinalPosition; }
    public void setOrdinalPosition(Integer ordinalPosition) { this.ordinalPosition = ordinalPosition; }
}
