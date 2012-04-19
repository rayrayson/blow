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

package blow.events

import org.jclouds.compute.options.TemplateOptions

import blow.BlowSession;

/**
 * This event is before before a node (or a group of nodes is started)
 * 
 * @author Paolo Di Tommaso
 *
 */

class ONBeforeNodeStartEvent {

	def BlowSession session
	
	/** The cluster name that is going to be started */
	def String clusterName
	
	/** The role of this node(s) in the cluster (usually one of 'master' or 'worker') */
	def String role
	
	/** The number of nodes that are going to be started */
	def int numberOfNodes
	
	/** The template options used for the node configuration */
	def TemplateOptions options
	
}
