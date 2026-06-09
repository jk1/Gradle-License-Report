/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jk1.license.reader

/**
 * Parser + serializer for the OSGi "common header syntax" (OSGi Core, ch. 3).
 *
 *   header    ::= clause ( ',' clause )*
 *   clause    ::= name ( ';' name )* ( ';' parameter )*
 *   parameter ::= key '='  value      // attribute
 *              |  key ':=' value      // directive
 *   value     ::= token | '"' quoted '"'
 *
 * Multiple names before the parameters ("aliases") share one clause, e.g.
 *   org.foo;org.bar;version="1.0"
 * yields a single clause with names [org.foo, org.bar].
 *
 * Limitations (intentional, to match the strict spec / Equinox / Felix):
 *  - No backslash escaping inside quoted values. Standard OSGi has no way to
 *    represent a literal '"' inside a quoted value; bnd extends the syntax to
 *    allow it, this parser does not.
 *  - Surrounding whitespace around names, keys and values is trimmed.
 *  - Attribute/directive values containing a separator (, ; = :) or whitespace
 *    MUST be quoted in the input; on output they are re-quoted automatically.
 *
 * No external dependencies.
 */
class OsgiHeader {

    /** One clause: one or more names sharing a set of attributes and directives. */
    static class Clause {
        List<String> names = []
        Map<String, String> attributes = [:]   // insertion-ordered
        Map<String, String> directives = [:]
    }

    /** Quote-aware tokenizer: splits on the given separator chars, ignoring
     *  separators that fall inside double quotes. Quotes are stripped. */
    private static class QuotedTokenizer {
        private final String s
        private final String seps
        private int i = 0
        char separator = 0 as char   // separator that ended the last token (0 = end of input)

        QuotedTokenizer(String s, String seps) { this.s = s; this.seps = seps }

        String next() {
            def sb = new StringBuilder()
            while (i < s.length()) {
                char c = s.charAt(i)
                if (seps.indexOf((int) c) >= 0) {
                    separator = c; i++; return sb.toString()
                }
                if (c == ('"' as char)) {
                    i++
                    while (i < s.length() && s.charAt(i) != ('"' as char)) {
                        sb.append(s.charAt(i)); i++
                    }
                    i++ // skip closing quote
                    continue
                }
                sb.append(c); i++
            }
            separator = 0 as char
            sb.toString()
        }
    }

    /** Parse a header value into an ordered list of clauses. */
    static List<Clause> parse(String header) {
        def result = []
        if (!header?.trim()) return result

        def qt = new QuotedTokenizer(header, ';=,')
        char del
        while (true) {
            def clause = new Clause()

            String name = qt.next().trim()
            del = qt.separator
            if (name) clause.names << name

            while (del == (';' as char)) {
                String key = qt.next().trim()
                del = qt.separator
                if (del != ('=' as char)) {          // bare token => another name (alias)
                    if (key) clause.names << key
                    continue
                }
                String value = qt.next().trim()       // del was '=', read the value
                del = qt.separator
                if (key.endsWith(':')) {
                    clause.directives[key[0..-2].trim()] = value
                } else {
                    clause.attributes[key] = value
                }
            }

            if (clause.names || clause.attributes || clause.directives) result << clause
            if (del != (',' as char)) break
        }
        result
    }
}
