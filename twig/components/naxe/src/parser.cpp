/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/** \file parser.cpp
 * Une expression text -> AST parser (see parser.hpp for the pipeline).
 */
#include "naxe/parser.hpp"

#include <cassert>
#include <cstdlib>
#include <cstring>

#include "naxe/operators.hpp"

namespace naxe
{
    namespace
    {
        /** Hex value of a hex digit character, or -1 if `c` isn't one. */
        int hexDigit( char c )
        {
            if( c >= '0' && c <= '9' ) return c - '0';
            if( c >= 'a' && c <= 'f' ) return c - 'a' + 10;
            if( c >= 'A' && c <= 'F' ) return c - 'A' + 10;
            return -1;
        }

        /** UTF-8 encodes a Basic Multilingual Plane codepoint (<= 0xFFFF). */
        size_t utf8Encode( unsigned cp, char * out )
        {
            if( cp <= 0x7F )
            {
                out[ 0 ] = static_cast<char>( cp );
                return 1;
            }
            if( cp <= 0x7FF )
            {
                out[ 0 ] = static_cast<char>( 0xC0 | ( cp >> 6 ) );
                out[ 1 ] = static_cast<char>( 0x80 | ( cp & 0x3F ) );
                return 2;
            }
            out[ 0 ] = static_cast<char>( 0xE0 | ( cp >> 12 ) );
            out[ 1 ] = static_cast<char>( 0x80 | ( ( cp >> 6 ) & 0x3F ) );
            out[ 2 ] = static_cast<char>( 0x80 | ( cp & 0x3F ) );
            return 3;
        }
    } // namespace

    std::string_view jsonUnescape( Arena & arena, std::string_view text )
    {
        char * buf = static_cast<char *>( arena.alloc( text.size() ) );
        assert( ( buf != nullptr || text.empty() ) && "arena exhausted unescaping expression text" );

        size_t out = 0;

        for( size_t i = 0; i < text.size(); ++i )
        {
            const char c = text[ i ];

            if( c == '\\' && i + 1 < text.size() )
            {
                const char n = text[ ++i ];

                switch( n )
                {
                    case '"':  buf[ out++ ] = '"';  break;
                    case '\\': buf[ out++ ] = '\\'; break;
                    case '/':  buf[ out++ ] = '/';  break;
                    case 'b':  buf[ out++ ] = '\b'; break;
                    case 'f':  buf[ out++ ] = '\f'; break;
                    case 'n':  buf[ out++ ] = '\n'; break;
                    case 'r':  buf[ out++ ] = '\r'; break;
                    case 't':  buf[ out++ ] = '\t'; break;

                    case 'u':
                        if( i + 4 < text.size() )
                        {
                            const int d0 = hexDigit( text[ i + 1 ] ), d1 = hexDigit( text[ i + 2 ] ),
                                      d2 = hexDigit( text[ i + 3 ] ), d3 = hexDigit( text[ i + 4 ] );
                            if( d0 >= 0 && d1 >= 0 && d2 >= 0 && d3 >= 0 )
                            {
                                const unsigned cp = static_cast<unsigned>( ( d0 << 12 ) | ( d1 << 8 ) | ( d2 << 4 ) | d3 );
                                out += utf8Encode( cp, buf + out );
                                i += 4;
                            }
                        }
                        break;

                    default: buf[ out++ ] = n; break; // unknown escape: keep literally
                }
            }
            else
            {
                buf[ out++ ] = c;
            }
        }

        return std::string_view( buf, out );
    }

    namespace
    {
        /** Records the first error in `err`; subsequent errors are ignored. */
        void setErr( ParseError & err, const char * msg )
        {
            if( err.hasError )
            {
                return; // first error wins
            }
            err.hasError = true;
            err.message = msg;
        }

        // ---- Token model ----------------------------------------------------------

        enum class TokKind
        {
            Number, String, Boolean, Name, Function,
            Operator, OperatorUnary,
            ParenOpen, ParenClose, Comma,
            Reserved,   // AFTER / WITHIN
            Quantifier  // ANY / ALL / NONE
        };

        struct Tok
        {
            TokKind kind;
            const char * data;
            size_t len;
        };

        constexpr size_t kMaxTokens = 128;
        constexpr uint32_t kMaxGroupMembers = 32;

        bool isOperatorChar( char c )
        {
            return std::strchr( "+-*/%^=!><&|~:,?", c ) != nullptr;
        }

        bool isNameChar( char c )
        {
            return c == '_' || ( c >= 'a' && c <= 'z' ) || ( c >= 'A' && c <= 'Z' ) || ( c >= '0' && c <= '9' );
        }

        bool isDigitOrDot( char c )
        {
            return ( c >= '0' && c <= '9' ) || c == '.';
        }

        Tok readOperatorTok( const char * buf, size_t len, size_t & pos )
        {
            const size_t start = pos;
            const char c0 = buf[ pos++ ];
            const char c1 = ( pos < len ) ? buf[ pos ] : '\0';
            size_t combinedLen = 1;

            switch( c0 )
            {
                case '=': case '!': if( c1 == '=' ) combinedLen = 2; break;
                case '<': if( c1 == '=' || c1 == '<' ) combinedLen = 2; break;
                case '>': if( c1 == '=' || c1 == '>' || c1 == '<' ) combinedLen = 2; break;
                case '&': if( c1 == '&' ) combinedLen = 2; break;
                case '|': if( c1 == '|' || c1 == '&' ) combinedLen = 2; break;
                case '?': if( c1 == '>' || c1 == '<' || c1 == '=' || c1 == '!' ) combinedLen = 2; break;
                default: break;
            }

            if( combinedLen == 2 )
            {
                pos++;
                if( c0 == '?' && pos < len )
                {
                    if( c1 == '<' && buf[ pos ] == '>' ) { pos++; combinedLen = 3; }
                    else if( c1 == '!' && buf[ pos ] == '=' ) { pos++; combinedLen = 3; }
                }
            }

            return Tok{ TokKind::Operator, buf + start, combinedLen };
        }

        bool readStringTok( char * buf, size_t len, size_t & pos, Tok & out )
        {
            const size_t start = pos;
            size_t outPos = pos;
            pos++; // skip opening '"'

            while( pos < len && buf[ pos ] != '"' )
            {
                if( buf[ pos ] == '\\' && pos + 1 < len )
                {
                    const char esc = buf[ pos + 1 ];
                    char decoded;
                    switch( esc )
                    {
                        case 'n':  decoded = '\n'; break;
                        case 'r':  decoded = '\r'; break;
                        case 't':  decoded = '\t'; break;
                        case '\\': decoded = '\\'; break;
                        case '"':  decoded = '"';  break;
                        default:   decoded = esc;  break;
                    }
                    buf[ outPos++ ] = decoded;
                    pos += 2;
                }
                else
                {
                    buf[ outPos++ ] = buf[ pos ];
                    pos++;
                }
            }

            if( pos >= len )
            {
                return false; // unclosed string
            }

            pos++; // skip closing '"'
            out = Tok{ TokKind::String, buf + start, outPos - start };
            return true;
        }

        /** Pass 1: tokenize the buffer into raw lexemes. */
        bool lexRaw( char * buf, size_t len, Tok * out, size_t cap, size_t & count, ParseError & err )
        {
            count = 0;
            size_t pos = 0;

            while( pos < len )
            {
                const char c = buf[ pos ];

                if( static_cast<unsigned char>( c ) <= 32 ) { pos++; continue; }

                if( count >= cap ) { setErr( err, "expression too long (token capacity)" ); return false; }

                if( isDigitOrDot( c ) && ( c != '.' || ( pos + 1 < len && buf[ pos + 1 ] >= '0' && buf[ pos + 1 ] <= '9' ) ) )
                {
                    const size_t start = pos;
                    while( pos < len && ( isDigitOrDot( buf[ pos ] ) || buf[ pos ] == '_' ) ) pos++;
                    out[ count++ ] = Tok{ TokKind::Number, buf + start, pos - start };
                }
                else if( c == '"' )
                {
                    Tok tok;
                    if( ! readStringTok( buf, len, pos, tok ) ) { setErr( err, "unclosed string literal" ); return false; }
                    out[ count++ ] = tok;
                }
                else if( c == '(' || c == ')' )
                {
                    out[ count++ ] = Tok{ ( c == '(' ) ? TokKind::ParenOpen : TokKind::ParenClose, buf + pos, 1 };
                    pos++;
                }
                else if( c == ',' )
                {
                    out[ count++ ] = Tok{ TokKind::Comma, buf + pos, 1 };
                    pos++;
                }
                else if( isOperatorChar( c ) )
                {
                    out[ count++ ] = readOperatorTok( buf, len, pos );
                }
                else if( isNameChar( c ) && ! ( c >= '0' && c <= '9' ) )
                {
                    const size_t start = pos;
                    while( pos < len && isNameChar( buf[ pos ] ) ) pos++;
                    const size_t nameLen = pos - start;

                    if( nameLen == 4 && std::memcmp( buf + start, "true", 4 ) == 0 )
                        out[ count++ ] = Tok{ TokKind::Boolean, buf + start, nameLen };
                    else if( nameLen == 5 && std::memcmp( buf + start, "false", 5 ) == 0 )
                        out[ count++ ] = Tok{ TokKind::Boolean, buf + start, nameLen };
                    else if( ( nameLen == 5 && std::memcmp( buf + start, "AFTER", 5 ) == 0 ) ||
                             ( nameLen == 6 && std::memcmp( buf + start, "WITHIN", 6 ) == 0 ) )
                        out[ count++ ] = Tok{ TokKind::Reserved, buf + start, nameLen };
                    else if( ( nameLen == 3 && std::memcmp( buf + start, "ANY", 3 ) == 0 ) ||
                             ( nameLen == 3 && std::memcmp( buf + start, "ALL", 3 ) == 0 ) ||
                             ( nameLen == 4 && std::memcmp( buf + start, "NONE", 4 ) == 0 ) )
                        out[ count++ ] = Tok{ TokKind::Quantifier, buf + start, nameLen };
                    else
                        out[ count++ ] = Tok{ TokKind::Name, buf + start, nameLen };
                }
                else
                {
                    setErr( err, "unrecognized character in expression" );
                    return false;
                }
            }

            return true;
        }

        /** Pass 2: detect unary +/-/! and reclassify Name '(' as Function. */
        void classify( const Tok * raw, size_t rawCount, Tok * out, size_t & count )
        {
            count = 0;

            for( size_t i = 0; i < rawCount; ++i )
            {
                Tok t = raw[ i ];

                if( t.kind == TokKind::Operator && t.len == 1 &&
                    ( t.data[ 0 ] == '+' || t.data[ 0 ] == '-' || t.data[ 0 ] == '!' ) )
                {
                    bool isUnary;
                    if( i == 0 )
                    {
                        isUnary = true;
                    }
                    else
                    {
                        const TokKind prevKind = raw[ i - 1 ].kind;
                        isUnary = ( prevKind == TokKind::ParenOpen || prevKind == TokKind::Comma ||
                                    prevKind == TokKind::Operator || prevKind == TokKind::OperatorUnary );
                    }

                    if( isUnary )
                    {
                        if( t.data[ 0 ] == '+' )
                        {
                            continue; // unary '+' is a no-op
                        }
                        out[ count++ ] = Tok{ TokKind::OperatorUnary, t.data, t.len };
                        continue;
                    }
                }

                if( t.kind == TokKind::Name )
                {
                    const bool nextIsOpenParen = ( i + 1 < rawCount ) && ( raw[ i + 1 ].kind == TokKind::ParenOpen );
                    out[ count++ ] = Tok{ nextIsOpenParen ? TokKind::Function : TokKind::Name, t.data, t.len };
                    continue;
                }

                out[ count++ ] = t;
            }
        }

        constexpr Tok kTokParenOpen  { TokKind::ParenOpen,    "(",  1 };
        constexpr Tok kTokParenClose { TokKind::ParenClose,   ")",  1 };
        constexpr Tok kTokOr         { TokKind::Operator,     "||", 2 };
        constexpr Tok kTokAnd        { TokKind::Operator,     "&&", 2 };
        constexpr Tok kTokNot        { TokKind::OperatorUnary,"!",  1 };

        /** Expands ANY/ALL/NONE group quantifiers into explicit OR/AND chains. */
        bool expandQuantifiers( const Tok * in, size_t inCount, Tok * out, size_t outCap,
                                size_t & outCount, GroupResolverFn resolver, void * resolverCtx,
                                ParseError & err )
        {
            outCount = 0;

            auto emit = [ & ]( Tok t ) -> bool
            {
                if( outCount >= outCap ) { setErr( err, "expression too complex" ); return false; }
                out[ outCount++ ] = t;
                return true;
            };

            for( size_t i = 0; i < inCount; ++i )
            {
                if( in[ i ].kind != TokKind::Quantifier )
                {
                    if( ! emit( in[ i ] ) ) return false;
                    continue;
                }

                const bool isAny = in[ i ].len == 3 && std::memcmp( in[ i ].data, "ANY", 3 ) == 0;
                const bool isNone = in[ i ].len == 4; // else ALL

                if( i + 1 >= inCount || in[ i + 1 ].kind != TokKind::Name )
                {
                    setErr( err, "ANY/ALL/NONE must be followed by a group name" );
                    return false;
                }
                const Tok groupTok = in[ i + 1 ];

                if( resolver == nullptr )
                {
                    setErr( err, "group quantifier used but no group resolver installed" );
                    return false;
                }

                std::string_view members[ kMaxGroupMembers ];
                const uint32_t memberCount =
                    resolver( resolverCtx, std::string_view( groupTok.data, groupTok.len ), members, kMaxGroupMembers );

                if( memberCount == 0 )
                {
                    setErr( err, "unknown or empty group" );
                    return false;
                }

                const size_t condStart = i + 2;
                if( condStart >= inCount || in[ condStart ].kind != TokKind::Operator )
                {
                    setErr( err, "expected a comparison after group name" );
                    return false;
                }

                size_t depth = 0;
                size_t condEnd = condStart + 1;
                while( condEnd < inCount )
                {
                    const TokKind k = in[ condEnd ].kind;

                    if( k == TokKind::ParenOpen ) { depth++; condEnd++; continue; }
                    if( k == TokKind::ParenClose )
                    {
                        if( depth == 0 ) break;
                        depth--; condEnd++; continue;
                    }
                    if( depth == 0 && ( k == TokKind::Reserved || k == TokKind::Quantifier || k == TokKind::Comma ) )
                        break;
                    if( depth == 0 && k == TokKind::Operator && in[ condEnd ].len == 2 &&
                        ( std::memcmp( in[ condEnd ].data, "&&", 2 ) == 0 || std::memcmp( in[ condEnd ].data, "||", 2 ) == 0 ) )
                        break;

                    condEnd++;
                }

                if( condEnd <= condStart + 1 )
                {
                    setErr( err, "expected a value after comparison operator" );
                    return false;
                }

                if( isNone && ! emit( kTokNot ) ) return false;
                if( ! emit( kTokParenOpen ) ) return false;

                for( uint32_t m = 0; m < memberCount; ++m )
                {
                    if( m > 0 && ! emit( isAny || isNone ? kTokOr : kTokAnd ) ) return false;

                    if( ! emit( kTokParenOpen ) ) return false;
                    if( ! emit( Tok{ TokKind::Name, members[ m ].data(), members[ m ].size() } ) ) return false;
                    for( size_t k = condStart; k < condEnd; ++k )
                        if( ! emit( in[ k ] ) ) return false;
                    if( ! emit( kTokParenClose ) ) return false;
                }

                if( ! emit( kTokParenClose ) ) return false;

                i = condEnd - 1;
            }

            return true;
        }

        // ---- Shunting-yard ---------------------------------------------------------

        struct OpInfo { int precedence; bool leftAssoc; };

        constexpr int kPrecSend = 80, kPrecUnary = 60, kPrecPower = 45, kPrecMul = 30,
                      kPrecAdd = 20, kPrecCompare = 10, kPrecEquality = 7, kPrecTemporal = 5,
                      kPrecAnd = 4, kPrecOr = 2;

        bool opInfo( const char * text, size_t len, OpInfo & out )
        {
            if( len == 1 )
            {
                switch( text[ 0 ] )
                {
                    case ':': out = { kPrecSend, true }; return true;
                    case '^': out = { kPrecPower, true }; return true;
                    case '*': case '/': case '%': out = { kPrecMul, true }; return true;
                    case '+': case '-': out = { kPrecAdd, true }; return true;
                    case '>': case '<': out = { kPrecCompare, true }; return true;
                }
                return false;
            }

            if( len == 2 )
            {
                if( std::memcmp( text, "==", 2 ) == 0 || std::memcmp( text, "!=", 2 ) == 0 )
                { out = { kPrecEquality, true }; return true; }
                if( std::memcmp( text, ">=", 2 ) == 0 || std::memcmp( text, "<=", 2 ) == 0 )
                { out = { kPrecCompare, true }; return true; }
                if( std::memcmp( text, "&&", 2 ) == 0 ) { out = { kPrecAnd, true }; return true; }
                if( std::memcmp( text, "||", 2 ) == 0 || std::memcmp( text, "|&", 2 ) == 0 )
                { out = { kPrecOr, true }; return true; }
                if( std::memcmp( text, "?>", 2 ) == 0 || std::memcmp( text, "?<", 2 ) == 0 ||
                    std::memcmp( text, "?=", 2 ) == 0 )
                { out = { kPrecCompare, true }; return true; }
                return false;
            }

            if( len == 3 )
            {
                if( std::memcmp( text, "?!=", 3 ) == 0 || std::memcmp( text, "?<>", 3 ) == 0 )
                { out = { kPrecCompare, true }; return true; }
                return false;
            }

            return false;
        }

        int stackTopPrecedence( const Tok & top )
        {
            if( top.kind == TokKind::OperatorUnary )
            {
                return kPrecUnary;
            }
            if( top.kind == TokKind::Reserved )
            {
                return kPrecTemporal;
            }
            OpInfo info;
            opInfo( top.data, top.len, info );
            return info.precedence;
        }

        bool toRPN( const Tok * infix, size_t n, Tok * rpn, size_t rpnCap, size_t & rpnCount, ParseError & err )
        {
            Tok opStack[ kMaxTokens ];
            size_t sp = 0;
            rpnCount = 0;

            auto emit = [ & ]( Tok t ) -> bool
            {
                if( rpnCount >= rpnCap ) { setErr( err, "expression too complex" ); return false; }
                rpn[ rpnCount++ ] = t;
                return true;
            };

            for( size_t i = 0; i < n; ++i )
            {
                const Tok t = infix[ i ];

                switch( t.kind )
                {
                    case TokKind::Number:
                    case TokKind::String:
                    case TokKind::Boolean:
                    case TokKind::Name:
                        if( ! emit( t ) ) return false;
                        break;

                    case TokKind::Function:
                        if( sp >= kMaxTokens ) { setErr( err, "expression too complex" ); return false; }
                        opStack[ sp++ ] = t;
                        break;

                    case TokKind::Comma:
                        while( sp > 0 && opStack[ sp - 1 ].kind != TokKind::ParenOpen )
                            if( ! emit( opStack[ --sp ] ) ) return false;
                        if( sp == 0 ) { setErr( err, "unexpected ','" ); return false; }
                        break;

                    case TokKind::Operator:
                    {
                        OpInfo info;
                        if( ! opInfo( t.data, t.len, info ) )
                        {
                            setErr( err, "unsupported operator" );
                            return false;
                        }

                        while( sp > 0 &&
                               ( opStack[ sp - 1 ].kind == TokKind::Operator || opStack[ sp - 1 ].kind == TokKind::OperatorUnary ) )
                        {
                            const int topPrec = stackTopPrecedence( opStack[ sp - 1 ] );
                            const bool shouldPop = info.leftAssoc ? ( info.precedence <= topPrec ) : ( info.precedence < topPrec );
                            if( ! shouldPop ) break;
                            if( ! emit( opStack[ --sp ] ) ) return false;
                        }

                        if( sp >= kMaxTokens ) { setErr( err, "expression too complex" ); return false; }
                        opStack[ sp++ ] = t;
                        break;
                    }

                    case TokKind::OperatorUnary:
                        if( sp >= kMaxTokens ) { setErr( err, "expression too complex" ); return false; }
                        opStack[ sp++ ] = t;
                        break;

                    case TokKind::ParenOpen:
                        if( i > 0 && infix[ i - 1 ].kind == TokKind::Function )
                            if( ! emit( t ) ) return false;
                        if( sp >= kMaxTokens ) { setErr( err, "expression too complex" ); return false; }
                        opStack[ sp++ ] = t;
                        break;

                    case TokKind::ParenClose:
                        while( sp > 0 && opStack[ sp - 1 ].kind != TokKind::ParenOpen )
                            if( ! emit( opStack[ --sp ] ) ) return false;
                        if( sp == 0 ) { setErr( err, "mismatched parentheses" ); return false; }
                        --sp;
                        if( sp > 0 && opStack[ sp - 1 ].kind == TokKind::Function )
                            if( ! emit( opStack[ --sp ] ) ) return false;
                        break;

                    case TokKind::Reserved:
                    {
                        while( sp > 0 &&
                               ( opStack[ sp - 1 ].kind == TokKind::Operator ||
                                 opStack[ sp - 1 ].kind == TokKind::OperatorUnary ||
                                 opStack[ sp - 1 ].kind == TokKind::Reserved ) &&
                               kPrecTemporal <= stackTopPrecedence( opStack[ sp - 1 ] ) )
                        {
                            if( ! emit( opStack[ --sp ] ) ) return false;
                        }
                        if( sp >= kMaxTokens ) { setErr( err, "expression too complex" ); return false; }
                        opStack[ sp++ ] = t;
                        break;
                    }

                    case TokKind::Quantifier:
                        setErr( err, "internal error: unexpanded group quantifier" );
                        return false;
                }
            }

            while( sp > 0 )
            {
                if( opStack[ sp - 1 ].kind == TokKind::ParenOpen ) { setErr( err, "mismatched parentheses" ); return false; }
                if( ! emit( opStack[ --sp ] ) ) return false;
            }

            return true;
        }

        // ---- RPN -> AST ------------------------------------------------------------

        /** Parses a numeric token into an Int (no '.') or Float (has '.') Value. */
        bool numberToValue( const char * text, size_t len, Value & out )
        {
            char tmp[ 64 ];
            if( len >= sizeof( tmp ) ) return false;

            size_t outLen = 0;
            bool hasDot = false;
            for( size_t i = 0; i < len; ++i )
            {
                const char c = text[ i ];
                if( c == '_' ) continue; // strip digit-group separators
                if( c == '.' ) hasDot = true;
                tmp[ outLen++ ] = c;
            }
            tmp[ outLen ] = '\0';

            char * end = nullptr;
            if( hasDot )
            {
                const float f = std::strtof( tmp, &end );
                if( end == tmp ) return false;
                out = Value::makeFloat( f );
            }
            else
            {
                const long long ll = std::strtoll( tmp, &end, 10 );
                if( end == tmp ) return false;
                out = Value::makeInt( static_cast<int64_t>( ll ) );
            }
            return true;
        }

        ASTNode * rpnToAst( Arena & arena, const Tok * rpn, size_t n, ParseError & err )
        {
            ASTNode * nodeStack[ kMaxTokens ];
            size_t nsp = 0;

            size_t argMarkers[ kMaxTokens ];
            size_t amsp = 0;

            for( size_t i = 0; i < n; ++i )
            {
                const Tok t = rpn[ i ];

                switch( t.kind )
                {
                    case TokKind::Number:
                    {
                        Value v;
                        if( ! numberToValue( t.data, t.len, v ) ) { setErr( err, "invalid number literal" ); return nullptr; }
                        nodeStack[ nsp++ ] = astLiteral( arena, v );
                        break;
                    }

                    case TokKind::String:
                        nodeStack[ nsp++ ] = astLiteral( arena, Value::makeString( std::string_view( t.data, t.len ) ) );
                        break;

                    case TokKind::Boolean:
                        nodeStack[ nsp++ ] = astLiteral( arena, Value::makeBool( t.len == 4 ) ); // "true"=4
                        break;

                    case TokKind::Name:
                        nodeStack[ nsp++ ] = astVariable( arena, std::string_view( t.data, t.len ) );
                        break;

                    case TokKind::ParenOpen:
                        if( amsp >= kMaxTokens ) { setErr( err, "expression too complex" ); return nullptr; }
                        argMarkers[ amsp++ ] = nsp;
                        break;

                    case TokKind::Function:
                    {
                        const size_t marker = ( amsp > 0 ) ? argMarkers[ --amsp ] : nsp;
                        const uint32_t argCount = static_cast<uint32_t>( nsp - marker );

                        ASTNode ** args = nullptr;
                        if( argCount > 0 )
                        {
                            args = static_cast<ASTNode **>( arena.alloc( sizeof( ASTNode * ) * argCount ) );
                            assert( args != nullptr && "arena exhausted building function args" );
                            for( uint32_t k = 0; k < argCount; ++k ) args[ k ] = nodeStack[ marker + k ];
                        }

                        nsp = marker;
                        nodeStack[ nsp++ ] = astCall( arena, std::string_view( t.data, t.len ), args, argCount );
                        break;
                    }

                    case TokKind::OperatorUnary:
                    {
                        if( nsp < 1 ) { setErr( err, "missing operand for unary operator" ); return nullptr; }
                        ASTNode * operand = nodeStack[ --nsp ];
                        const AstOp op = ( t.data[ 0 ] == '!' ) ? AstOp::Not : AstOp::Neg;
                        nodeStack[ nsp++ ] = astUnary( arena, op, operand );
                        break;
                    }

                    case TokKind::Operator:
                    {
                        if( nsp < 2 ) { setErr( err, "missing operand(s) for operator" ); return nullptr; }
                        ASTNode * right = nodeStack[ --nsp ];
                        ASTNode * left  = nodeStack[ --nsp ];

                        if( t.len == 1 && t.data[ 0 ] == ':' )
                        {
                            if( right->kind != AstNodeKind::FunctionCall )
                            {
                                setErr( err, "expected a method call after ':'" );
                                return nullptr;
                            }
                            nodeStack[ nsp++ ] = astMethodCall( arena, left, right->funcName, right->args, right->argCount );
                        }
                        else
                        {
                            AstOp op;
                            if( ! resolveOperator( t.data, t.len, op ) )
                            {
                                setErr( err, "unsupported operator" );
                                return nullptr;
                            }
                            nodeStack[ nsp++ ] = astBinary( arena, op, left, right );
                        }
                        break;
                    }

                    case TokKind::Reserved:
                    {
                        if( nsp < 2 ) { setErr( err, "missing operand(s) for AFTER/WITHIN" ); return nullptr; }
                        ASTNode * right = nodeStack[ --nsp ];
                        ASTNode * left  = nodeStack[ --nsp ];
                        const AstOp op = ( t.len == 5 ) ? AstOp::After : AstOp::Within;
                        nodeStack[ nsp++ ] = astBinary( arena, op, left, right );
                        break;
                    }

                    default:
                        setErr( err, "unexpected token" );
                        return nullptr;
                }
            }

            if( nsp != 1 )
            {
                setErr( err, "invalid expression" );
                return nullptr;
            }

            return nodeStack[ 0 ];
        }
    } // namespace

    ASTNode * parseExpression( Arena & arena, std::string_view text, ParseError & err,
                               GroupResolverFn groupResolver, void * groupResolverCtx )
    {
        err = ParseError{};

        if( text.empty() ) { setErr( err, "empty expression" ); return nullptr; }

        const std::string_view unescaped = jsonUnescape( arena, text );

        // const_cast is safe: jsonUnescape() just freshly allocated this arena buffer.
        Tok raw[ kMaxTokens ];
        size_t rawCount = 0;
        if( ! lexRaw( const_cast<char *>( unescaped.data() ), unescaped.size(), raw, kMaxTokens, rawCount, err ) )
        {
            return nullptr;
        }

        if( rawCount == 0 ) { setErr( err, "empty expression" ); return nullptr; }

        Tok infix[ kMaxTokens ];
        size_t infixCount = 0;
        classify( raw, rawCount, infix, infixCount );

        Tok expanded[ kMaxTokens ];
        size_t expandedCount = 0;
        if( ! expandQuantifiers( infix, infixCount, expanded, kMaxTokens, expandedCount,
                                 groupResolver, groupResolverCtx, err ) )
        {
            return nullptr;
        }

        Tok rpn[ kMaxTokens ];
        size_t rpnCount = 0;
        if( ! toRPN( expanded, expandedCount, rpn, kMaxTokens, rpnCount, err ) )
        {
            return nullptr;
        }

        if( rpnCount == 0 ) { setErr( err, "empty expression" ); return nullptr; }

        return rpnToAst( arena, rpn, rpnCount, err );
    }
} // namespace naxe
