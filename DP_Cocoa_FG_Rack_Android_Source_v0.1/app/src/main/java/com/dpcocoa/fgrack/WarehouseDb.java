package com.dpcocoa.fgrack;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WarehouseDb extends SQLiteOpenHelper {
    static final String DP="DP", MDLZ="MDLZ";
    public static final String[] ROLES={"ADMIN","MANAGER","OPERATOR","CHECKER","VIEWER"};
    private static final SecureRandom RNG=new SecureRandom();
    public WarehouseDb(Context ctx) { super(ctx,"dp_fg_rack_v1.db",null,1); }
    static String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()); }
    static String today() { return now().substring(0,10); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT UNIQUE NOT NULL,role TEXT NOT NULL,salt TEXT NOT NULL,hash TEXT NOT NULL,active INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE recovery(id INTEGER PRIMARY KEY CHECK(id=1),salt TEXT NOT NULL,hash TEXT NOT NULL)");
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY,org TEXT NOT NULL,name TEXT NOT NULL COLLATE NOCASE,uom TEXT NOT NULL CHECK(uom IN ('Bags','Boxes')),kg_per_unit REAL NOT NULL CHECK(kg_per_unit>0),UNIQUE(org,name))");
        db.execSQL("CREATE TABLE racks(code TEXT PRIMARY KEY,org TEXT NOT NULL,active INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE stock(id INTEGER PRIMARY KEY,org TEXT NOT NULL,product_id INTEGER NOT NULL REFERENCES products(id),batch TEXT NOT NULL,rack TEXT NOT NULL REFERENCES racks(code),qty INTEGER NOT NULL CHECK(qty>=0),qa TEXT NOT NULL CHECK(qa IN ('HOLD','RELEASE','REJECT')),mfg_date TEXT DEFAULT '',received_at TEXT NOT NULL,UNIQUE(org,product_id,batch,rack,qa))");
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY,at TEXT NOT NULL,actor TEXT NOT NULL,org TEXT NOT NULL,product_id INTEGER,product TEXT NOT NULL,batch TEXT NOT NULL,kind TEXT NOT NULL,from_rack TEXT NOT NULL,to_rack TEXT NOT NULL,qty INTEGER NOT NULL,unit_kg REAL NOT NULL,notes TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE counts(id INTEGER PRIMARY KEY,at TEXT NOT NULL,actor TEXT NOT NULL,stock_id INTEGER NOT NULL,org TEXT NOT NULL,product TEXT NOT NULL,batch TEXT NOT NULL,rack TEXT NOT NULL,system_qty INTEGER NOT NULL,physical_qty INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'PENDING',reviewer TEXT DEFAULT '',reviewed_at TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE audit(id INTEGER PRIMARY KEY,at TEXT NOT NULL,actor TEXT NOT NULL,action TEXT NOT NULL,detail TEXT NOT NULL)");
        db.execSQL("CREATE TABLE assignments(id INTEGER PRIMARY KEY,rack TEXT NOT NULL,assignee TEXT NOT NULL,due_date TEXT NOT NULL,status TEXT DEFAULT 'ASSIGNED',created_by TEXT NOT NULL,created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sync_outbox(id INTEGER PRIMARY KEY,at TEXT NOT NULL,event_type TEXT NOT NULL,payload TEXT NOT NULL,state TEXT DEFAULT 'PENDING')");
        db.execSQL("CREATE INDEX ix_stock_product ON stock(org,product_id,batch,qa)");
        db.execSQL("CREATE INDEX ix_stock_rack ON stock(rack)");
        db.execSQL("CREATE INDEX ix_movements_at ON movements(at)");
        // 1,372 predefined rack locations: A-K DP (924), L-P Mondelez (420), Q01-Q28 shared (28).
        for(char letter='A';letter<='Q';letter++) {
            int max=letter=='Q'?28:84;
            String org=letter<='K'?DP:(letter<='P'?MDLZ:"BOTH");
            for(int i=1;i<=max;i++) db.execSQL("INSERT INTO racks(code,org) VALUES(?,?)",new Object[]{String.format(Locale.US,"%c%02d",letter,i),org});
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) { throw new IllegalStateException("Migration required; never wipe stock data."); }
    static void require(boolean b,String error) { if(!b) throw new IllegalArgumentException(error); }
    static String clean(String text) { return text==null?"":text.trim(); }
    static int pos(String s) { try { int n=Integer.parseInt(s.trim()); require(n>0,"Quantity must be greater than zero"); return n; } catch(NumberFormatException e){throw new IllegalArgumentException("Enter a valid whole-number quantity");} }
    static String hex(byte[] bytes) { StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.US,"%02x",x&0xff));return b.toString(); }
    static byte[] fromHex(String h) {byte[] out=new byte[h.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);return out;}
    static String salt() { byte[] b=new byte[24];RNG.nextBytes(b);return hex(b); }
    static String hash(String password,String salt) {
        try {
            PBEKeySpec key=new PBEKeySpec(password.toCharArray(),fromHex(salt),160000,256);
            byte[] h=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded(); key.clearPassword(); return hex(h);
        } catch(Exception e) { throw new IllegalStateException("Cannot secure password",e); }
    }
    static boolean slowEq(String a,String b) {return java.security.MessageDigest.isEqual(fromHex(a),fromHex(b));}
    int scalar(String sql,String... args) { try(Cursor c=getReadableDatabase().rawQuery(sql,args)) {return c.moveToFirst()?c.getInt(0):0;} }
    boolean hasAdmin() {return scalar("SELECT count(*) FROM users WHERE role='ADMIN'")>0;}
    void initialize(String username,String password,String recovery) {
        require(!hasAdmin(),"Admin is already registered");
        require(username.length()>=3,"Username must have 3+ characters");
        require(password.length()>=10,"Password must have at least 10 characters");
        require(recovery.length()>=12,"Recovery code must have at least 12 characters");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            insertUser(db,username,password,"ADMIN");
            String s=salt(); db.execSQL("INSERT INTO recovery(id,salt,hash) VALUES(1,?,?)",new Object[]{s,hash(recovery,s)});
            log(db,username,"SETUP","Initial admin configured"); db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void insertUser(SQLiteDatabase db,String u,String pw,String role) {
        require(u.trim().length()>=3,"Username must have 3+ characters");
        require(pw.length()>=10,"Password must have 10+ characters");
        boolean valid=false;for(String r:ROLES)if(r.equals(role))valid=true;
        require(valid,"Invalid role");String s=salt();db.execSQL("INSERT INTO users(username,role,salt,hash) VALUES(?,?,?,?)",new Object[]{u.trim(),role,s,hash(pw,s)});
    }
    void createUser(String actor,String username,String password,String role) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {insertUser(db,username,password,role);log(db,actor,"USER_CREATE",username+" / "+role);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    String login(String user,String pw) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT role,salt,hash FROM users WHERE username=? AND active=1",new String[]{clean(user)})) {
            if(c.moveToFirst()&&slowEq(hash(pw,c.getString(1)),c.getString(2)))return c.getString(0);
        }return null;
    }
    void resetAdmin(String code,String newPw) {
        require(newPw.length()>=10,"New password must have at least 10 characters");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT salt,hash FROM recovery WHERE id=1",null)) {
            require(c.moveToFirst()&&slowEq(hash(code,c.getString(0)),c.getString(1)),"Invalid recovery code");
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            String s=salt();db.execSQL("UPDATE users SET salt=?,hash=? WHERE role='ADMIN'",new Object[]{s,hash(newPw,s)});
            log(db,"RECOVERY","ADMIN_RESET","Admin password recovered on this device");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void resetUser(String actor,String username,String newPassword) {
        require(newPassword.length()>=10,"Password must have 10+ characters");
        require(scalar("SELECT count(*) FROM users WHERE username=? AND role!='ADMIN'",username)>0,"Non-admin username not found");String s=salt();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            db.execSQL("UPDATE users SET hash=?,salt=? WHERE username=? AND role!='ADMIN'",new Object[]{hash(newPassword,s),s,username});
            log(db,actor,"PASSWORD_RESET","Reset "+username);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void deactivate(String actor,String username){require(!username.equals(actor),"Cannot disable self");SQLiteDatabase db=getWritableDatabase();db.execSQL("UPDATE users SET active=0 WHERE username=? AND role!='ADMIN'",new Object[]{username}); log(db,actor,"USER_DISABLE",username);}
    void log(SQLiteDatabase db,String actor,String action,String detail){db.execSQL("INSERT INTO audit(at,actor,action,detail) VALUES(?,?,?,?)",new Object[]{now(),actor,action,detail});}
    void outbox(SQLiteDatabase db,String kind,String text){db.execSQL("INSERT INTO sync_outbox(at,event_type,payload) VALUES(?,?,?)",new Object[]{now(),kind,text});}
    static String orgName(String org){return MDLZ.equals(org)?"Mondelez (SAP)":"DP Cocoa (Tally)";}
    void addProduct(String actor,String org,String product,String uom,double kg) {
        require(product.trim().length()>1,"Enter product name");require(kg>0,"Kg per Bag/Box must be positive");
        SQLiteDatabase db=getWritableDatabase();db.execSQL("INSERT INTO products(org,name,uom,kg_per_unit) VALUES(?,?,?,?)",new Object[]{org,clean(product),uom,kg});log(db,actor,"PRODUCT_ADD",org+" "+product);
    }
    void changeUom(String actor,long productId,String uom,double kg){require(kg>0,"Kg/unit must be positive");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
        db.execSQL("UPDATE products SET uom=?,kg_per_unit=? WHERE id=?",new Object[]{uom,kg,productId});log(db,actor,"PRODUCT_UOM_CHANGE",productId+" -> "+uom+" / "+kg+"kg");db.setTransactionSuccessful();
    }finally{db.endTransaction();}}
    void addRack(String actor,String code,String org) {
        code=clean(code).toUpperCase(Locale.US);require(code.matches("[A-Z0-9_-]{2,20}"),"Use rack code A01 / FLOOR-01");
        SQLiteDatabase db=getWritableDatabase();db.execSQL("INSERT INTO racks(code,org) VALUES(?,?)",new Object[]{code,org});log(db,actor,"RACK_ADD",code+" / "+org);
    }
    void disableRack(String actor,String rack) {
        require(scalar("SELECT count(*) FROM stock WHERE rack=? AND qty>0",rack)==0,"Rack has stock; transfer first");
        SQLiteDatabase db=getWritableDatabase();db.execSQL("UPDATE racks SET active=0 WHERE code=?",new Object[]{rack});log(db,actor,"RACK_DISABLE",rack);
    }
    static class Product { long id;String name,uom,org;double kg;Product(long i,String n,String u,double k,String o){id=i;name=n;uom=u;kg=k;org=o;} public String toString(){return name+" ("+uom+")";} }
    static class Stock {long id,productId;String org,product,batch,rack,qa,mfg,at,uom;int qty;double kg;
        String pretty(){return rack+" | "+product+" | "+batch+" | "+qty+" "+uom+" | "+qa+" | "+String.format(Locale.US,"%.1f Kg",qty*kg);}}
    static class Count {long id,stockId;String actor,org,product,batch,rack,at,status;int system,physical;}
    List<Product> products(String org){List<Product> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,uom,kg_per_unit,org FROM products WHERE org=? ORDER BY name",new String[]{org})){
        while(c.moveToNext())out.add(new Product(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4)));
    }return out;}
    Product getProduct(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,uom,kg_per_unit,org FROM products WHERE id=?",new String[]{""+id})) {if(c.moveToFirst())return new Product(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4));}throw new IllegalArgumentException("Product not found");}
    List<Stock> stocks(String org,String find){List<Stock> out=new ArrayList<>();String filter="%"+clean(find)+"%";
        String sql="SELECT s.id,s.product_id,s.org,p.name,s.batch,s.rack,s.qa,s.mfg_date,s.received_at,p.uom,s.qty,p.kg_per_unit FROM stock s JOIN products p ON p.id=s.product_id WHERE s.org=? AND s.qty>0 AND (p.name LIKE ? OR s.batch LIKE ? OR s.rack LIKE ?) ORDER BY p.name,s.batch,s.rack";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{org,filter,filter,filter})) {while(c.moveToNext()){
            Stock s=new Stock();s.id=c.getLong(0);s.productId=c.getLong(1);s.org=c.getString(2);s.product=c.getString(3);s.batch=c.getString(4);s.rack=c.getString(5);s.qa=c.getString(6);s.mfg=c.getString(7);s.at=c.getString(8);s.uom=c.getString(9);s.qty=c.getInt(10);s.kg=c.getDouble(11);out.add(s);
        }}return out;
    }
    List<String> racks(String org){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT code FROM racks WHERE active=1 AND (org=? OR org='BOTH') ORDER BY code",new String[]{org})){while(c.moveToNext())out.add(c.getString(0));}return out;}
    String suggestRack(String org,long productId,String batch) {
        // Consolidate same product + batch first, then use first vacant location in the same company's zone.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.rack FROM stock s JOIN racks r ON r.code=s.rack WHERE s.org=? AND s.product_id=? AND s.batch=? AND r.active=1 ORDER BY s.rack LIMIT 1",new String[]{org,""+productId,batch})) {if(c.moveToFirst())return c.getString(0);}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT r.code FROM racks r WHERE r.active=1 AND (r.org=? OR r.org='BOTH') AND NOT EXISTS (SELECT 1 FROM stock s WHERE s.rack=r.code AND s.qty>0) ORDER BY CASE WHEN r.org=? THEN 0 ELSE 1 END,r.code LIMIT 1",new String[]{org,org})) {if(c.moveToFirst())return c.getString(0);}
        return "NO VACANT RACK";
    }
    void requireRack(SQLiteDatabase db,String org,String rack,long productId,String batch){
        try(Cursor c=db.rawQuery("SELECT org,active FROM racks WHERE code=?",new String[]{rack})){
            require(c.moveToFirst()&&c.getInt(1)==1,"Rack does not exist or is disabled");String rackOrg=c.getString(0);
            require(rackOrg.equals(org)||rackOrg.equals("BOTH"),"Rack belongs to the other company");
        }
        try(Cursor c=db.rawQuery("SELECT count(*) FROM stock WHERE rack=? AND qty>0 AND (product_id!=? OR batch!=?)",new String[]{rack,""+productId,batch})){
            require(c.moveToFirst()&&c.getInt(0)==0,"Rack has a different product/batch. Select empty rack or transfer first.");
        }
    }
    void upsert(SQLiteDatabase db,String org,long productId,String batch,String rack,int qty,String qa,String mfg){
        String check="SELECT id FROM stock WHERE org=? AND product_id=? AND batch=? AND rack=? AND qa=?";
        try(Cursor c=db.rawQuery(check,new String[]{org,""+productId,batch,rack,qa})){
            if(c.moveToFirst())db.execSQL("UPDATE stock SET qty=qty+? WHERE id=?",new Object[]{qty,c.getLong(0)});
            else db.execSQL("INSERT INTO stock(org,product_id,batch,rack,qty,qa,mfg_date,received_at) VALUES(?,?,?,?,?,?,?,?)",new Object[]{org,productId,batch,rack,qty,qa,mfg,now()});
        }
    }
    void movement(SQLiteDatabase db,String actor,String org,Product p,String batch,String kind,String from,String to,int qty,String notes){
        db.execSQL("INSERT INTO movements(at,actor,org,product_id,product,batch,kind,from_rack,to_rack,qty,unit_kg,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",new Object[]{now(),actor,org,p.id,p.name,batch,kind,from,to,qty,p.kg,notes});
        outbox(db,kind,org+"|"+p.name+"|"+batch+"|"+qty+"|"+from+"|"+to);
    }
    void receive(String actor,String org,long productId,String batch,String rack,int qty,String mfg,String qa,String notes){
        require(qty>0,"Quantity must be positive"); require(clean(batch).length()>0,"Enter batch");require("HOLD".equals(qa)||"RELEASE".equals(qa)||"REJECT".equals(qa),"Invalid QA status");
        Product p=getProduct(productId);require(org.equals(p.org),"Product belongs to another company");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            rack=rack.trim().toUpperCase(Locale.US);requireRack(db,org,rack,productId,batch.trim());
            upsert(db,org,productId,batch.trim(),rack,qty,qa,clean(mfg));
            movement(db,actor,org,p,batch.trim(),"RECEIVE","GATE",rack,qty,notes);
            log(db,actor,"PUTAWAY",p.name+" / "+batch+" / "+rack+" / "+qty);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void receiveMany(String actor,String org,long productId,String batch,java.util.Map<String,Integer> locations,String mfg,String qa,String notes) {
        require(!locations.isEmpty(),"Please enter at least one Rack=Quantity");
        require(clean(batch).length()>0,"Enter batch");
        require("HOLD".equals(qa)||"RELEASE".equals(qa)||"REJECT".equals(qa),"Invalid QA status");
        Product product=getProduct(productId);require(org.equals(product.org),"Company mismatch");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            for(java.util.Map.Entry<String,Integer> e:locations.entrySet()) {
                String rack=e.getKey().trim().toUpperCase(Locale.US);int qty=e.getValue();require(qty>0,"Quantity must be positive");
                requireRack(db,org,rack,productId,batch.trim());
                upsert(db,org,productId,batch.trim(),rack,qty,qa,clean(mfg));
                movement(db,actor,org,product,batch.trim(),"RECEIVE","GATE",rack,qty,notes);
            }
            log(db,actor,"MULTI_RACK_PUTAWAY",product.name+" / "+batch+" / "+locations.size()+" racks");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void qa(String actor,String org,String product,String batch,String target) {
        require(target.equals("HOLD")||target.equals("RELEASE")||target.equals("REJECT"),"Invalid QA status");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            List<Stock> rows=stocks(org,product);int changed=0;
            for(Stock s:rows)if(s.product.equalsIgnoreCase(product)&&s.batch.equals(batch)&&!s.qa.equals(target)){
                db.execSQL("UPDATE stock SET qty=0 WHERE id=?",new Object[]{s.id});
                upsert(db,org,s.productId,batch,s.rack,s.qty,target,s.mfg);changed++;
            }
            require(changed>0,"No batches to update (or status is already the same)");
            log(db,actor,"QA_"+target,org+" "+product+" "+batch+" / "+changed+" rows");outbox(db,"QA_CHANGE",org+"|"+product+"|"+batch+"|"+target);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    List<FifoEngine.Lot> fifoLots(String org,long productId) {
        List<FifoEngine.Lot> out=new ArrayList<>();String sql="SELECT id,batch,rack,mfg_date,received_at,qa,qty FROM stock WHERE org=? AND product_id=? AND qty>0";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{org,""+productId})){
            while(c.moveToNext())out.add(new FifoEngine.Lot(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getInt(6)));
        }return FifoEngine.sortForDisplay(out);
    }
    List<FifoEngine.Pick> dispatch(String actor,String org,long productId,int qty,String destination,String invoice) {
        Product p=getProduct(productId);require(p.org.equals(org),"Company mismatch");require(!clean(destination).isEmpty(),"Enter dispatch destination");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            List<FifoEngine.Pick> picks=FifoEngine.allocate(fifoLots(org,productId),qty);
            for(FifoEngine.Pick pick:picks){
                db.execSQL("UPDATE stock SET qty=qty-? WHERE id=? AND qty>=?",new Object[]{pick.quantity,pick.lot.rowId,pick.quantity});
                movement(db,actor,org,p,pick.lot.batch,"DISPATCH",pick.lot.rack,destination,pick.quantity,"Invoice: "+invoice);
            }
            log(db,actor,"DISPATCH",p.name+" / "+qty+" / "+destination);db.setTransactionSuccessful();return picks;
        }finally{db.endTransaction();}
    }
    void transfer(String actor,long stockId,String to,int qty,String reason){require(qty>0,"Quantity must be positive");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
        Stock s=getStock(stockId);require(qty<=s.qty,"Not enough stock in source rack");to=to.trim().toUpperCase(Locale.US);
        require(!to.equals(s.rack),"Source and destination racks are same");
        requireRack(db,s.org,to,s.productId,s.batch);
        db.execSQL("UPDATE stock SET qty=qty-? WHERE id=? AND qty>=?",new Object[]{qty,stockId,qty});
        upsert(db,s.org,s.productId,s.batch,to,qty,s.qa,s.mfg);
        movement(db,actor,s.org,getProduct(s.productId),s.batch,"TRANSFER",s.rack,to,qty,reason);
        log(db,actor,"TRANSFER",s.rack+" -> "+to+" / "+qty);db.setTransactionSuccessful();
    }finally{db.endTransaction();}}
    Stock getStock(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT s.org FROM stock s WHERE s.id=?",new String[]{""+id})){require(c.moveToFirst(),"Stock row not found");String org=c.getString(0);for(Stock s:stocks(org,""))if(s.id==id)return s;}throw new IllegalArgumentException("Stock row empty");}
    void count(String actor,long stockId,int physical){require(physical>=0,"Physical quantity cannot be negative");Stock s=getStock(stockId);
        require(scalar("SELECT count(*) FROM counts WHERE stock_id=? AND status='PENDING'",""+stockId)==0,"This stock row has pending verification");
        SQLiteDatabase db=getWritableDatabase();db.execSQL("INSERT INTO counts(at,actor,stock_id,org,product,batch,rack,system_qty,physical_qty) VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{now(),actor,stockId,s.org,s.product,s.batch,s.rack,s.qty,physical});log(db,actor,"CYCLE_COUNT","Rack "+s.rack+" count "+physical);
    }
    List<Count> pendingCounts(){List<Count> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,stock_id,actor,org,product,batch,rack,at,system_qty,physical_qty,status FROM counts WHERE status='PENDING' ORDER BY at",null)){
        while(c.moveToNext()){Count n=new Count();n.id=c.getLong(0);n.stockId=c.getLong(1);n.actor=c.getString(2);n.org=c.getString(3);n.product=c.getString(4);n.batch=c.getString(5);n.rack=c.getString(6);n.at=c.getString(7);n.system=c.getInt(8);n.physical=c.getInt(9);n.status=c.getString(10);rows.add(n);}
    }return rows;}
    void reviewCount(String actor,long countId,boolean approve){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
        Count picked=null;for(Count x:pendingCounts())if(x.id==countId)picked=x;
        require(picked!=null,"Count already reviewed");
        if(approve){Stock current=getStock(picked.stockId);require(current.qty==picked.system,"Stock changed since count; recount required");
            int delta=picked.physical-picked.system;
            if(delta!=0){db.execSQL("UPDATE stock SET qty=? WHERE id=?",new Object[]{picked.physical,picked.stockId});
                movement(db,actor,current.org,getProduct(current.productId),current.batch,"ADJUSTMENT",current.rack,current.rack,delta,"Approved physical count #"+countId);
            }
        }
        db.execSQL("UPDATE counts SET status=?,reviewer=?,reviewed_at=? WHERE id=?",new Object[]{approve?"APPROVED":"REJECTED",actor,now(),countId});
        log(db,actor,approve?"COUNT_APPROVED":"COUNT_REJECTED","Count #"+countId);db.setTransactionSuccessful();
    }finally{db.endTransaction();}}
    int total(String org){return scalar("SELECT COALESCE(sum(qty),0) FROM stock WHERE org=?",org);}
    int monthMovement(String org,String kind){String ym=today().substring(0,7);return scalar("SELECT COALESCE(sum(qty),0) FROM movements WHERE org=? AND kind=? AND substr(at,1,7)=?",org,kind,ym);}
    int todayMovement(String org,String kind){return scalar("SELECT COALESCE(sum(qty),0) FROM movements WHERE org=? AND kind=? AND substr(at,1,10)=?",org,kind,today());}
    int pendingOutbox(){return scalar("SELECT count(*) FROM sync_outbox WHERE state='PENDING'");}
    int activeBatches(String org){return scalar("SELECT count(DISTINCT product_id || ':' || batch) FROM stock WHERE org=? AND qty>0",org);}
    int qaHold(String org){return scalar("SELECT COALESCE(sum(qty),0) FROM stock WHERE org=? AND qa!='RELEASE'",org);}

    Cursor rackReport(String org,String rack){
        return getReadableDatabase().rawQuery("SELECT s.rack AS Rack,p.name AS Product,s.batch AS Batch,s.qty AS Quantity,p.uom AS UOM,ROUND(s.qty*p.kg_per_unit,2) AS Kg,s.qa AS QA FROM stock s JOIN products p ON s.product_id=p.id WHERE s.org=? AND s.rack=? AND s.qty>0 ORDER BY p.name,s.batch",new String[]{org,rack.trim().toUpperCase(Locale.US)});
    }
    void assignRack(String actor,String org,String rack,String assignee,String due){
        require(due.matches("\\d{4}-\\d{2}-\\d{2}"),"Due date must be YYYY-MM-DD");
        require(racks(org).contains(rack),"Select valid company rack");
        require(scalar("SELECT count(*) FROM assignments WHERE rack=? AND status='ASSIGNED'",rack)==0,"Rack already assigned for cycle count");
        require(scalar("SELECT count(*) FROM users WHERE username=? AND active=1",assignee)>0,"Username not found");
        SQLiteDatabase db=getWritableDatabase();db.execSQL("INSERT INTO assignments(rack,assignee,due_date,created_by,created_at) VALUES(?,?,?,?,?)",new Object[]{rack,assignee,due,actor,now()});
        log(db,actor,"COUNT_ASSIGN",rack+" -> "+assignee+" / "+due);
    }
    Cursor assignedRacks(String username,boolean all){
        return getReadableDatabase().rawQuery(all?"SELECT id,rack,assignee,due_date,status FROM assignments ORDER BY status,due_date":"SELECT id,rack,assignee,due_date,status FROM assignments WHERE assignee=? ORDER BY status,due_date",all?null:new String[]{username});
    }
    void completeAssignment(String actor,long id){
        SQLiteDatabase db=getWritableDatabase();db.execSQL("UPDATE assignments SET status='COMPLETED' WHERE id=? AND assignee=?",new Object[]{id,actor});log(db,actor,"COUNT_ASSIGN_DONE","Assignment "+id);
    }
    Cursor report(String key,String org) {
        SQLiteDatabase db=getReadableDatabase();
        switch(key) {
            case "Stock":return db.rawQuery("SELECT ? AS Company,p.name AS Product,s.batch AS Batch,s.rack AS Rack,s.qty AS Units,p.uom AS UOM,ROUND(s.qty*p.kg_per_unit,2) AS Kg,s.qa AS QA,s.mfg_date AS Mfg_Date,s.received_at AS Received FROM stock s JOIN products p ON p.id=s.product_id WHERE s.org=? AND s.qty>0 ORDER BY p.name,s.batch,s.rack",new String[]{orgName(org),org});
            case "Product Summary":return db.rawQuery("SELECT p.name AS Product,p.uom AS UOM,SUM(s.qty) AS Units,ROUND(SUM(s.qty*p.kg_per_unit),2) AS Kg,COUNT(DISTINCT s.batch) AS Batches FROM stock s JOIN products p ON p.id=s.product_id WHERE s.org=? AND s.qty>0 GROUP BY s.product_id ORDER BY p.name",new String[]{org});
            case "Batch Summary":return db.rawQuery("SELECT p.name AS Product,s.batch AS Batch,p.uom AS UOM,SUM(s.qty) AS Units,ROUND(SUM(s.qty*p.kg_per_unit),2) AS Kg,s.qa AS QA,group_concat(DISTINCT s.rack) AS Racks FROM stock s JOIN products p ON p.id=s.product_id WHERE s.org=? AND s.qty>0 GROUP BY s.product_id,s.batch,s.qa ORDER BY p.name,s.batch",new String[]{org});
            case "Dispatch":return db.rawQuery("SELECT at AS Date,product AS Product,batch AS Batch,from_rack AS Rack,to_rack AS Destination,qty AS Units,ROUND(qty*unit_kg,2) AS Kg,notes AS Invoice,actor AS User FROM movements WHERE org=? AND kind='DISPATCH' ORDER BY at DESC",new String[]{org});
            case "Putaway":return db.rawQuery("SELECT at AS Date,product AS Product,batch AS Batch,to_rack AS Rack,qty AS Units,ROUND(qty*unit_kg,2) AS Kg,actor AS User,notes AS Notes FROM movements WHERE org=? AND kind='RECEIVE' ORDER BY at DESC",new String[]{org});
            case "Today Opening":return db.rawQuery("SELECT p.name AS Product,p.uom AS UOM,COALESCE(sum(s.qty),0)-COALESCE((SELECT SUM(CASE WHEN m.kind='RECEIVE' THEN m.qty WHEN m.kind='DISPATCH' THEN -m.qty WHEN m.kind='ADJUSTMENT' THEN m.qty ELSE 0 END) FROM movements m WHERE m.product_id=p.id AND substr(m.at,1,10)=?),0) AS Opening_Units FROM products p LEFT JOIN stock s ON s.product_id=p.id WHERE p.org=? GROUP BY p.id ORDER BY p.name",new String[]{today(),org});
            case "Aging":return db.rawQuery("SELECT p.name AS Product,s.batch AS Batch,s.rack AS Rack,s.qty AS Units,p.uom AS UOM,CAST(julianday('now')-julianday(CASE WHEN length(s.mfg_date)=10 THEN s.mfg_date ELSE substr(s.received_at,1,10) END) AS INTEGER) AS Age_Days,s.qa AS QA FROM stock s JOIN products p ON p.id=s.product_id WHERE s.org=? AND s.qty>0 ORDER BY Age_Days DESC",new String[]{org});
            case "Audit":return db.rawQuery("SELECT at AS Date,actor AS User,action AS Action,detail AS Details FROM audit ORDER BY at DESC LIMIT 2000",null);
            case "Cycle Counts":return db.rawQuery("SELECT at AS Date,actor AS Counter,org AS Company,product AS Product,batch AS Batch,rack AS Rack,system_qty AS System_Units,physical_qty AS Physical_Units,physical_qty-system_qty AS Variance,status AS Status,reviewer AS Reviewer FROM counts WHERE org=? ORDER BY at DESC",new String[]{org});
            default: throw new IllegalArgumentException("Unknown report");
        }
    }
}
