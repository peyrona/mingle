package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.ParseException;
import com.peyrona.mingle.lang.MingleException;
import static org.junit.Assert.*;
import org.junit.Test;

public class UtilJsonTest
{
    @Test
    public void testRemoveCommentsPreservesLineNumbers()
    {
        // Valid JSON with comments
        String validJson = "{\n"
                + "  \"key\": \"value\", # comment\n"
                + "  \"key2\": 1\n"
                + "}";
        assertNotNull( UtilJson.parse( validJson ) );

        // Invalid JSON to check error line number
        // Line 1: {
        // Line 2:   "a": 1, # comment
        // Line 3:   "b":
        // Line 4: }
        // The error is '}' at line 6 where a value was expected for "b"
        String json = "{\n"
                + "# Comment line 1\n"
                + "# Comment line 2\n"
                + "  \"a\": 1, # comment\n"
                + "  \"b\": \n"
                + "}";

        try
        {
            UtilJson.parse( json );
            fail( "Expected MingleException" );
        }
        catch( MingleException me )
        {
            Throwable cause = me.getCause();
            if( cause instanceof ParseException )
            {
                ParseException pe = (ParseException) cause;
                int line = pe.getLine();
                // If newline was swallowed, the whole thing would be on fewer lines.
                // Specifically line 2 and 3 would merge.
                // So '}' would be on line 3.
                // If newline preserved, '}' is on line 4.

                // Let's assert it is 4.
                assertEquals( "Error should be reported at line 6", 6, line );
            }
        }
    }
}
