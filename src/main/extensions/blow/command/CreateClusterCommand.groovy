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
import groovy.util.logging.Slf4j;

/**
 * Create a cluster using the underlying configuration
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
class CreateClusterCommand extends AbstractShellCommand {

	@Override
	public String getName() { "create" }

	@Override
	public void parse(def args) { }

	@Override
	public void invoke() {
        def size = shell.session.conf.size
        def answer = shell.prompt("Please confirm that you want to start ${size} node(s) [y/n]") { ['y','n'].contains(it) }

        if( answer == 'y' ) {
		    session.createCluster();
        }
	}

	@Override
	public String help() {

		"""\
		Create and launch the cluster using the settings provided in the configuration file
		"""
		.stripIndent()
		
	}

}
