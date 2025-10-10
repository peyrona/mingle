
package com.peyrona.mingle.controllers.modbus;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import java.util.HashMap;
import java.util.Map;

public class Test
{
//  "44.210.106.46:502"; address = 3024;
    public static void main(String[] args) throws Exception
    {
        Map<String, Object> deviceInit = new HashMap<>();
                            deviceInit.put( "uri"    , "44.210.106.46:502" );
                            deviceInit.put( "address", 1 );
                            deviceInit.put( "type"   , "int" );

        ModbusTcpClientWrapper mb = new ModbusTcpClientWrapper();
                               mb.set( "devicename", deviceInit, createListener() );
                               mb.read();

//        ModbusTcpClient4J2mod mbI  = new ModbusTcpClient4J2mod( "77.211.19.166", 1502, 1416,   "int", "ABCD", 0, null );
//        ModbusTcpClient4J2mod mbF1 = new ModbusTcpClient4J2mod( "77.211.19.166", 1502,   62, "float", "ABCD", 0, null );
////        ModbusTcpClient4J2mod mbF2 = new ModbusTcpClient4J2mod( "77.211.19.166", 1502,   62, "float", "ABCD", 0, null );
////        ModbusTcpClient4J2mod mbF3 = new ModbusTcpClient4J2mod( "77.211.19.166", 1502,   63, "float", "ABCD", 0, null );
////        ModbusTcpClient4J2mod mbF4 = new ModbusTcpClient4J2mod( "77.211.19.166", 1502,   64, "float", "ABCD", 0, null );
//
//        System.out.println( "["+ mbI.read() +']' );
//        System.out.printf(  "%.6f%n", (Float) mbF1.read() );
////        System.out.println( "["+ mbF2.read() +']' );
////        System.out.println( "["+ mbF3.read() +']' );
////        System.out.println( "["+ mbF4.read() +']' );
    }

    private static IController.Listener createListener()
    {
        return new IController.Listener()
                                {
                                    @Override
                                    public void onChanged(String deviceName, Object newValue)
                                    {
                                        System.out.println( deviceName +" changed: "+ newValue );
                                    }

                                    @Override
                                    public void onError(ILogger.Level level, String message, String device )
                                    {
                                        System.out.println( message );
                                    }
                                };
    }
}
