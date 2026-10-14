package com.sarfa.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TransactionsFlowTest {
    private View findTransaction(View root, String snippet) {
        if (root.getContentDescription()!=null && root.getContentDescription().toString().contains(snippet)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++) {
                View found=findTransaction(group.getChildAt(i),snippet);
                if(found!=null)return found;
            }
        }
        return null;
    }
    private void voice(ActivityScenario<MainActivity> scenario, String words) {
        scenario.onActivity(a -> {
            Intent i=new Intent();
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList(words)));
            a.onActivityResult(101,Activity.RESULT_OK,i);
        });
    }
    private JSONArray stored(ActivityScenario<MainActivity> scenario) {
        final JSONArray[] result={null};
        scenario.onActivity(a -> {
            try {
                result[0]=new JSONArray(a.getSharedPreferences("sarfa",0).getString("entries","[]"));
            } catch(Exception e){ throw new AssertionError(e); }
        });
        return result[0];
    }
    @Test public void voiceNeedsExplicitConfirmationThenPersistsBothDirectionsAndDeletes() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a -> a.getSharedPreferences("sarfa",0).edit().clear().commit());
            voice(scenario,"15 ريال عشاء لحمودي");
            assertEquals(0,stored(scenario).length());
            scenario.onActivity(a -> a.confirmationDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            assertEquals(1,stored(scenario).length());
            assertEquals(1500,stored(scenario).getJSONObject(0).getLong("halalas"));
            voice(scenario,"دخل 100 ريال راتب");
            scenario.onActivity(a -> a.confirmationDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            assertEquals(2,stored(scenario).length());
            assertTrue(stored(scenario).getJSONObject(0).getBoolean("income"));
            voice(scenario,"صرف 40 ريال تسوق");
            scenario.onActivity(a -> a.confirmationDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            assertEquals(2,stored(scenario).length());
            scenario.onActivity(a -> {
                View row = findTransaction(a.getWindow().getDecorView(), "عشاء لحمودي");
                assertNotNull("Expense row must exist",row);
                row.performClick();
                assertNotNull("Delete dialog must open",a.deletionDialog);
                a.deletionDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            assertEquals(1,stored(scenario).length());
            assertEquals(10000,stored(scenario).getJSONObject(0).getLong("halalas"));
        }
    }
    @Test public void activityExportsActualPdfToSelectedFile() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                File file=new File(a.getFilesDir(),"test-export-from-activity.pdf");
                Intent result=new Intent().setData(Uri.fromFile(file));
                a.onActivityResult(102, Activity.RESULT_OK, result);
                assertTrue("PDF export did not write file",file.exists());
                assertTrue("PDF export produced an empty file",file.length()>1000);
            });
        }
    }
}
