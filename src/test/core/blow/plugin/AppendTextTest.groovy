/*
 * Copyright (c) 2012, the authors.
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

package blow.plugin

import spock.lang.Specification

/**
 * @author Paolo Di Tommaso
 *
 */
class AppendTextTest extends Specification {

    def "test validation fail" () {

        when:
        new AppendText().validate()

        then:
        thrown( AssertionError )
    }

    def "test validation fail 2" () {

        when:
        new AppendText(to: "~/file" ).validate()

        then:
        thrown( AssertionError )
    }

    def "test validation fail 3" () {

        /*
         * the validator have to raise an exception when the specified file not exist
         */
        when:
        new AppendText(file: "/some/file" , to: "~/file" ).validate()

        then:
        thrown( AssertionError )

    }

    def "test validation OK" () {

        when:
        new AppendText(text: "content" , to: "~/file" ).validate()

        then:
        notThrown( AssertionError )

    }


    def "test validation OK 2" () {

        /*
        * The validator have to raise an exception when the specified file not exist
        * But we know that the ones below exists
        */
        when:
        new AppendText(file: "./src/test/extensions/blow/Command1.groovy" , to: "~/file" ).validate()

        then:
        notThrown( AssertionError )

    }

}
