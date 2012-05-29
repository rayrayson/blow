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
import blow.shell.BlowShell
import blow.shell.Cmd
import groovy.util.logging.Slf4j

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

	@Cmd( summary = "Create and launch the cluster using the settings provided in the configuration file" )
	public void start() {
        if( session.dirty ) {
            log.info("Reloading session .. ")
            session = shell.useCluster( session.clusterName )
        }

        def size = session.conf.size
        def answer = shell.prompt("Please confirm that you want to start ${size} node(s) cluster [y/n]") { ['y','n'].contains(it) }

        if( answer == 'y' ) {

		    session.createCluster();
        }

	}



    @Cmd(summary="Terminate the current cluster")
    public void terminate() {

        def answer = shell.prompt("Please confirm that you want to terminate cluster named ${shell.session.clusterName} [y/n]") {
            ['y','n'].contains(it)
        }

        if( answer == 'y' ) {
            session.terminateCluster()
        }
    }

    @Cmd(summary="Shows the list of the current running clusters")
    def listclusters() {

        def clusters = shell.listClusters()
        if( clusters ) {
            clusters.each { println "${it}"  }
        }
        else {
            println "(no clusters available)"
        }

    }



    @Cmd(summary="Print the configuration settings for the current selected cluster")
    def conf() {
        println session.getConfString()
    }


}
