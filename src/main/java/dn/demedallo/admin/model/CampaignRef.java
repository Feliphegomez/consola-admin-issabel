package dn.demedallo.admin.model;

public final class CampaignRef {
    public String type = "";
    public int id;
    public String name = "";
    public String status = "";

    @Override
    public String toString() {
        String typeLabel = type == null || type.isBlank() ? "" : "(" + type + ") ";
        String nm = name == null || name.isBlank() ? ("#" + id) : name;
        String st = status == null || status.isBlank() ? "" : " · " + status;
        return typeLabel + nm + st;
    }
}
