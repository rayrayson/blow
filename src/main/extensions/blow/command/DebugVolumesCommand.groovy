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

package blow.command;


import org.jclouds.ec2.domain.*
import blow.shell.AbstractShellCommand

public class DebugVolumesCommand extends AbstractShellCommand {

	public String getName() { "debugvolume" }

	@Override
	public void invoke() {

		def snapshotId = null
		def volumeId = "vol-8814f7e4"
		def device = "/dev/sdh"
		def instanceId = "i-6bc02d0c"
		
		def vols = shell.session.getBlockStore().ebs.describeVolumesInRegion( shell.session.conf.regionId ).findAll {
			Volume vol -> vol.status == Volume.Status.IN_USE  \
			&& ( snapshotId==null || snapshotId == vol.snapshotId  )  \
			&& ( volumeId==null || volumeId == vol.id  )  \
			&& vol.attachments?.find { Attachment attach -> attach.instanceId == instanceId && attach.device == device }
		} 
	
		vols.each {  println it } 
					
	}
	
	
	
	
}
