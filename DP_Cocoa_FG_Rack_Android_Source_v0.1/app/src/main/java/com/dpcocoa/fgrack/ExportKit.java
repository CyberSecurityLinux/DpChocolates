package com.dpcocoa.fgrack;

import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Built-in PDF and .xlsx exports, no external Excel or PDF packages. */
public final class ExportKit {
    private ExportKit() {}
    static class Sheet {String name;List<String[]> rows=new ArrayList<>();Sheet(String n){name=n;}}
    static Sheet read(String name,Cursor c) {
        Sheet s=new Sheet(name);s.rows.add(c.getColumnNames());
        while(c.moveToNext()){
            String[] row=new String[c.getColumnCount()];
            for(int i=0;i<row.length;i++)row[i]=c.isNull(i)?"":c.getString(i);
            s.rows.add(row);
        }return s;
    }
    static String xml(String s){return (s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    static String column(int i){String v="";for(int n=i+1;n>0;n=(n-1)/26)v=(char)('A'+(n-1)%26)+v;return v;}
    static void write(ZipOutputStream out,String name,String data) throws IOException {
        out.putNextEntry(new ZipEntry(name));out.write(data.getBytes(StandardCharsets.UTF_8));out.closeEntry();
    }
    public static byte[] xlsx(List<Sheet> sheets) throws IOException {
        ByteArrayOutputStream mem=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(mem)){
            StringBuilder ct=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            StringBuilder wb=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder rel=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for(int i=0;i<sheets.size();i++){
                int n=i+1;String path="/xl/worksheets/sheet"+n+".xml";
                ct.append("<Override PartName=\"").append(path).append("\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                wb.append("<sheet name=\"").append(xml(sheets.get(i).name)).append("\" sheetId=\"").append(n).append("\" r:id=\"rId").append(n).append("\"/>");
                rel.append("<Relationship Id=\"rId").append(n).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(n).append(".xml\"/>");
                Sheet s=sheets.get(i);StringBuilder body=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetData>");
                for(int r=0;r<s.rows.size();r++){
                    body.append("<row r=\"").append(r+1).append("\">");String[] values=s.rows.get(r);
                    for(int k=0;k<values.length;k++){
                        String a=column(k)+(r+1);String v=values[k]==null?"":values[k];
                        // Every cell is exported as text so batch codes and leading zeros are never lost.
                        body.append("<c r=\"").append(a).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(v)).append("</t></is></c>");
                    }body.append("</row>");
                }
                body.append("</sheetData></worksheet>");write(zip,"xl/worksheets/sheet"+n+".xml",body.toString());
            }
            ct.append("</Types>");wb.append("</sheets></workbook>");rel.append("</Relationships>");
            write(zip,"[Content_Types].xml",ct.toString());
            write(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            write(zip,"xl/workbook.xml",wb.toString());write(zip,"xl/_rels/workbook.xml.rels",rel.toString());
        }return mem.toByteArray();
    }
    public static byte[] pdf(Sheet sheet,String title) throws IOException {
        PdfDocument doc=new PdfDocument();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        int page=0,row=0;int width=842,height=595;
        try{
            while(row<sheet.rows.size() || page==0){
                PdfDocument.Page p=doc.startPage(new PdfDocument.PageInfo.Builder(width,height,++page).create());Canvas c=p.getCanvas();
                c.drawColor(0xffffffff);paint.setColor(0xff4f3022);paint.setTextSize(17);paint.setFakeBoldText(true);
                c.drawText("DP COCOA  |  FG RACK MANAGEMENT",26,32,paint);
                paint.setTextSize(10);paint.setColor(0xff222222);paint.setFakeBoldText(false);
                c.drawText(title+"   |   "+WarehouseDb.today()+"   |   Page "+page,26,52,paint);
                int y=78;
                String[] header=sheet.rows.isEmpty()?new String[0]:sheet.rows.get(0);
                paint.setColor(0xffefe6da);c.drawRect(20,61,width-20,88,paint);
                paint.setColor(0xff352216);paint.setTextSize(8);paint.setFakeBoldText(true);
                paintRow(c,paint,header,76,width);paint.setFakeBoldText(false);
                y=104;
                while(row<sheet.rows.size() && y<height-20){
                    if(row==0){row++;continue;}
                    paint.setColor((row%2==0)?0xfff7f4ee:0xffffffff);c.drawRect(20,y-13,width-20,y+4,paint);
                    paint.setColor(0xff222222);paintRow(c,paint,sheet.rows.get(row),y,width);
                    y+=18;row++;
                }
                doc.finishPage(p);
            }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();doc.writeTo(bytes);return bytes.toByteArray();
        }finally{doc.close();}
    }
    static void paintRow(Canvas c,Paint p,String[] vals,int y,int width) {
        if(vals.length==0)return;
        float total=width-55f,unit=total/vals.length;
        for(int i=0;i<vals.length;i++){
            String str=vals[i]==null?"":vals[i];int cap=Math.max(5,(int)(unit/4.5f));
            if(str.length()>cap)str=str.substring(0,cap-1)+"…";
            c.drawText(str,25+i*unit,y,p);
        }
    }
}
