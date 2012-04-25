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

package blow.plugin

import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import blow.events.OnAfterClusterCreateEvent
import blow.events.OnAfterClusterTerminationEvent
import blow.events.OnBeforeClusterCreationEvent
import blow.events.OnBeforeClusterTerminationEvent
import blow.events.OnAfterNodeStartEvent
import blow.events.ONBeforeNodeStartEvent


@Slf4j
@Plugin("trace")
class TracePlugin {


	@Override
	public void execute() { }
	
	
	@Subscribe
	public void onBeforeCreate( OnBeforeClusterCreationEvent event ) {
		blow.plugin.TracePlugin.log.info(">> Before cluster create: ${event.clusterName}")
	}

	@Subscribe
	public void onAfterCreate( OnAfterClusterCreateEvent event ) {
		blow.plugin.TracePlugin.log.info(">> After cluster create: ${event.clusterName}")
	}

	
	@Subscribe
	public void onBeforeStart( ONBeforeNodeStartEvent event ) {
		blow.plugin.TracePlugin.log.info(">> Before start node(s): ${event.role}: ${event.numberOfNodes}")
	}

	@Subscribe
	public void onAfterStart( OnAfterNodeStartEvent event ) {
		blow.plugin.TracePlugin.log.info(">> After start node(s): ${event.role}: ${event.numberOfNodes}")
	}

	@Subscribe
	public void onBeforeTerminate( OnBeforeClusterTerminationEvent event ) {
		blow.plugin.TracePlugin.log.info(">> Before cluster terminate: ${event.clusterName}")
	}

	@Subscribe
	public void onAfterTermiante( OnAfterClusterTerminationEvent event ) {
		blow.plugin.TracePlugin.log.info(">> After cluster terminate: ${event.clusterName}")
	}
	
}
