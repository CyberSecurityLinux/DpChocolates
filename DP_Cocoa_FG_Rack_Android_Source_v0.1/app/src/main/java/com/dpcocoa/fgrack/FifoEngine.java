package com.dpcocoa.fgrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free allocation logic: earliest manufacturing/batch date first, then oldest receipt. */
public final class FifoEngine {
    private FifoEngine() {}
    public static final class Lot {
        public final long rowId;
        public final String batch, rack, mfgDate, receivedAt, qa;
        public final int available;
        public Lot(long rowId, String batch, String rack, String mfgDate, String receivedAt, String qa, int available) {
            this.rowId=rowId; this.batch=batch; this.rack=rack; this.mfgDate=mfgDate;
            this.receivedAt=receivedAt; this.qa=qa; this.available=available;
        }
    }
    public static final class Pick {
        public final Lot lot;
        public final int quantity;
        public Pick(Lot lot, int quantity) { this.lot=lot; this.quantity=quantity; }
    }
    public static String fifoDate(Lot lot) {
        if (lot.mfgDate != null && lot.mfgDate.matches("\\d{4}-\\d{2}-\\d{2}")) return lot.mfgDate;
        Matcher m = Pattern.compile("(?i)[A-Z]?(\\d{2})(\\d{2})(\\d{2})").matcher(lot.batch == null ? "" : lot.batch);
        if (m.find()) {
            int yy=Integer.parseInt(m.group(1)), mm=Integer.parseInt(m.group(2)), dd=Integer.parseInt(m.group(3));
            if (mm>=1 && mm<=12 && dd>=1 && dd<=31) return String.format(java.util.Locale.US,"20%02d-%02d-%02d",yy,mm,dd);
        }
        if (lot.receivedAt != null && lot.receivedAt.length()>=10) return lot.receivedAt.substring(0,10);
        return "9999-12-31";
    }
    public static List<Pick> allocate(List<Lot> lots, int required) {
        if (required<=0) throw new IllegalArgumentException("Quantity must be positive");
        List<Lot> sorted=new ArrayList<>();
        for(Lot l:lots) if("RELEASE".equals(l.qa) && l.available>0) sorted.add(l);
        sorted.sort(Comparator.comparing(FifoEngine::fifoDate)
                .thenComparing(l -> l.receivedAt == null ? "" : l.receivedAt)
                .thenComparing(l -> l.batch).thenComparing(l -> l.rack));
        int left=required;
        List<Pick> picks=new ArrayList<>();
        for(Lot l:sorted) {
            int take=Math.min(left,l.available);
            if(take>0) { picks.add(new Pick(l,take)); left-=take; }
            if(left==0) break;
        }
        if(left>0) throw new IllegalArgumentException("Released stock short by "+left+" Bags/Boxes");
        return picks;
    }
    public static List<Lot> sortForDisplay(List<Lot> lots) {
        List<Lot> sorted=new ArrayList<>(lots);
        sorted.sort(Comparator.comparing(FifoEngine::fifoDate)
                .thenComparing(l -> l.receivedAt==null ? "" : l.receivedAt)
                .thenComparing(l -> l.batch).thenComparing(l -> l.rack));
        return sorted;
    }
}
