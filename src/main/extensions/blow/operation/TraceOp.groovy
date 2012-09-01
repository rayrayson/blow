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

package blow.operation

import blow.BlowSession
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import blow.events.*

@Slf4j
@Operation("trace")
class TraceOp {

    private BlowSession session

	@Override
	public void execute() { }
	
	
	@Subscribe
	public void onBeforeCreate( OnBeforeClusterStartEvent event ) {
		TraceOp.log.info(">> Before cluster create: ${event.clusterName}")
	}

	@Subscribe
	public void onAfterCreate( OnAfterClusterStartedEvent event ) {
		TraceOp.log.info(">> After cluster create: ${event.clusterName}")
	}

	
	@Subscribe
	public void onBeforeStart( OnBeforeNodeLaunchEvent event ) {
		TraceOp.log.info(">> Before start node(s): ${event.role}: ${event.numberOfNodes}")
	}

	@Subscribe
	public void onAfterStart( OnAfterNodeLaunchEvent event ) {
		TraceOp.log.info(">> After start node(s): ${event.role}: ${event.numberOfNodes}")
	}

	@Subscribe
	public void onBeforeTerminate( OnBeforeClusterTerminationEvent event ) {
		TraceOp.log.info(">> Before cluster terminate: ${event.clusterName}")
	}

	@Subscribe
	public void onAfterTermiante( OnAfterClusterTerminationEvent event ) {
		TraceOp.log.info(">> After cluster terminate: ${event.clusterName}")
	}
	
}
