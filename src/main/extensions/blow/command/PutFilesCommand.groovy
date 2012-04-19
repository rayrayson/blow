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

package blow.command

import blow.shell.AbstractShellCommand;

class PutFilesCommand extends AbstractShellCommand {

	@Override
	public String getName() {
		return "put";
	}

	@Override
	public void invoke() {

		def args = params.split(" ");
		if( !args || !args[0] ) { 
			println "usage: put <local filename> [<targetHost path>]"
			return 
		}
		
		File source = new File(args[0])
		String target = args.length>1 ? args[1] : source.getAbsolutePath() 
		
		if( !source.exists() ) {
			println "error: the specified file does not exist"	
			return
		}
		
		session.copyToNodes(source, target)

	}

	@Override
	public String help() {
		// TODO Auto-generated method stub
		return null;
	}

}
