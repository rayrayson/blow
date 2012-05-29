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

package blow.command

import blow.BlowSession
import blow.shell.Cmd

class PutFilesCommand  {

    def BlowSession session
    
    @Cmd(summary="Copy a file to the remote hostname")
	public void put( String fLocal, String fTarget ) {

		if( !fLocal ) {
			println "usage: put <local filename> [<targetHost path>]"
			return 
		}
		
		File sourcePath = new File(fLocal)
		String targetPath = fTarget ?: sourcePath.getName()
		
		if( !sourcePath.exists() ) {
			println "error: the specified file does not exist"	
			return
		}
		
		session.copyToNodes(sourcePath, targetPath)

	}



}
