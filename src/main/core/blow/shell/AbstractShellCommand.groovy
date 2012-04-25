/*
 * Copyright (c) 2012. Paolo Di Tommaso
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

import blow.BlowSession

/**
 * Provide a base implementation for a Pilot shell command
 * 
 * @author Paolo Di Tommaso
 *
 */
abstract class AbstractShellCommand implements ShellCommand {

	/** 
	 * The owner shell instance which uses this command. This value will be 'injected' by
	 * the managing creator object
	 */
	BlowShell shell
	
	/**
	 * Accessor method to the 'blow' instance
	 */
	final BlowSession getSession() { shell.session }

	/**
	 * Define the command command name usedin the 'shell' interface,
	 * override to provide a custom command name/verb
	 */
	public String getName() { this.getClass().getSimpleName(); } 

	@Override
	public void parse(def args) {
		/* do nothing by default */
	}

	/**
	 * The help string showed in the shell 
	 */
	public String help() { null }
	
	
}
