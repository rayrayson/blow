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

package blow.shell

import groovy.util.logging.Slf4j

/**
 * Provide a base implementation for a Pilot shell command
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
abstract class AbstractShellCommand implements ShellCommand {

    static final int LEFT_PADDING = 2


    /**
	 * Define the command command name used in the 'shell' interface,
	 * override to provide a custom command name/verb
	 */
	public String getName() { this.getClass().getSimpleName(); }

    /**
     * Override to parse the command arguments
     *
     * @param args A list of string containing the arguments as entered by the user after the command name
     * @return The args list itself
     */
    @Override
    public void parse( List<String> args ) {  }

    /**
     * @return One line description for the command
     */
    @Override
	public String getSummary() { null }


    @Override
    public String getHelp() {
        def result = new StringBuilder()

        /*
         * name
         */
        result.append("NAME") .append("\n")
        result.append("".padLeft(LEFT_PADDING)).append(getName())

        def summary = getSummary()
        if( summary ) {
            result.append(" -- ") .append(summary)
            result.append("\n")
        }

        return result.toString()
    }



    /**
     * Override to release resources allocated by the command
     */
    @Override
    public void free() { }


}
