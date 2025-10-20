package com.peyrona.mingle.lang.japi;

public class UtilCLI {
    private final String[] args;
    
    public UtilCLI(String[] args) {
        this.args = args;
    }
    
    public boolean hasOption(String option) {
        for (String arg : args) {
            if (arg.equals("-" + option)) {
                return true;
            }
        }
        return false;
    }
}