
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.lexer.Language;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This class has a bunch of utility methods to deal with common communications
 * issues.
 * <p>
 * About IP Addresses:
 * <ul>
 * <li>Any address in the range 127.xxx.xxx.xxx is a "loopback" address. It is
 *     only visible to "this" host.
 * <li>Any address in the range 192.168.xxx.xxx is a private (aka site local)
 *     IP address. These are reserved for use within an organization. The same
 *     applies to 10.xxx.xxx.xxx addresses, and 172.16.xxx.xxx through
 *     172.31.xxx.xxx.
 * <li>Addresses in the range 169.254.xxx.xxx are link local IP addresses.
 *     These are reserved for use on a single network segment.
 * <li>Addresses in the range 224.xxx.xxx.xxx through 239.xxx.xxx.xxx are
 *     multicast addresses.
 * <li>The address 255.255.255.255 is the broadcast address.
 * <li>Anything else should be a valid public point-to-point IPv4 address.
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class UtilComm
{
    // All ports are above 49152 are safe for custom apps.

    public static final int MINGLE_DEFAULT_SOCKET_PORT        = 55880;
    public static final int MINGLE_DEFAULT_SOCKET_PORT_SSL    = 55881;
    public static final int MINGLE_DEFAULT_WEBSOCKET_PORT     = 55882;
    public static final int MINGLE_DEFAULT_WEBSOCKET_PORT_SSL = 55883;

    public static final int TCP_PORT_MIN_ALLOWED              = 1;
    public static final int UDP_PORT_MIN_ALLOWED              = 0;
    public static final int TCP_PORT_MAX_ALLOWED              = 0xFFFF;
    public static final int UDP_PORT_MAX_ALLOWED              = 0xFFFF;
    public static final int PORT_USER_MIN_ALLOWED             =   1024;
    public static final int PORT_USER_MIN_RECOMMENDED         = 0xC000;   // == 49152

    public static final int PORT_FTP_DEFAULT                  =  21;
    public static final int PORT_SSH_DEFAULT                  =  22;
    public static final int PORT_SMTP_DEFAULT                 =  25;
    public static final int PORT_POP3_DEFAULT                 = 110;
    public static final int PORT_IMAP_DEFAULT                 = 143;

    public static final short ALLOW_IP_LOCAL                  = 1;    // 192.168.7.9
    public static final short ALLOW_IP_SUBNET                 = 2;    // 192.168.7.*
    public static final short ALLOW_IP_INTRANET               = 3;    // 192.168.*.*
    public static final short ALLOW_IP_ANY                    = 4;    // *.*.*.*

    public static enum  Protocol { file, http, https }

    //------------------------------------------------------------------------//
    private UtilComm() {}  // Avoid this class instances creation
    //------------------------------------------------------------------------//

    /**
     * Checks if the given port number is a valid UDP port.
     *
     * @param port The port number to validate.
     * @return true if the port is within the valid UDP range (0-65535); false otherwise.
     */
    public static boolean isValidPort( int port )
    {
        return ((port >= UDP_PORT_MIN_ALLOWED) && (port <= UDP_PORT_MAX_ALLOWED));
    }

    /**
     * Checks if the given port number is a valid TCP port.
     *
     * @param port The port number to validate.
     * @return true if the port is within the valid TCP range (1-65535); false otherwise.
     */
    public static boolean isValidPort4TCP( int port )
    {
        return ((port >= TCP_PORT_MIN_ALLOWED) && (port <= TCP_PORT_MAX_ALLOWED));
    }

    /**
     * Checks if the given port number is a valid UDP port.
     *
     * @param port The port number to validate.
     * @return true if the port is within the valid UDP range (0-65535); false otherwise.
     */
    public static boolean isValidPort4UDP( int port )
    {
        return ((port >= UDP_PORT_MIN_ALLOWED) && (port <= UDP_PORT_MAX_ALLOWED));
    }

    /**
     * Ensures the given port number is within the valid TCP range.
     * If the port is outside the valid range, it is clamped to the nearest valid value.
     *
     * @param port The port number to validate and adjust.
     * @return A port number within the valid TCP range (1-65535).
     */
    public static int makeValidPort4TCP( int port )
    {
        if( port < TCP_PORT_MIN_ALLOWED || port > TCP_PORT_MAX_ALLOWED )
        {
            port = UtilUnit.setBetween(TCP_PORT_MIN_ALLOWED, port, TCP_PORT_MAX_ALLOWED );
        }

        return port;
    }

    /**
     * Ensures the given port number is within the valid UDP range.
     * If the port is outside the valid range, it is clamped to the nearest valid value.
     *
     * @param port The port number to validate and adjust.
     * @return A port number within the valid UDP range (0-65535).
     */
    public static int makeValidPort4UDP( int port )
    {
        if( port < UDP_PORT_MIN_ALLOWED || port > UDP_PORT_MAX_ALLOWED )
        {
            port = UtilUnit.setBetween(UDP_PORT_MIN_ALLOWED, port, UDP_PORT_MAX_ALLOWED );
        }

        return port;
    }

    /**
     * Checks if the given port is within the recommended user port range.
     * <p>
     * In strict mode, the port must be 49152 or higher (ephemeral ports).
     * In non-strict mode, the port must be 1024 or higher (user ports).
     *
     * @param port The port number to validate.
     * @param strict If true, requires port >= 49152; if false, requires port >= 1024.
     * @return true if the port is within the recommended user range; false otherwise.
     */
    public static boolean isRecommendedPort( int port, boolean strict )
    {
        if( strict )
        {
            return ((port >= PORT_USER_MIN_RECOMMENDED )
                &&
                (port <= TCP_PORT_MAX_ALLOWED));
        }

        return ((port >= PORT_USER_MIN_ALLOWED )
                &&
                (port <= TCP_PORT_MAX_ALLOWED));
    }

    /**
     * Parses a client scope string and returns the corresponding scope constant.
     * <p>
     * Supported values (case-insensitive):
     * <ul>
     * <li>"local" - returns ALLOW_IP_LOCAL
     * <li>"subnet" - returns ALLOW_IP_SUBNET
     * <li>"intranet" - returns ALLOW_IP_INTRANET
     * <li>"any" - returns ALLOW_IP_ANY
     * </ul>
     *
     * @param sAllow The scope string to parse.
     * @return The corresponding scope constant.
     * @throws IllegalArgumentException if the string does not contain a valid scope value.
     */
    public static short clientScope( String sAllow )
    {
        if( UtilStr.contains( "local"   , sAllow ) )  return ALLOW_IP_LOCAL;
        if( UtilStr.contains( "subnet"  , sAllow ) )  return ALLOW_IP_SUBNET;
        if( UtilStr.contains( "intranet", sAllow ) )  return ALLOW_IP_INTRANET;
        if( UtilStr.contains( "any"     , sAllow ) )  return ALLOW_IP_ANY;

        throw new IllegalArgumentException( sAllow +": invalid value for 'allow'. Only 'local', 'intranet' or 'any' are accepted.");
    }

    /**
     * Checks if the given client address is allowed based on the specified scope.
     *
     * @param nScope The scope to check against (ALLOW_IP_LOCAL, ALLOW_IP_SUBNET, ALLOW_IP_INTRANET, or ALLOW_IP_ANY).
     * @param addr The client address to validate.
     * @return true if the address is allowed within the specified scope; false otherwise.
     * @throws SocketException if an I/O error occurs while checking the address.
     * @throws IllegalArgumentException if nScope is not a valid scope constant.
     */
    public static boolean isClientAllowed( short nScope, InetAddress addr ) throws SocketException
    {
        switch( nScope )
        {
            case ALLOW_IP_ANY     :  return true;
            case ALLOW_IP_INTRANET:  return isIntranet( addr );
            case ALLOW_IP_SUBNET  :  return isSubnet(   addr );
            case ALLOW_IP_LOCAL   :  return isLocal(    addr );
            default:
                throw new IllegalArgumentException( "Scope = "+ nScope );
        }
    }

    /**
     * Returns the Protocol enum instance to be used to read a file; or null when it is a malformed URI.
     *
     * @param sURI
     * @return The Protocol enum instance to be used to read a file; or null when it is a malformed URI.
     */
    public static Protocol getFileProtocol( String sURI )
    {
        if( sURI == null )
            return null;

        sURI = sURI.trim();

        if( sURI.length() < 8 )
            return null;

        String s = sURI.substring( 0,8 ).toLowerCase();     // 'substring' to save CPU

        if( s.startsWith( "file:"    ) )  { return Protocol.file;  }    // Normally appears as : "file://", but also (less common) as "file:" (v.g. when doing: new File("myfile).toURI())
        if( s.startsWith( "http://"  ) )  { return Protocol.http;  }
        if( s.startsWith( "https://" ) )  { return Protocol.https; }

        return null;
    }

    /**
     * Returns the schema from the given URI, or null if the URI has no schema.
     * <p>
     * Example: "https://peyrona.com:8080" returns "https://"
     *
     * @param sURI The URI to extract the schema from.
     * @return The schema (lowercased with "://") or null if no schema is present.
     */
    public static String getSchema( String sURI )
    {
        return getSchema( sURI, null );
    }

    /**
     * Passing "https://peyrona.com:8080", returns "https://" (lowered-case always).
     * <p>
     * Note: schema always has a "://" as part of it.
     *
     * @param sURI To extract the schema from.
     * @param sDefault What to return when passed URI has no schema.
     * @return The schema or sDefault if passed URI has no schema.
     */
    public static String getSchema( String sURI, String sDefault )
    {
        if( sURI == null )
            return sDefault;

        sURI = sURI.trim();

        int ndx = sURI.indexOf( "//" );

        if( ndx < 0 )
            return sDefault;

        return sURI.substring( 0, ndx + 2 ).toLowerCase();    // +2 to add "//"
    }

    /**
     * Passing "https://peyrona.com:8080", returns "peyrona.com"
     *
     * @param sURI To extract the host from.
     * @return The host or null if passed URI has no host.
     */
    /**
     * Returns the host from the given URI, or null if the URI has no host.
     * <p>
     * Example: "https://peyrona.com:8080" returns "peyrona.com"
     *
     * @param sURI The URI to extract the host from.
     * @return The host or null if no host is present.
     */
    public static String getHost( String sURI )
    {
        return getHost( sURI, null );
    }

    /**
     * Passing "https://peyrona.com:8080", returns "peyrona.com"
     *
     * @param sURI To extract the host from.
     * @param sDefault What to return when passed URI has no host.
     * @return The host or sDefault if passed URI has no host.
     */
    public static String getHost( String sURI, String sDefault )
    {
        if( sURI == null )
            return null;

        sURI = sURI.trim();

        int nStart = sURI.indexOf( "//" );
        int nEnd   = sURI.lastIndexOf( ':' );

        if( nStart == -1 )   // There is no schema
        {
            nStart = 0;

            if( nEnd == -1 )
                nEnd = sURI.length();
         // else
         //     Current nEnd is valid
        }
        else
        {
            nStart += 2;           // +2 to add the "//"

            if( nEnd < nStart )    // The only ':' is part of the schema
            {
                nEnd = sURI.length();
            }
            else                   // There is more than one ':'
            {
                nEnd = sURI.substring( nStart + 1 ).lastIndexOf( ':' );

                if( nEnd == -1 )
                    nEnd = sURI.length();
            }
        }

        return sURI.substring( nStart, nEnd );
    }

    /**
     * Returns the port from the given string, or -1 if no port is present.
     * <p>
     * The port can be a numeric value or a time suffix (e.g., "100ms").
     *
     * @param s The string to extract the port from.
     * @return The port number or -1 if no port is present.
     */
    public static int getPort( String s )
    {
        return getPort( s, -1 );
    }

    /**
     * Passing "https://peyrona.com:8080", returns 8080
     *
     * @param s To extract the port
     * @param nDefault What to return when passed URI has no port.
     * @return The port or nDefault if passed URI has no port.
     */
    public static int getPort( String s, int nDefault )
    {
        if( s == null )
            return nDefault;

        s = s.trim();

        int ndx = s.lastIndexOf( ':' );

        if( ndx < 0 )
            return nDefault;

        if( UtilStr.isLastChar( s, ':' ) )
            return nDefault;

        s = s.substring( ndx + 1 );

        if( Language.isNumber( s ) )
            return UtilType.toInteger( s );

        if( (s.length() > 1) && Language.isTimeSuffix( UtilStr.getLastChar( s ) ) )
            return UtilType.toInteger( UtilUnit.toMillis( s ) );

        return nDefault;
    }

    /**
     *
     * @param hostname
     * @return A boolean indicating if the address is reachable.
     */
    public static boolean isReachable( String hostname )
    {
        return isReachable( hostname, 750 );
    }

    /**
     *
     * @param hostname
     * @param timeout The time, in milliseconds, before the call aborts
     * @return A boolean indicating if the address is reachable.
     */
    public static boolean isReachable( String hostname, int timeout )
    {
        try
        {
            return isReachable( InetAddress.getByName( hostname ), timeout );
        }
        catch( UnknownHostException ex )
        {
            return false;
        }
    }

    /**
     * Checks if the given InetAddress is reachable with a default timeout of 750ms.
     *
     * @param hostname The InetAddress to check.
     * @return true if the address is reachable; false otherwise.
     */
    public static boolean isReachable( InetAddress hostname )
    {
        return isReachable( hostname, 750 );
    }

    /**
     * Checks if the given InetAddress is reachable within the specified timeout.
     *
     * @param hostname The InetAddress to check.
     * @param timeout The timeout in milliseconds before the check is aborted.
     * @return true if the address is reachable; false otherwise.
     */
    public static boolean isReachable( InetAddress hostname, int timeout )
    {
        try
        {
            return hostname.isReachable( timeout );
        }
        catch( IOException ex )
        {
            return false;
        }
    }

    /**
     * Checks if an InetAddress is a local address.
     *
     * @param address The InetAddress to check.
     * @return true if the address is a loopback, wildcard, link-local, or private address; false otherwise.
     */
    public static boolean isLocal( InetAddress address )
    {
        if( address == null )
            return false;

        return address.isLoopbackAddress()  ||
               address.isAnyLocalAddress()  ||
               address.isLinkLocalAddress() ||
               isPrivateAddress( address );
    }

    /**
     * Determines if the provided IP address is an intranet address.
     * An intranet address is an IP address that belongs to the local intranet network  (192.168.*.*).
     *
     * @param address the {@link InetAddress} to be checked.
     * @return {@code true} if the address is an intranet address; {@code false} otherwise.
     * @throws SocketException if an I/O error occurs.
     */
    public static boolean isIntranet( InetAddress address ) throws SocketException
    {
        if( address == null )
            return false;

        if( isLocal( address ) )
            return true;

        Set<InetAddress> localIntranetIPs = getLocalIPs();

        for( InetAddress ia : localIntranetIPs )
        {
            boolean bEquals = false;

                 if( ia instanceof Inet4Address && address instanceof Inet4Address )  bEquals = isIntranetIPv4( (Inet4Address) ia, (Inet4Address) address );
            else if( ia instanceof Inet6Address && address instanceof Inet6Address )  bEquals = isIntranetIPv6( (Inet6Address) ia, (Inet6Address) address );

            if( bEquals )
                return true;
        }

        return false;
    }

    /**
     * Determines if the provided IP address is a subnet address.
     * A subnet address is an IP address that belongs to the same local subanet network (192.168.7.*).
     *
     * @param address the {@link InetAddress} to be checked.
     * @return {@code true} if the address is an intranet address; {@code false} otherwise.
     * @throws SocketException if an I/O error occurs.
     */
    public static boolean isSubnet( InetAddress address ) throws SocketException
    {
        if( address == null )
            return false;

        if( address.isLoopbackAddress() ||
            address.isAnyLocalAddress() ||
            address.isLinkLocalAddress() )    // isPrivateAddress( address ) works for Intranet but not for subnet
        {
            return true;
        }

        Set<InetAddress> localIntranetIPs = getLocalIPs();

        for( InetAddress ia : localIntranetIPs )
        {
            boolean bEquals = false;

                 if( ia instanceof Inet4Address && address instanceof Inet4Address )  bEquals = isSubnetIPv4( (Inet4Address) ia, (Inet4Address) address );
            else if( ia instanceof Inet6Address && address instanceof Inet6Address )  bEquals = isSubnetIPv6( (Inet6Address) ia, (Inet6Address) address );

            if( bEquals )
                return true;
        }

        return false;
    }

    /**
     * Returns this machine IP as viewed by other computers inside the Intranet.
     *
     * @return This machine IP as viewed by other computers inside the Intranet.
     *
     * @throws SocketException
     */
    public static Set<InetAddress> getLocalIPs() throws SocketException
    {
        Enumeration<NetworkInterface> enumera = NetworkInterface.getNetworkInterfaces();
        Set<InetAddress>              locals  = new HashSet<>();                          // This is a Set because duplicates are automatically handled
                                      locals.add( InetAddress.getLoopbackAddress() );

        try
        {
            locals.add( InetAddress.getLocalHost() );
        }
        catch( UnknownHostException uhe )
        {
            // Nothing to do
        }

        if( enumera == null )
            return locals;     // Return the set with just the loopback address

        while( enumera.hasMoreElements() )
        {
            NetworkInterface         ni = enumera.nextElement();
            Enumeration<InetAddress> ee = ni.getInetAddresses();

            while( ee.hasMoreElements() )
            {
                InetAddress address = ee.nextElement();

                if( address.isSiteLocalAddress() || isUniqueLocalAddress( address ) )
                {
                    locals.add( address );
//                    System.out.println( address +" --> isAnyLocalAddress="+ address.isAnyLocalAddress() +", "
//                                                +"isLinkLocalAddress="+ address.isLinkLocalAddress()    +", "
//                                                +"isLoopbackAddress=" + address.isLoopbackAddress()     +", "
//                                                +"isSiteLocalAddress="+ address.isSiteLocalAddress() );
                }
            }
        }

        return Collections.unmodifiableSet( locals );    // For thread safety
    }

    /**
     * Composes an IPv4 address by merging a base IP address with a template containing wildcards.
     * The template uses '*' as a wildcard character which will be replaced with the corresponding octet from the parent IP.
     * <p>
     * Examples:
     * <pre>
     * compose( "192.168.1.1", "*" )         --> "192.168.1.1"
     * compose( "192.168.1.1", "*.5" )       --> "192.168.1.5"
     * compose( "192.168.1.1", "5.*" )       --> "192.168.5.1"
     * compose( "192.168.1.1", "*.3.5" )     --> "192.168.3.5"
     * compose( "192.168.1.1", "*.100.3.5" ) --> "192.100.3.5"
     * compose( "192.168.1.1", "212.*.4.5" ) --> "212.168.4.5"
     * </pre>
     * The method also handles port numbers. If either parameter contains a port, the resulting address will include the port from the template if present, otherwise from the parent.
     *
     * @param parent A valid IPv4 address (with optional port) used to fill in wildcard positions.
     * @param template An IPv4 address template (with optional port) containing wildcards ('*') to be replaced.
     * @return The composed IPv4 address with wildcards replaced, including port if present.
     * @throws IllegalArgumentException if either parameter is null or empty, if parent is not a valid IPv4 address, if template is malformed, or if the resulting port is out of bounds.
     */
    public static String compose( String parent, String template )
    {
        if( UtilStr.isEmpty( parent ) || UtilStr.isEmpty( template ) )
        {
            throw new IllegalArgumentException( "Both arguments must be not null, neither empty." );
        }

        parent   = parent.trim();
        template = template.trim();

        if( UtilStr.countChar( parent, '.' ) != 3 )
        {
            throw new IllegalArgumentException( "First parameter is not a valid IP v4." );
        }

        if( "*".equals( template ) )   // Si el 2º param es '*'
        {
            return parent;             // se devuelve el 1º
        }

        if( template.indexOf( '*' ) == -1 )    // Si el 2º param no contiene comodines ('*')
        {
            return template;                   // se devuelve el propio 2º param
        }

        if( template.indexOf( '.' ) == -1 )
        {
            throw new IllegalArgumentException( "Second parameter must have at least one '.'" );
        }

        // Inicializo valores
        int nPortParent = Math.max( getPort( parent   ), -1 );    // No puede ser x < -1
        int nPortTempla = Math.max( getPort( template ), -1 );    // No puede ser x < -1

        if( nPortParent > -1 )
        {
            int index = parent.indexOf( ":" );     // Este es el separador del puerto
            parent = (index > -1 ? parent.substring( 0, index ) : parent );
        }

        if( nPortTempla > -1 )
        {
            int index = template.indexOf( ":" );     // Este es el separador del puerto
            template = (index > -1 ? template.substring( 0, index ) : template );
        }

        String[] asParentIP  = parent.split( "\\." );
        String[] asTemplate  = template.split( "\\." );

        // Esto puede hacerse con un bucle, pero al haber tan pocos casos,
        // queda más claro con un switch
        switch( asTemplate.length )
        {
         // case 1: break;  -> para este salta una IllegalArgumentException
            case 2: asTemplate = new String[] { "*", "*", asTemplate[0], asTemplate[1] };           break;
            case 3: asTemplate = new String[] { "*", asTemplate[0], asTemplate[1], asTemplate[2] }; break;
         // case 3: break;  -> este no necesita rellenar nada
        }

        // Ahora sustituyo los '*' por sus valores en el parent
        for( int n = 0; n < asParentIP.length; n++ )
        {
            if( asTemplate[n].equals( "*" ) )
            {
                asTemplate[n] = asParentIP[n];
            }
        }

        int nPort = ((nPortTempla > -1) ? nPortTempla
                                        : (nPortParent > -1) ? nPortParent
                                                             : -1);

        if( ! isValidPort4UDP( nPort ) )    // UDP es más amplio pq permite 0
        {
            throw new IllegalArgumentException( "Port is out or bounds" );
        }

        return asTemplate[0] +"."+ asTemplate[1] +"."+ asTemplate[2] +"."+ asTemplate[3] +
               ((nPort > - 1) ? ":"+ nPort : "");
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Checks if an InetAddress is a private address.
     *
     * @param address The InetAddress to check.
     * @return true if the address is in a private IP range; false otherwise.
     */
    private static boolean isPrivateAddress( InetAddress address )
    {
        if( address == null )
            return false;

        byte[] bytes = address.getAddress();

        if( bytes.length == 4 )          // IPv4
        {
            int firstOctet  = bytes[0] & 0xFF;
            int secondOctet = bytes[1] & 0xFF;

            return (firstOctet == 10)
                    ||  // 10.0.0.0/8
                    (firstOctet == 172 && (secondOctet >= 16 && secondOctet <= 31))
                    ||  // 172.16.0.0/12
                    (firstOctet == 192 && secondOctet == 168); // 192.168.0.0/16
        }
        else if( bytes.length == 16 )    // IPv6
        {
            // Check for private IPv6 ranges (e.g., fc00::/7)
            return (bytes[0] == (byte) 0xfc || bytes[0] == (byte) 0xfd);
        }

        return false;
    }

    /**
     * Checks if two IPv4 addresses are on the same intranet (last two groups match).
     *
     * @param ia1 The first IPv4 address.
     * @param ia2 The second IPv4 address.
     * @return true if the first two octets of the addresses match; false otherwise.
     */
    private static boolean isIntranetIPv4( Inet4Address ia1, Inet4Address ia2 )
    {
        if( ia1 == null || ia2 == null )
            return false;

        if( Objects.equals( ia1, ia2 ) )
            return true;

        byte[] octets1 = ia1.getAddress();
        byte[] octets2 = ia2.getAddress();

        return octets1[0] == octets2[0] &&
               octets1[1] == octets2[1];
    }

    /**
     * Checks if two IPv6 addresses are on the same intranet (last two groups match).
     *
     * @param ia1 The first IPv6 address.
     * @param ia2 The second IPv6 address.
     * @return true if the first six groups of the addresses match; false otherwise.
     */
    private static boolean isIntranetIPv6( Inet6Address ia1, Inet6Address ia2 )
    {
        if( ia1 == null || ia2 == null )
            return false;

        if( Objects.equals( ia1, ia2 ) )
            return true;

        byte[] bytes1 = ia1.getAddress();
        byte[] bytes2 = ia2.getAddress();

        // Compare the first 12 bytes (6 groups) of the IPv6 address
        for( int n = 0; n < 12; n++ )
        {
            if( bytes1[n] != bytes2[n] )
                return false;
        }

        return true;
    }

    /**
 * Checks if two IPv4 addresses are on the same subnet (first three octets match).
 *
 * @param ia1 The first IPv4 address.
 * @param ia2 The second IPv4 address.
 * @return true if the first three octets of the addresses match; false otherwise.
 */
    private static boolean isSubnetIPv4(Inet4Address ia1, Inet4Address ia2)
    {
        if( ia1 == null || ia2 == null )
            return false;

        if( Objects.equals( ia1, ia2 ) )
            return true;

        byte[] octets1 = ia1.getAddress();
        byte[] octets2 = ia2.getAddress();

        return octets1[0] == octets2[0] &&
               octets1[1] == octets2[1] &&
               octets1[2] == octets2[2];
    }

    /**
     * Checks if two IPv6 addresses are on the same subnet. For IPv6, we consider addresses to be on the same subnet if they share the first seven groups (14 bytes) of their addresses.
     *
     * @param ia1 The first IPv6 address.
     * @param ia2 The second IPv6 address.
     * @return true if the first seven groups of the addresses match; false otherwise.
     */
    private static boolean isSubnetIPv6(Inet6Address ia1, Inet6Address ia2)
    {
        if( ia1 == null || ia2 == null )
            return false;

        if( Objects.equals( ia1, ia2 ) )
            return true;

        byte[] bytes1 = ia1.getAddress();
        byte[] bytes2 = ia2.getAddress();

        // Compare the first 14 bytes (7 groups) of the IPv6 address

        for( int n = 0; n < 14; n++ )
        {
            if( bytes1[n] != bytes2[n] )
                return false;
        }

        return true;
    }

    /**
     * Checks if an InetAddress is a unique local IPv6 address (ULA).
     * <p>
     * Unique local addresses are in the fc00::/7 range (fc00:: to fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff).
     *
     * @param address The InetAddress to check.
     * @return true if the address is a unique local IPv6 address; false otherwise.
     */
    // Helper method to check for IPv6 unique local addresses (ULA)
    private static boolean isUniqueLocalAddress( InetAddress address )
    {
        if( address instanceof Inet6Address )
        {
            byte[] bytes = address.getAddress();

            return (bytes[0] & 0xFE) == 0xFC;    // Check if the address is in the fc00::/7 range (unique local addresses)
        }

        return false;
    }

//    public static void main( String[] as ) throws UnknownHostException
//    {
//        String ipAddress1 = "192.168.1.8";
//        String ipAddress2 = "192.169.2.9";
//
//        InetAddress inetAddress1 = InetAddress.getByName( ipAddress1 );
//        InetAddress inetAddress2 = InetAddress.getByName( ipAddress2 );
//
//        System.out.println( isIntranetIPv4( (Inet4Address) inetAddress1, (Inet4Address) inetAddress2 ) );
//    }

//    public static void main( String[] as ) throws UnknownHostException
//    {
//        String ipAddress1 = "2001:db8:85a3:0:0:8a2e:0370:7334";
//        String ipAddress2 = "2001:db8:85a3:0:0:8a2d:0371:7335";
//
//        InetAddress inetAddress1 = InetAddress.getByName( ipAddress1 );
//        InetAddress inetAddress2 = InetAddress.getByName( ipAddress2 );
//
//        System.out.println( isIntranetIPv6( (Inet6Address) inetAddress1, (Inet6Address) inetAddress2 ) );
//    }
}