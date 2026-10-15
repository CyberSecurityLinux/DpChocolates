package com.dpcocoa.fgrack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** First native Android offline release. No WebView, no third-party packages. */
public class MainActivity extends Activity {
    private WarehouseDb db;
    private LinearLayout page;
    private String user="",role="",org=WarehouseDb.DP;
    private byte[] pendingExport;
    private String pendingName="";
    private static final int BROWN=Color.rgb(80,48,32),CREAM=Color.rgb(249,246,241),GOLD=Color.rgb(191,137,67);
    private static final String[] REPORTS={"Stock","Product Summary","Batch Summary","Today Opening","Putaway","Dispatch","Aging","Cycle Counts","Audit"};
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BROWN);db=new WarehouseDb(this);if(db.hasAdmin())loginScreen();else setupScreen();}
    int dip(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    GradientDrawable shape(int bg,int border){GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dip(12));if(border!=0)d.setStroke(dip(1),border);return d;}
    void start(String title,String subtitle,boolean home){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(CREAM);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dip(17),dip(20),dip(17),dip(30));
        sv.addView(page);setContentView(sv);
        TextView brand=text("DP COCOA  •  FG RACK",14,true,BROWN);brand.setLetterSpacing(.09f);page.addView(brand);
        TextView heading=text(title,25,true,BROWN);heading.setPadding(0,dip(14),0,dip(4));page.addView(heading);
        if(!subtitle.isEmpty())page.addView(text(subtitle,13,false,Color.DKGRAY));
        if(home && !user.isEmpty()){
            Button back=button("← Dashboard",()->home());back.setBackground(shape(0xffede3d6,0));back.setTextColor(BROWN);page.addView(back);
            page.addView(text("Signed in: "+user+" ("+role+")",12,false,Color.GRAY));
        }
    }
    TextView text(String v,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(v);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dip(3),0,dip(3));return t;}
    void section(String title){TextView t=text(title,17,true,BROWN);t.setPadding(0,dip(17),0,dip(6));page.addView(t);}
    EditText input(String hint,String preset,boolean secret){EditText e=new EditText(this);e.setTextSize(15);e.setSingleLine(true);e.setHint(hint);e.setText(preset);e.setPadding(dip(12),dip(12),dip(12),dip(12));e.setBackground(shape(Color.WHITE,0xffddcfbf));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dip(5),0,dip(7));page.addView(e,lp);if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    Button button(String label,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setTextSize(14);b.setText(label);b.setTextColor(Color.WHITE);b.setBackground(shape(BROWN,0));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dip(49));lp.setMargins(0,dip(6),0,dip(6));b.setLayoutParams(lp);b.setOnClickListener(v->safe(action));return b;}
    void action(String label,Runnable task){page.addView(button(label,task));}
    Spinner spinner(List<String> labels){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);s.setAdapter(a);s.setBackground(shape(Color.WHITE,0xffddcfbf));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dip(52));lp.setMargins(0,dip(5),0,dip(8));page.addView(s,lp);return s;}
    Spinner spinner(String[] labels){List<String> list=new ArrayList<>();for(String v:labels)list.add(v);return spinner(list);}
    String select(Spinner s){return s.getSelectedItem()==null?"":s.getSelectedItem().toString();}
    void hint(String msg){TextView v=text(msg,13,false,0xff66564a);v.setPadding(dip(10),dip(10),dip(10),dip(10));v.setBackground(shape(0xffefe6da,0));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dip(8),0,dip(8));page.addView(v,lp);}
    void info(String msg){TextView v=text(msg,14,false,0xff292929);v.setPadding(dip(12),dip(10),dip(12),dip(10));v.setBackground(shape(Color.WHITE,0xffe7dcca));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dip(3),0,dip(3));page.addView(v,lp);}
    void notice(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    void safe(Runnable task){try{task.run();}catch(Exception ex){new AlertDialog.Builder(this).setTitle("Please check").setMessage(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()).setPositiveButton("OK",null).show();}}
    boolean is(String... roles){for(String r:roles)if(role.equals(r))return true;return false;}
    void guard(String... roles){if(!is(roles))throw new IllegalArgumentException("Your "+role+" role cannot change this data");}
    void confirm(String title,String message,Runnable action){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w)->safe(action)).show();}
    String orgTitle(){return WarehouseDb.orgName(org);}
    void setupScreen(){start("First-time Admin Setup","Device-only registration • set your own private recovery code",false);
        hint("This is a native offline app. Keep your admin password and recovery code safe. Do not use a shared or publicly posted master password.");
        EditText name=input("Admin username","admin",false),pw=input("Strong admin password (10+ chars)","",true),code=input("Private recovery code (12+ chars)","",true);
        action("Create Admin & Open App",()->{db.initialize(name.getText().toString(),pw.getText().toString(),code.getText().toString());notice("Admin created");loginScreen();});
    }
    void loginScreen(){user="";role="";start("Warehouse Login","Independent app login • No ChatGPT account required",false);
        EditText u=input("Username","",false),p=input("Password","",true);
        action("Log In",()->{String result=db.login(u.getText().toString(),p.getText().toString());if(result==null)throw new IllegalArgumentException("Incorrect username or password");user=u.getText().toString().trim();role=result;home();});
        action("Forgot Admin Password",this::recoveryScreen);
    }
    void recoveryScreen(){start("Admin Password Recovery","Recover this device's local admin account",false);
        hint("Enter the private recovery code created during first setup. For other users, the Admin can reset passwords in User Management.");
        EditText code=input("Your private recovery code","",true),newPass=input("New admin password (10+ chars)","",true);
        action("Reset Admin Password",()->{db.resetAdmin(code.getText().toString(),newPass.getText().toString());notice("Password changed");loginScreen();});
        action("Back to Login",this::loginScreen);
    }
    void home(){start("FG Warehouse Dashboard",orgTitle()+" • Offline-first local data",false);
        section("Company / ERP");Spinner companies=spinner(new String[]{"DP Cocoa (Tally)","Mondelez (SAP)"});companies.setSelection(org.equals(WarehouseDb.DP)?0:1);
        action("Switch Company",()->{org=companies.getSelectedItemPosition()==0?WarehouseDb.DP:WarehouseDb.MDLZ;home();});
        section("Warehouse Snapshot");
        int opening=db.total(org)-db.todayMovement(org,"RECEIVE")+db.todayMovement(org,"DISPATCH")-db.todayMovement(org,"ADJUSTMENT");
        info("CURRENT STOCK      "+db.total(org)+" Bags / Boxes\nTODAY OPENING      "+opening+" Units\nBATCHES                 "+db.activeBatches(org)+"\nQA HOLD/REJECT   "+db.qaHold(org)+" Units");
        info("THIS MONTH  •  Putaway "+db.monthMovement(org,"RECEIVE")+" Units  |  Dispatch "+db.monthMovement(org,"DISPATCH")+" Units");
        hint("LOCAL OFFLINE MODE  •  "+db.pendingOutbox()+" changes queued for future server sync. Multi-mobile synchronization is NOT active until a secure backend is connected.");
        section("Operations");
        action("📦 Stock & Rack Search",this::stockScreen);
        if(is("ADMIN","MANAGER","OPERATOR")){
            action("↧ Putaway / Receiving",this::receiveScreen);
            action("⇢ FIFO Dispatch Picking",this::dispatchScreen);
            action("⇄ Rack Transfer / Re-slot Audit Stock",this::transferScreen);
        }
        if(is("ADMIN","MANAGER"))action("✓ QA Release / Hold / Reject",this::qaScreen);
        if(is("ADMIN","MANAGER","CHECKER","OPERATOR"))action("☑ Physical Count / Cycle Count",this::countScreen);
        if(is("ADMIN","MANAGER")){
            action("☑ Review Physical Count Corrections",this::reviewScreen);
            action("◫ Assign Rack Cycle Counts",this::assignmentScreen);
        }
        action("▤ Excel / PDF / Rack Reports",this::reportsScreen);
        if(is("ADMIN"))action("⚙ Admin Settings",this::adminScreen);
        action("Log Out",this::loginScreen);
    }
    void productHelp(List<WarehouseDb.Product> products){if(products.isEmpty())hint("No products created in this company. Admin → Products → Add Product first.");}
    WarehouseDb.Product chosen(List<WarehouseDb.Product> products,Spinner spinner){int idx=spinner.getSelectedItemPosition();if(idx<0||idx>=products.size())throw new IllegalArgumentException("Add/select a product first");return products.get(idx);}
    Spinner productSpinner(List<WarehouseDb.Product> products){List<String> labels=new ArrayList<>();for(WarehouseDb.Product p:products)labels.add(p.name+" | "+p.uom+" | "+p.kg+"kg");return spinner(labels);}
    void stockScreen(){stockScreen("");}
    void stockScreen(String filter){start("Stock & Rack Search",orgTitle(),true);
        EditText find=input("Search product / batch / rack",filter,false);
        action("Search Current Stock",()->stockScreen(find.getText().toString()));
        List<WarehouseDb.Stock> rows=db.stocks(org,filter);
        hint("Rows: "+rows.size()+" • Shows Product, Batch, Rack, Quantity, UOM, Kg and Quality Status.");
        int count=0;for(WarehouseDb.Stock s:rows){if(count++>=200)break;info(s.pretty());}
        if(rows.size()>200)hint("Showing first 200 lines. Narrow your search or export complete Stock Report.");
        if(rows.isEmpty())hint("No stock found. Use Putaway or import actual records after setting up products.");
    }
    void receiveScreen(){start("Putaway / Inward",orgTitle()+" • Recommend nearby matching batch racks",true);
        List<WarehouseDb.Product> products=db.products(org);productHelp(products);section("Material");Spinner p=productSpinner(products);
        EditText batch=input("Batch number (e.g. D2609030J0)","",false),mfg=input("Manufacturing date YYYY-MM-DD (optional)","",false),rack=input("Rack code (A01 / L01 / FLOOR-01)","",false),qty=input("Quantity in Bags / Boxes","",false);
        section("Quality on receipt");Spinner qa=spinner(new String[]{"HOLD","RELEASE","REJECT"});
        EditText notes=input("FGTN / GRN / remarks (optional)","",false);
        action("Suggest Rack for Product + Batch",()->{WarehouseDb.Product x=chosen(products,p);rack.setText(db.suggestRack(org,x.id,batch.getText().toString().trim()));});
        hint("Default is QA HOLD. Only RELEASE stock can be dispatched. Rack must be vacant or contain the same product and batch.");
        action("Save Putaway (Offline)",()->{guard("ADMIN","MANAGER","OPERATOR");WarehouseDb.Product x=chosen(products,p);
            String location=rack.getText().toString().trim();if(location.equals("NO VACANT RACK"))throw new IllegalArgumentException("No vacant rack. Admin must create a new rack.");
            db.receive(user,org,x.id,batch.getText().toString(),location,WarehouseDb.pos(qty.getText().toString()),mfg.getText().toString(),select(qa),notes.getText().toString());notice("Saved locally with audit trail");receiveScreen();});
        section("Multiple Racks • Same Product + Batch");
        EditText multi=input("Example: A01=20, A02=35, A03=10","",false);
        action("Save Multi-Rack Putaway Together",()->{
            guard("ADMIN","MANAGER","OPERATOR");WarehouseDb.Product x=chosen(products,p);
            java.util.Map<String,Integer> slots=new java.util.LinkedHashMap<>();
            for(String token:multi.getText().toString().split(",")){
                String[] pair=token.trim().split("=");
                if(pair.length!=2)throw new IllegalArgumentException("Use Rack=Quantity separated by commas");
                String code=pair[0].trim().toUpperCase(Locale.US);
                if(slots.containsKey(code))throw new IllegalArgumentException("Duplicate rack: "+code);
                slots.put(code,WarehouseDb.pos(pair[1]));
            }
            db.receiveMany(user,org,x.id,batch.getText().toString(),slots,mfg.getText().toString(),select(qa),notes.getText().toString());
            notice("All "+slots.size()+" racks saved in one transaction");receiveScreen();
        });
    }
    void dispatchScreen(){start("FIFO Dispatch Picking",orgTitle()+" • QA RELEASE only",true);
        List<WarehouseDb.Product> products=db.products(org);productHelp(products);section("Search Product");
        AutoCompleteTextView search=new AutoCompleteTextView(this);search.setSingleLine(true);search.setThreshold(1);search.setHint("Type product name and select");
        List<String> labels=new ArrayList<>();for(WarehouseDb.Product p:products)labels.add(p.name);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,labels);search.setAdapter(adapter);
        search.setBackground(shape(Color.WHITE,0xffddcfbf));search.setPadding(dip(14),dip(13),dip(14),dip(13));page.addView(search);
        EditText qty=input("Dispatch quantity (Bags/Boxes)","",false),destination=input("Destination / customer / plant","",false),invoice=input("Invoice / E-Way Bill / LR ref (optional)","",false);
        final WarehouseDb.Product[] selected={null};
        search.setOnItemClickListener((parent,v,pos,id)->{String name=parent.getItemAtPosition(pos).toString();for(WarehouseDb.Product pr:products)if(pr.name.equalsIgnoreCase(name))selected[0]=pr;});
        action("Show FIFO Batch → Rack Suggestions",()->{
            WarehouseDb.Product p=lookupProduct(products,search.getText().toString());selected[0]=p;
            List<FifoEngine.Lot> rows=db.fifoLots(org,p.id);
            StringBuilder b=new StringBuilder("Product: "+p.name+"  |  "+p.uom+"\n\n");int total=0;
            for(FifoEngine.Lot l:rows){b.append(FifoEngine.fifoDate(l)).append("  •  ").append(l.batch).append("  •  Rack ").append(l.rack).append("  •  ").append(l.available).append(" ").append(l.qa).append("\n");if(l.qa.equals("RELEASE"))total+=l.available;}
            b.append("\nTOTAL QA-RELEASED: ").append(total).append(" ").append(p.uom);
            new AlertDialog.Builder(this).setTitle("FIFO Picks | Oldest First").setMessage(b.toString()).setPositiveButton("OK",null).show();
        });
        hint("FIFO uses YYYY-MM-DD manufacturing date; otherwise DYYMMDD batch date; otherwise earliest receipt. Picks remove stock only on confirmed dispatch.");
        action("Confirm FIFO Dispatch",()->{guard("ADMIN","MANAGER","OPERATOR");WarehouseDb.Product p=lookupProduct(products,search.getText().toString());int n=WarehouseDb.pos(qty.getText().toString());
            List<FifoEngine.Lot> l=db.fifoLots(org,p.id);List<FifoEngine.Pick> expected=FifoEngine.allocate(l,n);
            StringBuilder info=new StringBuilder("Dispatch "+n+" "+p.uom+" from:\n");for(FifoEngine.Pick pick:expected)info.append("• ").append(pick.lot.batch).append(" / ").append(pick.lot.rack).append(" / ").append(pick.quantity).append("\n");
            confirm("Dispatch & Reduce Stock",info.toString(),()->{
                db.dispatch(user,org,p.id,n,destination.getText().toString(),invoice.getText().toString());notice("Dispatched / stock reduced with FIFO audit");dispatchScreen();
            });
        });
    }
    WarehouseDb.Product lookupProduct(List<WarehouseDb.Product> products,String term){for(WarehouseDb.Product p:products)if(p.name.equalsIgnoreCase(term.trim()))return p;throw new IllegalArgumentException("Select an existing product name from suggestions");}
    void transferScreen(){start("Rack Transfer / Audit Re-slot",orgTitle(),true);
        List<WarehouseDb.Stock> rows=db.stocks(org,"");section("Select occupied rack / batch");Spinner from=stockSpinner(rows);
        EditText target=input("Move to Rack / Floor location", "", false),qty=input("Move quantity (Bags/Boxes)","",false),reason=input("Reason (audit / consolidation / reach truck)","",false);
        action("Suggest Suitable Rack",()->{WarehouseDb.Stock s=chosenStock(rows,from);String v=db.suggestRack(org,s.productId,s.batch);target.setText(v);});
        hint("For audit space, Admin can create a FLOOR-01 or TEMP-01 location. Transfer stock back to its allocated product/batch rack later; all moves remain in history.");
        action("Confirm Transfer",()->{guard("ADMIN","MANAGER","OPERATOR");WarehouseDb.Stock s=chosenStock(rows,from);
            db.transfer(user,s.id,target.getText().toString(),WarehouseDb.pos(qty.getText().toString()),reason.getText().toString());notice("Rack transfer saved");transferScreen();});
    }
    Spinner stockSpinner(List<WarehouseDb.Stock> rows){List<String> names=new ArrayList<>();for(WarehouseDb.Stock s:rows)names.add(s.rack+" • "+s.product+" • "+s.batch+" • "+s.qty+" "+s.uom);if(names.isEmpty())names.add("No stock available");return spinner(names);}
    WarehouseDb.Stock chosenStock(List<WarehouseDb.Stock> rows,Spinner s){int index=s.getSelectedItemPosition();if(index<0||index>=rows.size())throw new IllegalArgumentException("No stock row found");return rows.get(index);}
    void qaScreen(){guard("ADMIN","MANAGER");start("Quality Control",orgTitle()+" • Immediate authorized status change",true);
        List<WarehouseDb.Product> products=db.products(org);productHelp(products);Spinner p=productSpinner(products);
        EditText batch=input("Exact batch number","",false);Spinner qa=spinner(new String[]{"RELEASE","HOLD","REJECT"});
        hint("Changing status updates all rack quantities belonging to the selected product and batch. No separate QA approval workflow is required in this version.");
        action("Update QA Status",()->{WarehouseDb.Product product=chosen(products,p);confirm("Quality "+select(qa),"Update "+product.name+" / "+batch.getText()+" to "+select(qa)+"?",()->{db.qa(user,org,product.name,batch.getText().toString(),select(qa));notice("QA status updated");qaScreen();});});
    }
    void countScreen(){guard("ADMIN","MANAGER","OPERATOR","CHECKER");start("Cycle / Physical Count",orgTitle()+" • Physical records kept separate from system stock",true);
        List<WarehouseDb.Stock> rows=db.stocks(org,"");Spinner from=stockSpinner(rows);EditText physical=input("Actual physically counted Bags / Boxes","",false);
        hint("Count creates a PENDING discrepancy entry. Manager/Admin must approve before system stock is replaced. Separate approval prevents silent adjustments.");
        action("Submit Physical Count",()->{WarehouseDb.Stock s=chosenStock(rows,from);db.count(user,s.id,Integer.parseInt(physical.getText().toString().trim()));notice("Count sent for verification (saved locally)");countScreen();});
        section("Your Rack Assignments");try(Cursor c=db.assignedRacks(user,is("ADMIN","MANAGER"))){int n=0;while(c.moveToNext()){
            final long id=c.getLong(0);String status=c.getString(4);
            info(c.getString(1)+" → "+c.getString(2)+" | Due "+c.getString(3)+" | "+status);
            if(status.equals("ASSIGNED")&&c.getString(2).equals(user))action("Mark "+c.getString(1)+" Count Complete",()->{db.completeAssignment(user,id);countScreen();});n++;
        }if(n==0)hint("No assigned racks yet");}
    }
    void reviewScreen(){guard("ADMIN","MANAGER");start("Review Discrepancies",orgTitle(),true);List<WarehouseDb.Count> rows=db.pendingCounts();hint("Pending counts: "+rows.size());
        for(WarehouseDb.Count row:rows){info("#"+row.id+"  "+row.product+" / "+row.batch+"\nRack: "+row.rack+" • System "+row.system+" vs Physical "+row.physical+" • Variance "+(row.physical-row.system)+"\nCounted by: "+row.actor);
            action("Approve Count #"+row.id+" and Adjust Stock",()->confirm("Approve Physical Stock","Replace system "+row.system+" with "+row.physical+"?",()->{db.reviewCount(user,row.id,true);reviewScreen();}));
            action("Reject Count #"+row.id,()->{db.reviewCount(user,row.id,false);reviewScreen();});
        }if(rows.isEmpty())hint("No physical counts awaiting approval.");
    }
    void assignmentScreen(){guard("ADMIN","MANAGER");start("Rack Count Assignment",orgTitle()+" • One active assignment per rack",true);
        EditText rack=input("Rack code (e.g. A01)","",false),assignee=input("Existing username","",false),due=input("Due date YYYY-MM-DD",WarehouseDb.today(),false);
        action("Assign Whole Rack",()->{db.assignRack(user,org,rack.getText().toString().trim().toUpperCase(Locale.US),assignee.getText().toString().trim(),due.getText().toString());notice("Assigned for count");assignmentScreen();});
        try(Cursor c=db.assignedRacks(user,true)){int n=0;while(c.moveToNext()&&n++<70)info("Rack "+c.getString(1)+" • "+c.getString(2)+" • "+c.getString(3)+" • "+c.getString(4));}
    }
    void reportsScreen(){start("Excel / PDF Reports",orgTitle()+" • Data is downloaded to your selected phone folder",true);
        section("Report Type");Spinner report=spinner(REPORTS);
        action("Preview Selected Report",()->{
            String key=select(report);try(Cursor c=db.report(key,org)){
                StringBuilder b=new StringBuilder();String[] heads=c.getColumnNames();for(String h:heads)b.append(h).append(" | ");b.append("\n\n");
                int n=0;while(c.moveToNext()&&n++<20){for(int i=0;i<c.getColumnCount();i++)b.append(c.getString(i)).append("  |  ");b.append("\n");}
                if(n==0)b.append("No entries found.");new AlertDialog.Builder(this).setTitle(key+" • First 20").setMessage(b.toString()).setPositiveButton("OK",null).show();
            }
        });
        action("Download Excel (.xlsx)",()->{
            String key=select(report);List<ExportKit.Sheet> sheets=new ArrayList<>();
            if(key.equals("Stock")){
                try(Cursor c=db.report("Product Summary",org)){sheets.add(ExportKit.read("Product Summary",c));}
                try(Cursor c=db.report("Stock",org)){sheets.add(ExportKit.read("Rack Data",c));}
            }else{try(Cursor c=db.report(key,org)){sheets.add(ExportKit.read(key,c));}}
            try{export(ExportKit.xlsx(sheets),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",safeName(key)+"_"+org+".xlsx");}catch(java.io.IOException ex){throw new IllegalArgumentException("Excel export failed: "+ex.getMessage());}
        });
        action("Download A4 Landscape PDF",()->{
            String key=select(report);ExportKit.Sheet sheet;try(Cursor c=db.report(key,org)){sheet=ExportKit.read(key,c);}
            try{export(ExportKit.pdf(sheet,orgTitle()+" | "+key),"application/pdf",safeName(key)+"_"+org+".pdf");}catch(java.io.IOException ex){throw new IllegalArgumentException("PDF export failed: "+ex.getMessage());}
        });
        section("One Rack: A4 PDF / Bin Card");EditText rack=input("One Rack number (e.g. A01)","",false);
        action("Download Single Rack PDF",()->{
            ExportKit.Sheet sheet;try(Cursor c=db.rackReport(org,rack.getText().toString())){sheet=ExportKit.read("Rack Data",c);}
            try{export(ExportKit.pdf(sheet,"Rack: "+rack.getText().toString().toUpperCase(Locale.US)+" | "+orgTitle()),"application/pdf","Rack_"+rack.getText().toString()+".pdf");}catch(java.io.IOException ex){throw new IllegalArgumentException("Rack PDF failed: "+ex.getMessage());}
        });
        hint("Excel Stock export has separate Product Summary and Rack Data sheets. Batch numbers are saved as text to preserve leading zeros. PDF is landscape A4.");
    }
    String safeName(String n){return n.replace(' ','_');}
    void export(byte[] bytes,String mime,String filename){pendingExport=bytes;pendingName=filename;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,filename);startActivityForResult(i,201);
    }
    void adminScreen(){guard("ADMIN");start("Admin Control Center","Products, users, rack master, recovery, import",true);
        action("Add / Change Products & UOM",this::productsAdmin);
        action("User Accounts / Reset Passwords",this::userAdmin);
        action("Rack Master (Add / Disable)",this::rackAdmin);
        action("Import Putaway CSV (Bulk)",()->{
            confirm("Bulk Putaway Import","CSV import ADDS receiving stock to existing data; it does not replace or reconcile opening stock. Proceed only for new receipts?",()->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,200);
            });
        });
        hint("Admin corrections are audited. Product UOM change affects display and current kg calculations for all stock of that product; audit history retains originally recorded unit weights.");
    }
    void productsAdmin(){guard("ADMIN");start("Product Master",orgTitle(),true);
        EditText product=input("New product name","",false),kg=input("Kg per Bag/Box (25 / 450 / 1200 etc.)","25",false);Spinner uom=spinner(new String[]{"Bags","Boxes"});
        action("Add Product",()->{db.addProduct(user,org,product.getText().toString(),select(uom),Double.parseDouble(kg.getText().toString()));notice("Product added");productsAdmin();});
        section("Change UOM for ALL existing stock");List<WarehouseDb.Product> products=db.products(org);productHelp(products);Spinner choose=productSpinner(products);
        EditText newKg=input("New kg per unit","25",false);Spinner target=spinner(new String[]{"Bags","Boxes"});
        action("Change Product-wide UOM / Unit Weight",()->{
            WarehouseDb.Product p=chosen(products,choose);confirm("Change ALL "+p.name+"?","All related stock displays and kg weights will reflect "+select(target)+" / "+newKg.getText()+" kg per unit.",()->{
                db.changeUom(user,p.id,select(target),Double.parseDouble(newKg.getText().toString()));notice("UOM updated product-wide");productsAdmin();
            });
        });
        for(WarehouseDb.Product p:products)info(p.name+" • "+p.uom+" • "+p.kg+" Kg");
    }
    void userAdmin(){guard("ADMIN");start("User Management","Admin creates all local user passwords",true);
        EditText username=input("New username","",false),pass=input("New password (10+ chars)","",true);Spinner role=spinner(new String[]{"OPERATOR","CHECKER","VIEWER","MANAGER","ADMIN"});
        action("Create User",()->{db.createUser(user,username.getText().toString(),pass.getText().toString(),select(role));notice("Local user created");userAdmin();});
        section("Reset an Existing User");EditText resetUser=input("Username to reset","",false),resetPass=input("New password (10+ chars)","",true);
        action("Reset User Password",()->{db.resetUser(user,resetUser.getText().toString(),resetPass.getText().toString());notice("If username exists, password updated");});
        section("Deactivate User");EditText disable=input("Username to disable","",false);
        action("Deactivate Non-Admin User",()->confirm("Disable login?","Disable "+disable.getText().toString()+" on this device?",()->{db.deactivate(user,disable.getText().toString());notice("User disabled where found");}));
        hint("Local users do not automatically work on other phones. Central identity/login will require the future sync backend. Admin recovery uses the code from initial setup.");
    }
    void rackAdmin(){guard("ADMIN");start("Rack Master",orgTitle()+" • Manual floor/temporary racks",true);
        hint("Preloaded: A01–K84 DP Cocoa; L01–P84 Mondelez; Q01–Q28 shared. Total 1,372. Extra codes like FLOOR-01 or TEMP-01 are supported.");
        EditText rack=input("Rack code (e.g. FLOOR-01)","",false);
        action("Add Rack",()->{db.addRack(user,rack.getText().toString(),org);notice("Rack created");rackAdmin();});
        section("Disable Empty Rack");EditText disable=input("Existing rack code","",false);
        action("Disable Rack",()->{db.disableRack(user,disable.getText().toString().trim().toUpperCase(Locale.US));notice("Rack disabled");rackAdmin();});
        info("Active company racks: "+db.racks(org).size());
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent result){super.onActivityResult(requestCode,resultCode,result);
        if(resultCode!=RESULT_OK || result==null || result.getData()==null)return;
        Uri uri=result.getData();
        if(requestCode==201&&pendingExport!=null){
            safe(()->{try(OutputStream s=getContentResolver().openOutputStream(uri)){if(s==null)throw new IllegalArgumentException("Cannot open selected file");s.write(pendingExport);notice("Saved "+pendingName);}catch(Exception e){throw new IllegalArgumentException("Save failed: "+e.getMessage());}finally{pendingExport=null;}});
        }else if(requestCode==200){safe(()->importCsv(uri));}
    }
    void importCsv(Uri uri){guard("ADMIN");try(InputStream stream=getContentResolver().openInputStream(uri)){
        if(stream==null)throw new IllegalArgumentException("Could not read file");ByteArrayOutputStream mem=new ByteArrayOutputStream();byte[] buf=new byte[8192];int read;
        while((read=stream.read(buf))!=-1){mem.write(buf,0,read);if(mem.size()>4_000_000)throw new IllegalArgumentException("CSV > 4MB; split into files");}
        String csv=mem.toString("UTF-8").replace("\uFEFF","");String[] lines=csv.split("\\r?\\n");
        if(lines.length<2)throw new IllegalArgumentException("CSV needs header + data rows");
        String[] header=parseCsvLine(lines[0]);String template="org,product,batch,rack,qty,mfg_date,qa,notes";
        requireHeader(header,template);
        int done=0,errors=0;StringBuilder detail=new StringBuilder();
        for(int n=1;n<lines.length;n++){
            if(lines[n].trim().isEmpty())continue;
            try {
                String[] fields=parseCsvLine(lines[n]);if(fields.length<8)throw new IllegalArgumentException("8 columns expected");
                String importedOrg=fields[0].trim().toUpperCase(Locale.US);
                if(!importedOrg.equals(WarehouseDb.DP)&&!importedOrg.equals(WarehouseDb.MDLZ))throw new IllegalArgumentException("Company must be DP or MDLZ");
                WarehouseDb.Product p=lookupProduct(db.products(importedOrg),fields[1]);
                db.receive(user,importedOrg,p.id,fields[2],fields[3],WarehouseDb.pos(fields[4]),fields[5],fields[6].toUpperCase(Locale.US),"CSV import: "+fields[7]);done++;
            }catch(Exception e){errors++;if(errors<=8)detail.append("Line ").append(n+1).append(": ").append(e.getMessage()).append("\n");}
        }
        new AlertDialog.Builder(this).setTitle("CSV Import Results").setMessage("Received rows: "+done+"\nFailed rows: "+errors+"\n\n"+detail).setPositiveButton("OK",null).show();
    }catch(Exception e){throw new IllegalArgumentException("CSV failed: "+e.getMessage());}}
    void requireHeader(String[] cols,String expected){String[] list=expected.split(",");if(cols.length<list.length)throw new IllegalArgumentException("Expected header: "+expected);
        for(int i=0;i<list.length;i++)if(!cols[i].trim().equalsIgnoreCase(list[i]))throw new IllegalArgumentException("Wrong CSV header; expected: "+expected);
    }
    String[] parseCsvLine(String line){List<String> values=new ArrayList<>();StringBuilder b=new StringBuilder();boolean quote=false;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='\"'){
            if(quote&&i+1<line.length()&&line.charAt(i+1)=='\"'){b.append('\"');i++;}else quote=!quote;
        }else if(c==','&&!quote){values.add(b.toString());b.setLength(0);}else b.append(c);}
        values.add(b.toString());return values.toArray(new String[0]);
    }
    @Override public void onBackPressed(){if(user.isEmpty())loginScreen();else home();}
}
