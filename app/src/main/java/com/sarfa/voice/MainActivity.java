package com.sarfa.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int RECOGNIZE = 101;
    private static final int EXPORT = 102;
    private static final int GREEN = Color.rgb(20, 122, 99);
    private static final int DARK = Color.rgb(26, 45, 43);
    private static final int MUTED = Color.rgb(121, 133, 131);
    private static final int PAPER = Color.WHITE;
    private static final int SOFT = Color.rgb(246, 249, 248);
    private final List<Ledger.Entry> entries = new ArrayList<>();
    private LinearLayout screen;
    private boolean pendingIncome = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        readEntries();
        showDashboard();
    }

    private int dp(float value) { return (int)(getResources().getDisplayMetrics().density * value + 0.5f); }

    private GradientDrawable surface(int color, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private void gap(LinearLayout l, int height) {
        View v = new View(this);
        l.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, -2, w);
    }
    private void pad(View v, int a, int b, int c, int d) {
        v.setPadding(dp(a),dp(b),dp(c),dp(d));
    }

    private void showDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        screen = vertical();
        pad(screen, 22, 22, 22, 26);
        scroll.addView(screen);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)-> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                screen.setPadding(dp(22),dp(22) + bars.top,dp(22),dp(26) + bars.bottom);
            }
            return insets;
        });

        LinearLayout heading = horizontal();
        LinearLayout brand = vertical();
        brand.addView(label("صَرْفة", 29, DARK, true));
        brand.addView(label("فلوسك واضحة، من غير تعب", 12, MUTED, false));
        heading.addView(brand, weight(1));
        TextView pdf = label("↧  PDF", 13, GREEN, true);
        pdf.setGravity(Gravity.CENTER);
        pad(pdf, 15, 11, 15, 11);
        pdf.setBackground(surface(SOFT, 16, 0));
        pdf.setContentDescription("تصدير تقرير PDF");
        pdf.setOnClickListener(v -> choosePdfLocation());
        heading.addView(pdf);
        screen.addView(heading);
        gap(screen, 27);

        LinearLayout balance = vertical();
        pad(balance, 22, 22, 22, 24);
        balance.setBackground(surface(SOFT, 23, 0));
        balance.addView(label("الرصيد المتاح الآن", 14, MUTED, false));
        gap(balance, 8);
        LinearLayout balanceLine = horizontal();
        balanceLine.addView(label(Ledger.money(Ledger.balance(entries)), 37, DARK, true));
        TextView currency = label("ر.س", 16, GREEN, true);
        pad(currency, 9, 0, 0, 0);
        balanceLine.addView(currency);
        balance.addView(balanceLine);
        gap(balance, 16);

        List<Ledger.Entry> month = Ledger.currentMonth(entries,System.currentTimeMillis());
        LinearLayout totals = horizontal();
        totals.addView(stat("↓  الدخل هذا الشهر", Ledger.money(Ledger.sum(month, true)), GREEN), weight(1));
        View spacer = new View(this);
        totals.addView(spacer, new LinearLayout.LayoutParams(dp(12), 1));
        totals.addView(stat("↑  الصرف هذا الشهر", Ledger.money(Ledger.sum(month, false)), DARK), weight(1));
        balance.addView(totals);
        screen.addView(balance);
        gap(screen, 28);

        screen.addView(label("سجّل عملية بصوتك", 20, DARK, true));
        gap(screen, 5);
        screen.addView(label("قل المبلغ والسبب، وأكد قبل الحفظ", 13, MUTED, false));
        gap(screen, 18);

        LinearLayout actions = horizontal();
        TextView expense = action("−", "صرف", false);
        TextView income = action("+", "دخل", true);
        actions.addView(expense, weight(1));
        View between = new View(this);
        actions.addView(between, new LinearLayout.LayoutParams(dp(12),1));
        actions.addView(income, weight(1));
        screen.addView(actions);
        gap(screen, 13);

        TextView voiceHint = label("مثال: «15 ريال عشاء لحمودي»", 13, MUTED, false);
        voiceHint.setGravity(Gravity.CENTER);
        pad(voiceHint, 5, 5, 5, 5);
        screen.addView(voiceHint);
        gap(screen, 34);

        LinearLayout historyHeading = horizontal();
        historyHeading.addView(label("آخر العمليات", 20, DARK, true), weight(1));
        TextView count = label(entries.size() + " عملية", 12, MUTED, false);
        historyHeading.addView(count);
        screen.addView(historyHeading);
        gap(screen, 14);
        if (entries.isEmpty()) {
            LinearLayout empty = vertical();
            pad(empty, 20, 35, 20, 35);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(surface(SOFT, 18, 0));
            TextView emptyIcon = label("◌", 37, GREEN, false);
            emptyIcon.setGravity(Gravity.CENTER);
            empty.addView(emptyIcon);
            TextView e = label("ما عندك عمليات إلى الآن", 15, DARK, true);
            e.setGravity(Gravity.CENTER);
            empty.addView(e);
            gap(empty, 5);
            TextView t = label("ابدأ من زر الصرف أو الدخل", 12, MUTED, false);
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            screen.addView(empty);
        } else {
            int limit = Math.min(entries.size(), 40);
            for (int i=0;i<limit;i++) {
                Ledger.Entry e=entries.get(i);
                screen.addView(entryRow(e));
                gap(screen, 7);
            }
            if (entries.size()>limit) screen.addView(label("يعرض آخر 40 عملية. التقرير يحتوي على السجل كاملًا.",12,MUTED,false));
        }
        gap(screen, 20);
        TextView footer = label("بياناتك محفوظة على هذا الجهاز فقط", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        screen.addView(footer);
    }

    private LinearLayout stat(String caption, String amount, int color) {
        LinearLayout l = vertical();
        l.addView(label(caption, 11, MUTED, false));
        gap(l, 6);
        l.addView(label(amount + " ر.س", 17, color, true));
        return l;
    }

    private TextView action(String symbol, String name, boolean income) {
        TextView v=label(symbol + "    " + name + " بالصوت", 16, income ? GREEN : PAPER, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(82));
        v.setBackground(surface(income ? 0xFFE9F5F0 : GREEN, 18, 0));
        v.setContentDescription("تسجيل " + name + " بالصوت");
        v.setOnClickListener(w -> listen(income));
        return v;
    }

    private View entryRow(Ledger.Entry e) {
        LinearLayout row = horizontal();
        pad(row, 12, 13, 12, 13);
        row.setBackground(surface(PAPER, 12, 0xFFE9EEED));
        TextView icon = label(e.income ? "+" : "−", 23, e.income ? GREEN : DARK, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(surface(e.income ? 0xFFE9F5F0 : SOFT, 12, 0));
        row.addView(icon,new LinearLayout.LayoutParams(dp(47),dp(47)));
        LinearLayout middle=vertical();
        pad(middle, 12, 0, 12, 0);
        TextView note = label(e.note, 14, DARK, true);
        note.setMaxLines(1);
        note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        middle.addView(note);
        gap(middle, 4);
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date(e.timestamp));
        middle.addView(label(e.category + " · " + date, 11, MUTED, false));
        row.addView(middle, weight(1));
        TextView money = label((e.income ? "+" : "−") + Ledger.money(e.halalas) + " ر.س", 15, e.income ? GREEN : DARK, true);
        money.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        row.addView(money);
        row.setContentDescription(e.note+"، "+Ledger.money(e.halalas)+" ريال. اضغط للحذف");
        row.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("حذف العملية؟")
                .setMessage(e.note + " — " + Ledger.money(e.halalas) + " ر.س\nسيتم تعديل الرصيد بعد الحذف.")
                .setNegativeButton("إلغاء", (d,w)->{})
                .setPositiveButton("حذف", (d,w)->{
                    entries.removeIf(item -> item.id.equals(e.id));
                    writeEntries();
                    showDashboard();
                }).show());
        return row;
    }

    private void listen(boolean income) {
        pendingIncome=income;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, income ? "قل المبلغ ومصدر الدخل" : "قل المبلغ وسبب الصرف");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            startActivityForResult(intent, RECOGNIZE);
        } catch (ActivityNotFoundException ex) {
            new AlertDialog.Builder(this).setTitle("التعرف على الصوت غير متوفر")
                    .setMessage("تحتاج خدمة تعرف على الكلام تدعم العربية على جهازك. لا يمكن تسجيل عملية دون التعرف على مبلغها.")
                    .setPositiveButton("حسنًا",null).show();
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode==RECOGNIZE) {
            if (resultCode != RESULT_OK || data==null) return;
            ArrayList<String> speech = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (speech == null || speech.isEmpty()) return;
            for (String candidate : speech) {
                try {
                    ExpenseParser.Result parsed = ExpenseParser.parse(candidate,pendingIncome);
                    showConfirmation(parsed);
                    return;
                } catch (IllegalArgumentException ignored) { }
            }
            new AlertDialog.Builder(this).setTitle("لم أتعرف على مبلغ واحد")
                    .setMessage("سمعت: «" + speech.get(0) + "»\nقل مبلغًا واحدًا واضحًا مثل «15 ريال عشاء لحمودي». لم تُحفظ أي عملية.")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("أعد التسجيل",(d,w)->listen(pendingIncome)).show();
        } else if (requestCode==EXPORT && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            try (OutputStream stream=getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new IllegalStateException("تعذر فتح الملف");
                byte[] report=ReportPdf.generate(this,entries);
                stream.write(report);
                stream.flush();
                Toast.makeText(this,"تم حفظ تقرير PDF",Toast.LENGTH_LONG).show();
            } catch(Exception ex) {
                new AlertDialog.Builder(this).setTitle("تعذر تصدير PDF")
                    .setMessage("لم يُحفظ التقرير. " + ex.getMessage())
                    .setPositiveButton("حسنًا",null).show();
            }
        }
    }

    private void showConfirmation(ExpenseParser.Result parsed) {
        LinearLayout content=vertical();
        pad(content,24,12,24,8);
        TextView heading=label(parsed.income ? "تأكيد تسجيل الدخل" : "تأكيد تسجيل الصرف", 17, DARK, true);
        content.addView(heading);
        gap(content, 17);
        TextView amount=label(Ledger.money(parsed.halalas) + " ر.س", 36, parsed.income ? GREEN : DARK, true);
        content.addView(amount);
        gap(content, 13);
        content.addView(label("النوع: " + (parsed.income ? "دخل" : "صرف"), 14, DARK, false));
        content.addView(label("التصنيف: " + parsed.category, 14, DARK, false));
        content.addView(label("التفاصيل: " + parsed.note, 14, DARK, false));
        gap(content, 15);
        content.addView(label("راجع المبلغ قبل التأكيد. لن يُخصم أو يُضاف شيء قبل الضغط على حفظ.", 12, MUTED, false));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(content)
            .setNegativeButton("إلغاء", (d,w)->{})
            .setNeutralButton("أعد بالصوت", (d,w)->listen(parsed.income))
            .setPositiveButton("تأكيد وحفظ", (d,w)->{
                entries.add(0, new Ledger.Entry(UUID.randomUUID().toString(),parsed.income,
                        parsed.halalas,parsed.category,parsed.note,System.currentTimeMillis()));
                if (writeEntries()) {
                    showDashboard();
                } else {
                    entries.remove(0);
                    new AlertDialog.Builder(this).setTitle("لم يتم الحفظ")
                            .setMessage("حدث خطأ في تخزين البيانات على الجهاز.")
                            .setPositiveButton("حسنًا",null).show();
                }
            }).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GREEN);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(GREEN);
    }

    private void choosePdfLocation() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE,"Sarfa_Report_"+day+".pdf");
        try { startActivityForResult(intent,EXPORT); }
        catch (ActivityNotFoundException e) { Toast.makeText(this,"لا يوجد تطبيق لحفظ PDF",Toast.LENGTH_LONG).show(); }
    }

    private void readEntries() {
        entries.clear();
        String saved=getSharedPreferences("sarfa",MODE_PRIVATE).getString("entries","[]");
        try {
            JSONArray a=new JSONArray(saved);
            for(int i=0;i<a.length();i++){
                JSONObject j=a.getJSONObject(i);
                entries.add(new Ledger.Entry(j.getString("id"),j.getBoolean("income"),
                        j.getLong("halalas"),j.getString("category"),j.getString("note"),j.getLong("timestamp")));
            }
            Collections.sort(entries,(a1,b1)->Long.compare(b1.timestamp,a1.timestamp));
        } catch(Exception ex) {
            // Never replace a damaged ledger with an empty record: allow inspection / recovery.
            new AlertDialog.Builder(this).setTitle("تعذر قراءة السجل")
                .setMessage("بياناتك ما زالت محفوظة كما هي. تجنب إضافة عمليات جديدة قبل استعادة السجل.")
                .setPositiveButton("حسنًا",null).show();
        }
    }
    private boolean writeEntries() {
        JSONArray a=new JSONArray();
        try {
            for (Ledger.Entry e:entries) {
                JSONObject j=new JSONObject();
                j.put("id",e.id);
                j.put("income",e.income);
                j.put("halalas",e.halalas);
                j.put("category",e.category);
                j.put("note",e.note);
                j.put("timestamp",e.timestamp);
                a.put(j);
            }
            return getSharedPreferences("sarfa",MODE_PRIVATE).edit()
                    .putString("entries",a.toString()).commit();
        } catch(Exception e) {return false;}
    }
}
