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

package blow.command

import org.jclouds.ec2.domain.Volume
import blow.shell.Cmd
import blow.shell.BlowShell
import org.jclouds.ec2.domain.Snapshot
import blow.shell.Completion
import blow.shell.Opt
import blow.shell.Synopsis

/**
 *
 * @author Paolo Di Tommaso
 */
class StorageCommands  {

    // injected by the framework
    def BlowShell shell

    /**
     *
     * @param args
     */
    @Cmd("listvolumes")
    @Completion( { findVolumesCompletion(it) } )
    @Synopsis("Display the list of volumes in the current region")
    def void listVolumes( @Opt( argName='name') def name,  def args ) {

        def list = shell.session.blockStore.listVolumes()
        if( !list ) {
            println "(no volumes found)"

        }

        list.each() { Volume vol ->
            def size = "${vol.size} G".padLeft(5)
            println "${vol.id}; ${size}; ${vol.status}"
        }
    }

    /**
     * Find out the list of volumes starting with the provided string.
     * <p>
     * Note this method will is invoked through the {@link Completion} annotation
     *
     * @param cmdline The request volumes prefix
     * @return A list of volume IDs starting with the specified prefix
     */
    def findVolumesCompletion( String cmdline ) {
        
        def vols = shell.session.blockStore.listVolumes()
        if( cmdline ) {
            vols = vols.findAll { vol -> vol.id.startsWith(cmdline)} 
        }
        
        vols.collect { vol -> vol.id }
    } 


    @Cmd("listsnapshots")
    def void listSnapshots() {

        def list = shell.session.blockStore.listSnapshots()
        if( !list ) {
            println "(no volumes found)"

        }

        list.each() { Snapshot snapshot ->
            println "${snapshot.id} [${snapshot.volumeId}] ${snapshot.status}"
        }
    }
}
