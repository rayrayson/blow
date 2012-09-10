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

package blow.util

import blow.shell.BlowShell

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PromptHelper {

    /**
     * Just wait for an user console entry
     *
     * @return the entered text
     */
    def String prompt( String prompt = "", Closure<String> accept = null ) {

        def line
        while( true ) {
            line = BlowShell.console.readLine(prompt)
            if( !accept || accept.call(line) ) {
                break
            }
        }

        println ""
        return line
    }

    /**
     * Wait for an the user console input. Only the entries specified as the second
     * parameters will be accepted as valid.
     *
     * @param text The string value to show on the input prompt
     * @param options A list of valid entries that will accepted, otherwise
     * it will continue to prompt for an answer
     */
    def String prompt ( String text, List<String> options  ) {
        assert options, "You should provide at least one entry in the 'options' list parameter"

        def show = (text ?: "") + " [${options.join('/')}]"
        prompt( show ) { it in options }

    }

    def String promptYesOrNo( String query ) {
        prompt(query,['y','n'])
    }

}
