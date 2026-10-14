package com.sarfa.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Ledger {
    private Ledger() {}

    public static final class Entry {
        public final String id;
        public final boolean income;
        public final long halalas;
        public final String category;
        public final String note;
        public final long timestamp;

        public Entry(String id, boolean income, long halalas, String category, String note, long timestamp) {
            if (halalas <= 0) throw new IllegalArgumentException("Amount must be positive");
            this.id = id;
            this.income = income;
            this.halalas = halalas;
            this.category = category;
            this.note = note;
            this.timestamp = timestamp;
        }
    }

    public static long balance(List<Entry> entries) {
        long total = 0;
        for (Entry e : entries) total = Math.addExact(total, e.income ? e.halalas : -e.halalas);
        return total;
    }

    public static long sum(List<Entry> entries, boolean income) {
        long total = 0;
        for (Entry e : entries) if (e.income == income) total = Math.addExact(total, e.halalas);
        return total;
    }

    public static String money(long halalas) {
        boolean negative = halalas < 0;
        long abs = Math.abs(halalas);
        String whole = String.format(Locale.US, "%,d", abs / 100);
        String fraction = abs % 100 == 0 ? "" : String.format(Locale.US, ".%02d", abs % 100);
        return (negative ? "−" : "") + whole + fraction;
    }

    public static List<Entry> currentMonth(List<Entry> entries, long now) {
        java.util.Calendar start = java.util.Calendar.getInstance();
        start.setTimeInMillis(now);
        start.set(java.util.Calendar.DAY_OF_MONTH, 1);
        start.set(java.util.Calendar.HOUR_OF_DAY, 0);
        start.set(java.util.Calendar.MINUTE, 0);
        start.set(java.util.Calendar.SECOND, 0);
        start.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Calendar next = (java.util.Calendar) start.clone();
        next.add(java.util.Calendar.MONTH, 1);
        List<Entry> monthly = new ArrayList<>();
        for (Entry e : entries) if (e.timestamp >= start.getTimeInMillis() && e.timestamp < next.getTimeInMillis()) monthly.add(e);
        return monthly;
    }
}
