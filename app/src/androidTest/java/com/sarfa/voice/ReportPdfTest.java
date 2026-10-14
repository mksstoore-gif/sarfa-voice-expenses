package com.sarfa.voice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReportPdfTest {
    @Test public void createsAndRendersRealArabicPdf() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        List<Ledger.Entry> entries=new ArrayList<>();
        long now=System.currentTimeMillis();
        entries.add(new Ledger.Entry("1",true,10000,"دخل","راتب",now));
        entries.add(new Ledger.Entry("2",false,1500,"طعام ومشروبات","عشاء لحمودي",now));
        entries.add(new Ledger.Entry("3",false,2500,"مواصلات","بنزين",now));
        byte[] pdf=ReportPdf.generate(c,entries);
        assertTrue("A PDF should not be empty",pdf.length>1500);
        assertEquals("%PDF",new String(pdf,0,4,java.nio.charset.StandardCharsets.US_ASCII));
        File saved=new File(c.getFilesDir(),"test-report.pdf");
        try (FileOutputStream out=new FileOutputStream(saved)){out.write(pdf);}
        assertTrue(saved.isFile());
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(saved,ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer=new PdfRenderer(fd)) {
            assertEquals(1,renderer.getPageCount());
            try(PdfRenderer.Page page=renderer.openPage(0)) {
                Bitmap bitmap=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                int nonwhite=0;
                for(int y=40;y<bitmap.getHeight()-40;y+=3)
                    for(int x=30;x<bitmap.getWidth()-30;x+=3)
                        if(bitmap.getPixel(x,y)!=Color.WHITE) nonwhite++;
                assertTrue("PDF content must actually render",nonwhite>250);
                bitmap.recycle();
            }
        }
    }
    @Test public void multiPageAndEmptyReportsAreValid() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        assertTrue(ReportPdf.generate(c,new ArrayList<>()).length>1000);
        List<Ledger.Entry> records=new ArrayList<>();
        for(int i=0;i<80;i++)
            records.add(new Ledger.Entry(String.valueOf(i),false,1500,"طعام ومشروبات","عشاء لحمودي",System.currentTimeMillis()));
        File output=new File(c.getFilesDir(),"test-multipage-report.pdf");
        try(FileOutputStream fos=new FileOutputStream(output)){fos.write(ReportPdf.generate(c,records));}
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer pdf=new PdfRenderer(fd)) {
            assertTrue("Pagination must not clip transaction history",pdf.getPageCount()>1);
        }
    }
}
