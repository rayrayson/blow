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
import blow.shell.CommandCompletor

/**
 * Print out information details for the specified node
 *
 * @author Paolo Di Tommaso
 */
class NodeInfoCommand extends AbstractShellCommand implements CommandCompletor {
	def nodeId
	
	@Override
	public String getName() { "nodeinfo" }

	@Override
	public void parse(def args) {
		nodeId = args.head()
	}
	
	@Override
	public void invoke() {

        def node = findMatchingNode( nodeId )
        if( !node ) {
            println "(cannot find any information for node: '$nodeId')"
            return
        }

		session.printNodeInfo( node.getProviderId() )
	}

	@Override
	public String help() {
		"""\
		Shows the information details for the specified node
		"""
		.stripIndent()
	}

    @Override
    List<String> findOptions(String cmdline) {

        findMatchingAttributes( cmdline, "providerId" )

    }
}
