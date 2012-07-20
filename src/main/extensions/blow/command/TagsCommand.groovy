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
import org.jclouds.aws.ec2.AWSEC2Client
import org.jclouds.aws.ec2.util.TagFilters

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TagsCommand {

    BlowSession session

    //@Cmd
    def void listTags() {

        def filter = [:]
        filter .put(TagFilters.FilterName.RESOURCE_TYPE, [TagFilters.ResourceType.INSTANCE] )

        def AWSEC2Client api = session .context.getProviderSpecificContext().getApi()
        def result = api.getTagServices().describeTagsInRegion(session.conf.regionId, filter)

        result.each { println it }

    }

}
