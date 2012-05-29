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

import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import blow.events.OnAfterClusterStartedEvent
import blow.events.OnAfterClusterTerminationEvent
import blow.events.OnBeforeClusterStartEvent
import blow.events.OnBeforeClusterTerminationEvent
import blow.events.OnAfterNodeLaunchEvent
import blow.events.OnBeforeNodeLaunchEvent


@Slf4j
@Operation("trace")
class TracePlugin {


	@Override
	public void execute() { }
	
	
	@Subscribe
	public void onBeforeCreate( OnBeforeClusterStartEvent event ) {
		blow.operation.TracePlugin.log.info(">> Before cluster create: ${event.clusterName}")
	}

	@Subscribe
	public void onAfterCreate( OnAfterClusterStartedEvent event ) {
		blow.operation.TracePlugin.log.info(">> After cluster create: ${event.clusterName}")
	}

	
	@Subscribe
	public void onBeforeStart( OnBeforeNodeLaunchEvent event ) {
		blow.operation.TracePlugin.log.info(">> Before start node(s): ${event.role}: ${event.numberOfNodes}")
	}

	@Subscribe
	public void onAfterStart( OnAfterNodeLaunchEvent event ) {
		blow.operation.TracePlugin.log.info(">> After start node(s): ${event.role}: ${event.numberOfNodes}")
	}

	@Subscribe
	public void onBeforeTerminate( OnBeforeClusterTerminationEvent event ) {
		blow.operation.TracePlugin.log.info(">> Before cluster terminate: ${event.clusterName}")
	}

	@Subscribe
	public void onAfterTermiante( OnAfterClusterTerminationEvent event ) {
		blow.operation.TracePlugin.log.info(">> After cluster terminate: ${event.clusterName}")
	}
	
}
