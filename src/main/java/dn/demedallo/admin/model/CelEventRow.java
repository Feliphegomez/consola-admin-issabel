package dn.demedallo.admin.model;

/**
 * One Channel Event Logging row ({@code asteriskcdrdb.cel}).
 */
public final class CelEventRow {

    public final String eventtype;
    public final String eventtime;
    public final String channame;
    public final String appname;
    public final String appdata;
    public final String extra;

    public CelEventRow(String eventtype, String eventtime, String channame,
            String appname, String appdata, String extra) {
        this.eventtype = eventtype == null ? "" : eventtype.trim();
        this.eventtime = eventtime == null ? "" : eventtime.trim();
        this.channame = channame == null ? "" : channame.trim();
        this.appname = appname == null ? "" : appname.trim();
        this.appdata = appdata == null ? "" : appdata.trim();
        this.extra = extra == null ? "" : extra.trim();
    }
}
