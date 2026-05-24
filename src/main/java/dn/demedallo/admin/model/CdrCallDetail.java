package dn.demedallo.admin.model;

/**
 * Full CDR row for trace reconstruction ({@code asteriskcdrdb.cdr}).
 */
public final class CdrCallDetail {

    public final String uniqueid;
    public final String calldate;
    public final String clid;
    public final String src;
    public final String dst;
    public final String dcontext;
    public final String channel;
    public final String dstchannel;
    public final String lastapp;
    public final String lastdata;
    public final int duration;
    public final int billsec;
    public final String disposition;
    public final String recordingfile;

    public CdrCallDetail(String uniqueid, String calldate, String clid, String src, String dst,
            String dcontext, String channel, String dstchannel, String lastapp, String lastdata,
            int duration, int billsec, String disposition, String recordingfile) {
        this.uniqueid = uniqueid == null ? "" : uniqueid;
        this.calldate = calldate == null ? "" : calldate;
        this.clid = clid == null ? "" : clid;
        this.src = src == null ? "" : src;
        this.dst = dst == null ? "" : dst;
        this.dcontext = dcontext == null ? "" : dcontext;
        this.channel = channel == null ? "" : channel;
        this.dstchannel = dstchannel == null ? "" : dstchannel;
        this.lastapp = lastapp == null ? "" : lastapp;
        this.lastdata = lastdata == null ? "" : lastdata;
        this.duration = duration;
        this.billsec = billsec;
        this.disposition = disposition == null ? "" : disposition;
        this.recordingfile = recordingfile == null ? "" : recordingfile;
    }
}
