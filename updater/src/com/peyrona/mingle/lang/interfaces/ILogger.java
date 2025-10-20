package com.peyrona.mingle.lang.interfaces;

public interface ILogger {
    enum Level {
        INFO, WARNING, SEVERE
    }
    
    void log(Level level, String message);
    void log(Level level, Exception e, String message);
}