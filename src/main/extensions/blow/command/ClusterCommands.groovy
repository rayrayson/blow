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

import blow.shell.AbstractShellCommand
import groovy.util.logging.Slf4j
import blow.shell.BlowShell
import blow.shell.Cmd
import blow.shell.Synopsis
import org.jclouds.compute.domain.NodeMetadata
import blow.BlowSession;

/**
 * Create a cluster using the underlying configuration
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
class ClusterCommands {

    def BlowShell shell
    def BlowSession session

	@Cmd
    @Synopsis("Create and launch the cluster using the settings provided in the configuration file")
	public void create() {
        def size = session.conf.size
        def answer = shell.prompt("Please confirm that you want to start ${size} node(s) [y/n]") { ['y','n'].contains(it) }

        if( answer == 'y' ) {
		    session.createCluster();
        }
	}



    @Cmd
    @Synopsis("Terminate the current cluster")
    public void terminate() {

        def answer = shell.prompt("Please confirm that you want to termiante cluster named ${shell.session.clusterName} [y/n]") {
            ['y','n'].contains(it)
        }

        if( answer == 'y' ) {
            session.terminateCluster()
        }
    }

    @Cmd
    @Synopsis("Shows the list of the current running clusters")
    def listclusters() {

        def clusters = session.listClusters()
        if( clusters ) {
            clusters.each { println "${it}"  }
        }
        else {
            println "(no clusters available)"
        }

    }



    @Cmd
    @Synopsis("Print the configuration settings for the current selected cluster")
    def conf() {
        println session.getConfString()
    }


}
