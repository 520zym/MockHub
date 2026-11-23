package com.mockhub.mock.model.dto;

/**
 * 接口导入结果
 */
public class ImportResult {
    private java.util.List<String> warnings=new java.util.ArrayList<String>();
    public java.util.List<String> getWarnings() { return warnings; }
    public void setWarnings(java.util.List<String> value) { warnings=value; }

    /** 新导入的接口数量 */
    private int imported;

    /** 跳过的接口数量（merge 模式下已存在的接口） */
    private int skipped;

    /** 覆盖的接口数量（override 模式下已存在的接口） */
    private int overridden;

    public ImportResult() {
    }

    public ImportResult(int imported, int skipped, int overridden) {
        this.imported = imported;
        this.skipped = skipped;
        this.overridden = overridden;
    }

    public int getImported() {
        return imported;
    }

    public void setImported(int imported) {
        this.imported = imported;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getOverridden() {
        return overridden;
    }

    public void setOverridden(int overridden) {
        this.overridden = overridden;
    }
}
