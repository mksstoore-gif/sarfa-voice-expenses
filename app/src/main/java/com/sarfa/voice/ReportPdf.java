package com.sarfa.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Native Android PDF exporter. StaticLayout uses RTL shaping for joined Arabic letters.
 * Rendering and PDF integrity are exercised on Android emulator by ReportPdfTest.
 */
public final class ReportPdf {
    private static final int W = 595, H = 842;
    private static final int INK = Color.rgb(30, 45, 44);
    private static final int GREEN = Color.rgb(20, 122, 99);
    private static final int GREY = Color.rgb(112, 126, 124);
    private static final int SOFT = Color.rgb(246, 249, 248);
    private ReportPdf() {}

    public static byte[] generate(Context context, List<Ledger.Entry> entries) throws IOException {
        PdfDocument pdf = new PdfDocument();
        try {
            long income = Ledger.sum(entries, true);
            long out = Ledger.sum(entries, false);
            int page = 1;
            int index = 0;
            boolean first = true;
            do {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(W, H, page).create();
                PdfDocument.Page docPage = pdf.startPage(info);
                Canvas c = docPage.getCanvas();
                c.drawColor(Color.WHITE);
                title(c, first ? "تقرير صَرْفة المالي" : "تقرير صَرْفة — متابعة", 43);
                if (first) {
                    rect(c, 32, 95, 563, 220, SOFT, 18);
                    rtl(c, "الرصيد المتاح", 537, 114, 12, false, GREY, 230);
                    rtl(c, Ledger.money(Ledger.balance(entries)) + " ر.س", 537, 144, 25, true, INK, 480);
                    rtl(c, "إجمالي الدخل: " + Ledger.money(income) + " ر.س", 537, 184, 11, false, GREEN, 245);
                    rtl(c, "إجمالي الصرف: " + Ledger.money(out) + " ر.س", 285, 184, 11, false, INK, 250);
                    rtl(c, "تفاصيل العمليات (" + entries.size() + ")", 560, 258, 16, true, INK, 500);
                }
                int y = first ? 300 : 125;
                int rowsPerPage = first ? 13 : 20;
                int end = Math.min(index + rowsPerPage, entries.size());
                if (entries.isEmpty() && first) {
                    rtl(c, "لا توجد عمليات مسجلة حتى الآن", 550, y, 15, false, GREY, 500);
                }
                for (; index < end; index++) {
                    Ledger.Entry e = entries.get(index);
                    if ((index % 2) == 0) rect(c, 32, y - 9, 563, y + 28, SOFT, 7);
                    rtl(c, e.note, 552, y, 12, true, INK, 235);
                    rtl(c, (e.income ? "دخل" : e.category) + " · " +
                            new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(e.timestamp)),
                            552, y + 16, 9, false, GREY, 242);
                    latin(c, (e.income ? "+" : "−") + Ledger.money(e.halalas) + " SAR",
                            47, y + 9, 12, e.income ? GREEN : INK);
                    y += 39;
                }
                line(c, 33, H - 51, W - 33, H - 51, 0xFFE6ECEA);
                rtl(c, "تم إصدار التقرير من تطبيق صَرْفة", 555, H - 39, 9, false, GREY, 390);
                latin(c, "Page " + page, 34, H - 30, 9, GREY);
                pdf.finishPage(docPage);
                first = false;
                page++;
            } while (index < entries.size());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pdf.writeTo(bytes);
            return bytes.toByteArray();
        } finally {
            pdf.close();
        }
    }

    private static void title(Canvas c, String text, int y) {
        rtl(c, text, 560, y, 23, true, INK, 510);
        line(c, 32, y + 40, 563, y + 40, GREEN);
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date());
        latin(c, date, 32, y + 20, 11, GREY);
    }
    private static void rect(Canvas c, float l, float t, float r, float b, int color, float radius) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawRoundRect(l,t,r,b,radius,radius,p);
    }
    private static void line(Canvas c, float x1, float y1, float x2, float y2, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(1.25f);
        c.drawLine(x1,y1,x2,y2,p);
    }
    private static void latin(Canvas c, String text, float x, float baseline, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.drawText(text, x, baseline, p);
    }
    /** Draw RTL with StaticLayout so Android's text engine shapes Arabic correctly. */
    private static void rtl(Canvas c, String text, float right, float top, int size,
                            boolean bold, int color, int width) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), p, width)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false).build();
        c.save();
        c.translate(right - width, top);
        layout.draw(c);
        c.restore();
    }
}
