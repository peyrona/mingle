package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.interfaces.IXprEval;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test class to reproduce the issue with temporal operations and logical AND
 */
public class TestDebug {
    
    public static void main(String[] args) throws InterruptedException {
        testTemporalAndIssue();
    }
    
    public static void testTemporalAndIssue() throws InterruptedException {
        System.out.println("=== Testing temporal operations with logical AND ===");
        
        // Test the problematic expression
        String expression = "(var1 == 5 AFTER 1000) && (var2 == 7 WITHIN 3000)";
        
        AtomicReference<Object> result = new AtomicReference<>();
        
        IXprEval eval = new NAXE().build(expression, result::set, null);
        
        System.out.println("Expression: " + expression);
        System.out.println("Is boolean: " + eval.isBoolean());
        System.out.println("Variables: " + eval.getVars());
        
        // Set initial values
        System.out.println("\n--- Setting var1 = 5 ---");
        eval.set("var1", 5);
        
        System.out.println("Current result: " + result.get());
        System.out.println("Is futureing: " + eval.isFutureing());
        
        // Wait for AFTER to complete
        Thread.sleep(1500);
        
        System.out.println("After 1500ms - Current result: " + result.get());
        System.out.println("Is futureing: " + eval.isFutureing());
        
        // Set var2 value
        System.out.println("\n--- Setting var2 = 7 ---");
        eval.set("var2", 7);
        
        System.out.println("Current result: " + result.get());
        System.out.println("Is futureing: " + eval.isFutureing());
        
        // Wait for WITHIN to complete
        Thread.sleep(2000);
        
        System.out.println("After 2000ms - Current result: " + result.get());
        System.out.println("Is futureing: " + eval.isFutureing());
        
        // Final wait
        Thread.sleep(2000);
        
        System.out.println("Final result: " + result.get());
        System.out.println("Expected: true");
    }
}