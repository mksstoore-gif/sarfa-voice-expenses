package com.sarfa.voice;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ExpenseParserTest {
    private ExpenseParser.Result parse(String s) { return ExpenseParser.parse(s,false); }

    @Test public void expenseFromSpeech() {
        ExpenseParser.Result r=parse("15 ريال عشاء لحمودي");
        assertEquals(1500, r.halalas);
        assertFalse(r.income);
        assertEquals("طعام ومشروبات",r.category);
        assertEquals("عشاء لحمودي",r.note);
    }
    @Test public void incomeFromSpeech() {
        ExpenseParser.Result r=parse("دخل 500 ريال راتب");
        assertEquals(50000,r.halalas);
        assertTrue(r.income);
        assertEquals("دخل",r.category);
    }
    @Test public void incomeViaIncomeButton() {
        ExpenseParser.Result r=ExpenseParser.parse("900 ريال من الوالد",true);
        assertTrue(r.income);
        assertEquals(90000,r.halalas);
    }
    @Test public void explicitExpenseOverridesIncomeButton() {
        ExpenseParser.Result r=ExpenseParser.parse("صرف ٣٠ ريال بنزين",true);
        assertFalse(r.income);
        assertEquals("مواصلات",r.category);
    }
    @Test public void arabicIndicDigits() { assertEquals(1500,parse("١٥ ريال عشاء").halalas); }
    @Test public void easternIndicDigits() { assertEquals(1500,parse("۱۵ ريال عشاء").halalas); }
    @Test public void decimals() { assertEquals(2450,parse("24.50 ريال قهوة").halalas); }
    @Test public void commaDecimal() { assertEquals(2750,parse("27,50 ريال غداء").halalas); }
    @Test public void spokenFifteen() { assertEquals(1500,parse("خمسة عشر ريال عشاء").halalas); }
    @Test public void spokenTwentyFive() { assertEquals(2500,parse("خمسة وعشرين ريال قهوة").halalas); }
    @Test public void spokenHundred() { assertEquals(10000,parse("مية ريال هدية").halalas); }
    @Test public void spokenThousand() { assertEquals(100000,parse("الف ريال").halalas); }
    @Test public void zeroIsRejected() {
        assertThrows(IllegalArgumentException.class,()->parse("0 ريال"));
    }
    @Test public void missingAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,()->parse("اشتريت عشاء"));
    }
    @Test public void multipleAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->parse("15 ريال و 20 ريال"));
    }
    @Test public void contradictoryTypeRejected() {
        assertThrows(IllegalArgumentException.class,()->parse("دخل وصرفت 20 ريال"));
    }
    @Test public void ledgerUpdatesExactlyInHalalas() {
        List<Ledger.Entry> entries=Arrays.asList(
            new Ledger.Entry("1",true,10000,"دخل","راتب",1),
            new Ledger.Entry("2",false,1500,"طعام ومشروبات","عشاء لحمودي",2),
            new Ledger.Entry("3",false,250,"مواصلات","بنزين",3)
        );
        assertEquals(8250,Ledger.balance(entries));
        assertEquals(10000,Ledger.sum(entries,true));
        assertEquals(1750,Ledger.sum(entries,false));
        assertEquals("82.50",Ledger.money(Ledger.balance(entries)));
    }
    @Test public void emptyLedgerIsZero() {
        assertEquals(0,Ledger.balance(Collections.emptyList()));
        assertEquals("0",Ledger.money(0));
    }
    @Test public void januaryFirstMonthBoundary() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(2026, java.util.Calendar.JANUARY, 15, 12, 0, 0);
        long mid=c.getTimeInMillis();
        c.set(2026, java.util.Calendar.JANUARY, 1, 0, 0, 0);
        c.set(java.util.Calendar.MILLISECOND,0);
        long start=c.getTimeInMillis();
        List<Ledger.Entry> entries=Arrays.asList(
            new Ledger.Entry("1",true,500,"دخل","x",start),
            new Ledger.Entry("2",false,100,"عام","x",start-1));
        assertEquals(1,Ledger.currentMonth(entries,mid).size());
    }
}
