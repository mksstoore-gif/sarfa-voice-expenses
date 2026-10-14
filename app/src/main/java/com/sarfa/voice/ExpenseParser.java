package com.sarfa.voice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative Arabic voice expense parser. Never persists a transaction by itself. */
public final class ExpenseParser {
    public static final class Result {
        public final boolean income;
        public final long halalas;
        public final String category;
        public final String note;
        public final String original;
        Result(boolean income, long halalas, String category, String note, String original) {
            this.income = income;
            this.halalas = halalas;
            this.category = category;
            this.note = note;
            this.original = original;
        }
    }

    private static final Pattern NUMERIC = Pattern.compile("(?<![0-9])([0-9]+(?:[.,][0-9]{1,2})?)(?![0-9])");
    private static final Map<String, Integer> WORDS = new HashMap<>();
    static {
        add(1,"واحد","واحدة","احد","احدى"); add(2,"اثنين","اثنان","اثنتين","اثنتان","ثنين");
        add(3,"ثلاثة","ثلاث"); add(4,"اربعة","اربع"); add(5,"خمسة","خمس");
        add(6,"ستة","ست"); add(7,"سبعة","سبع"); add(8,"ثمانية","ثمان");
        add(9,"تسعة","تسع"); add(10,"عشرة","عشر");
        add(11,"احدعشر","احدىعشر"); add(12,"اثناعشر","اثنيعشر");
        add(15,"خمستعشر","خمسةعشر");
        add(20,"عشرين","عشرون"); add(30,"ثلاثين","ثلاثون"); add(40,"اربعين","اربعون");
        add(50,"خمسين","خمسون"); add(60,"ستين","ستون"); add(70,"سبعين","سبعون");
        add(80,"ثمانين","ثمانون"); add(90,"تسعين","تسعون");
        add(100,"مية","مئة","مائه","مائة","ميه");
        add(200,"ميتين","مئتين","مائتين");
        add(300,"ثلاثمية","ثلاثمئة"); add(400,"اربعمية","اربعمئة");
        add(500,"خمسمية","خمسمئة"); add(600,"ستمية","ستمئة");
        add(700,"سبعمية","سبعمئة"); add(800,"ثمانمية","ثمانمئة");
        add(900,"تسعمية","تسعمئة");
        add(1000,"الف","ألف"); add(2000,"الفين","ألفين");
    }
    private static void add(int value, String... aliases) {
        for (String alias : aliases) WORDS.put(normalize(alias), value);
    }
    private ExpenseParser() {}

    static String normalize(String input) {
        return input.replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3')
                .replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7')
                .replace('٨','8').replace('٩','9').replace('۰','0').replace('۱','1')
                .replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5')
                .replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
                .replace("أ","ا").replace("إ","ا").replace("آ","ا")
                .replace("ـ","").replaceAll("[\\u064B-\\u065F]", "").trim();
    }

    public static Result parse(String speech, boolean defaultIncome) {
        if (speech == null || speech.trim().isEmpty()) throw new IllegalArgumentException("لم أسمع وصف العملية");
        String original = speech.trim();
        String normalized = normalize(original);
        Matcher digits = NUMERIC.matcher(normalized);
        List<String> found = new ArrayList<>();
        while (digits.find()) found.add(digits.group(1));

        long amount;
        String amountWords = "";
        if (!found.isEmpty()) {
            if (found.size() != 1) throw new IllegalArgumentException("سمعت أكثر من مبلغ، أعد التسجيل وحدد مبلغاً واحداً");
            try {
                amount = new BigDecimal(found.get(0).replace(',','.')).setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2).longValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException("المبلغ غير واضح");
            }
        } else {
            WordAmount parsed = parseWords(normalized);
            if (parsed == null) throw new IllegalArgumentException("لم أتعرف على المبلغ، حاول قول المبلغ بوضوح");
            amount = parsed.value * 100L;
            amountWords = parsed.text;
        }
        if (amount <= 0 || amount > 99_999_999_900L) throw new IllegalArgumentException("المبلغ خارج النطاق المسموح");

        boolean explicitIncome = containsAny(normalized, "دخل","ايداع","راتب","استلمت","وصلني","قبضت","مكافاة","حوالة واردة");
        boolean explicitExpense = containsAny(normalized, "صرف","صرفت","مصروف","دفعت","اشتريت","شريت","سددت");
        if (explicitIncome && explicitExpense) throw new IllegalArgumentException("غير واضح هل العملية دخل أو صرف");
        boolean income = explicitIncome || (!explicitExpense && defaultIncome);
        String category = categoryFor(normalized, income);

        String note = normalized.replaceFirst("(?<![0-9])" + Pattern.quote(found.isEmpty() ? "NO_DIGITS" : found.get(0)) + "(?![0-9])", " ");
        if (!amountWords.isEmpty()) note = note.replaceFirst(Pattern.quote(amountWords), " ");
        note = note.replaceAll("(?i)(ريال|ريالات|ريالاً|رس|ر\\.س|سعودي|دخل|ايداع|استلمت|وصلني|قبضت|صرفت|صرف|مصروف|دفعت|اشتريت|شريت|سددت)", " ");
        note = note.replaceAll("\\s+", " ").trim();
        if (note.isEmpty()) note = income ? "دخل عام" : "مصروف عام";
        return new Result(income, amount, category, note, original);
    }

    private static String categoryFor(String s, boolean income) {
        if (income) return "دخل";
        if (containsAny(s,"عشاء","غداء","فطور","مطعم","اكل","قهوة","كوفي","وجبة","بيتزا")) return "طعام ومشروبات";
        if (containsAny(s,"بنزين","سيارة","اوبر","تاكسي","مواصلات","مشوار","محطة")) return "مواصلات";
        if (containsAny(s,"كهرباء","ماء","انترنت","جوال","فاتورة","ايجار")) return "فواتير";
        if (containsAny(s,"صيدلية","مستشفى","دواء","طبيب","علاج")) return "صحة";
        if (containsAny(s,"سوبرماركت","بقالة","تسوق","ملابس","مشتريات")) return "تسوق";
        return "مصاريف عامة";
    }
    private static boolean containsAny(String s, String... patterns) {
        for (String p : patterns) if (s.contains(p)) return true;
        return false;
    }
    private static class WordAmount {
        final long value;
        final String text;
        WordAmount(long value, String text) { this.value = value; this.text = text; }
    }

    private static WordAmount parseWords(String phrase) {
        String[] tokens = phrase.split("[\\s،,]+");
        int start = -1, end = -1;
        long subtotal = 0, total = 0;
        boolean seen = false;
        for (int i=0;i<tokens.length;i++) {
            String token = tokens[i].replaceAll("[^\\p{L}]", "");
            Integer number = WORDS.get(token);
            if (number == null && token.startsWith("و") && token.length()>1)
                number = WORDS.get(token.substring(1));
            if (number != null) {
                if (start == -1) start = i;
                else if (end != i-1) throw new IllegalArgumentException("سمعت أكثر من مبلغ، أعد التسجيل");
                end = i;
                seen = true;
                if (number >= 1000) {
                    total += (subtotal == 0 ? 1 : subtotal) * number;
                    subtotal = 0;
                } else if (number == 100 && subtotal > 0 && subtotal < 10) subtotal *= 100;
                else subtotal += number;
            } else if (seen && (token.equals("و") || token.equals("ريال") || token.equals("ريالات"))) {
                // Currency or conjunction ends the number phrase.
            }
        }
        if (!seen) return null;
        long value = total + subtotal;
        if (value <= 0) return null;
        StringBuilder span = new StringBuilder();
        for (int i=start;i<=end;i++) {
            if (span.length()>0) span.append(" ");
            span.append(tokens[i]);
        }
        return new WordAmount(value, span.toString());
    }
}
