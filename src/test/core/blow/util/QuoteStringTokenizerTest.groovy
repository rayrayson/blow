/*
 * Copyright (c) 2012. Paolo Di Tommaso.
 *
 *   This file is part of Blow.
 *
 *   Blow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Blow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Blow.  If not, see <http://www.gnu.org/licenses/>.
 */

package blow.util

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class QuoteStringTokenizerTest extends Specification {

    def "test toknizer" () {

        when:
        QuoteStringTokenizer tkns = new QuoteStringTokenizer("a");

        then:
        tkns.next() == "a"

    }


    def "test more tokens" () {

        when:
        QuoteStringTokenizer tkns = new QuoteStringTokenizer("a bb ccc");

        then:
        tkns.next() == "a"
        tkns.next() == "bb"
        tkns.next() == "ccc"

    }

    def "test single quote" () {

        when:
        QuoteStringTokenizer tkns = new QuoteStringTokenizer("a 'bb ccc' d");

        then:
        tkns.next() == "a"
        tkns.next() == "bb ccc"
        tkns.next() == "d"

    }


    def "test double quote" () {

        when:
        QuoteStringTokenizer tkns = new QuoteStringTokenizer("a \"b c\" d");

        then:
        tkns.next() == "a"
        tkns.next() == "b c"
        tkns.next() == "d"

    }


    def "test nested quotes" () {

        when:
        QuoteStringTokenizer tkns = new QuoteStringTokenizer("a \"b '' c\" d; -z  ");

        then:
        tkns.next() == "a"
        tkns.next() == "b '' c"
        tkns.next() == "d;"
        tkns.next() == '-z'

    }
}
