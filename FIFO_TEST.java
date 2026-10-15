package com.dpcocoa.fgrack;
import java.util.*;
public class FIFO_TEST {
    public static void main(String[] args) {
        FifoEngine.Lot older = new FifoEngine.Lot(1,"D260101000","A01","","2026-02-01 13:00:00","RELEASE",10);
        FifoEngine.Lot newer = new FifoEngine.Lot(2,"D260201000","A02","","2026-01-01 13:00:00","RELEASE",20);
        FifoEngine.Lot held = new FifoEngine.Lot(3,"D251231000","A03","","2025-12-31 13:00:00","HOLD",200);
        var picks=FifoEngine.allocate(Arrays.asList(newer,held,older),15);
        if(picks.size()!=2 || picks.get(0).lot.rowId!=1 || picks.get(0).quantity!=10 || picks.get(1).lot.rowId!=2 || picks.get(1).quantity!=5)throw new AssertionError("FIFO allocation incorrect");
        boolean threw=false;try{FifoEngine.allocate(Arrays.asList(newer,older,held),31);}catch(IllegalArgumentException e){threw=true;}
        if(!threw)throw new AssertionError("Should reject insufficient RELEASE stock");
        if(!"2026-09-03".equals(FifoEngine.fifoDate(new FifoEngine.Lot(4,"D2609030J0","A04","","2026-10-03 00:00:00","RELEASE",1))))throw new AssertionError("Batch date parser");
        System.out.println("FIFO TESTS PASSED");
    }
}
