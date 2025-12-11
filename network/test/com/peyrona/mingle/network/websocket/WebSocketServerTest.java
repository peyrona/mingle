/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */
package com.peyrona.mingle.network.websocket;

import com.peyrona.mingle.lang.interfaces.network.INetServer;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author francisco
 */
public class WebSocketServerTest
{

    public WebSocketServerTest()
    {
    }

    @BeforeEach
    public void setUp()
    {
    }

    @AfterEach
    public void tearDown()
    {
    }

    @Test
    public void testStart()
    {
        System.out.println( "start" );

        String          sCfgAsJSON = "{\"host\":\"localhost\",\"port\":8080}";
        WebSocketServer instance   = new WebSocketServer();
        INetServer      result     = instance.start( sCfgAsJSON );

        assertNotNull( result );
        assertTrue( instance.isRunning() );

        instance.stop();
    }

    @Test
    public void testStop()
    {
        System.out.println( "stop" );

        String          sCfgAsJSON = "{\"host\":\"localhost\",\"port\":8081}";
        WebSocketServer instance   = new WebSocketServer();

        // Start first
        instance.start( sCfgAsJSON );
        assertTrue( instance.isRunning() );

        // Then stop
        INetServer result = instance.stop();

        assertNotNull( result );
        assertFalse( instance.isRunning() );
    }
}