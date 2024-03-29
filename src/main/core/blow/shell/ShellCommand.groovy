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

/**
 * Shell command have to implement this interface
 */
public interface ShellCommand {

	/**
	 * Define the shell command name
	 * 
	 * @return the string to be entered into the shell to invoke this command
	 */
	String getName();

	/**
	 * Parse the command arguments provided by the user
	 * 
	 * @param args
     * @return An object representing the parsed command line arguments
	 */
	void parse( List<String> args )
	
	/**
	 * Run the command
	 */
	void invoke();

    /**
     * @return One line description for the command
     */
    String getSummary()

    /**
     * @return The help string for this command
     */
    String getHelp()

    /**
     * Destroy the command and release all associated resources
     */
    void free();


	
}
