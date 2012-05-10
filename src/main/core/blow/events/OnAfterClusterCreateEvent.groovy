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

package blow.events

import blow.BlowSession

/**
 * This event is sent after the cluster has been created
 * 
 * @author Paolo Di Tommaso
 *
 */
class OnAfterClusterCreateEvent {

	def BlowSession session
	
	/** The cluster name that has been started */
	def String clusterName
	
	/** The collection of nodes in the cluster */
	def nodes
	
}
