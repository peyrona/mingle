package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.interfaces.ILogger;

public class UtilSys {
    private static ILogger logger = new ConsoleLogger();
    
    public static void setLogger(String name, Object config) {}
    
    public static ILogger getLogger() {
        return logger;
    }
    
    public static Object getConfig() {
        return new Object();
    }
    
    public static String getTmpDir() {
        return System.getProperty("java.io.tmpdir");
    }
    
    private static class ConsoleLogger implements ILogger {
        @Override
        public void log(Level level, String message) {
            System.out.println("[" + level + "] " + message);
        }
        
        @Override
        public void log(Level level, Exception e, String message) {
            System.out.println("[" + level + "] " + message);
            if (e != null) {
                e.printStackTrace();
            }
        }
    }
}