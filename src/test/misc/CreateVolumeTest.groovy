import com.google.common.collect.ImmutableSet
import com.google.inject.Module
import org.jclouds.ContextBuilder
import org.jclouds.aws.ec2.AWSEC2Client
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.ec2.services.ElasticBlockStoreClient
import org.jclouds.enterprise.config.EnterpriseConfigurationModule
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule
import org.jclouds.sshj.config.SshjSshClientModule

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

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

def accessKey = "AKIAIF3TUNLSJYG5UWQQ"
def secretKey = "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"

Properties props = new Properties();
props.setProperty("jclouds.ec2.ami-query", "");
props.setProperty("jclouds.ec2.cc-ami-query", "");

ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
        .credentials(accessKey,secretKey)
        .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule(), new SshjSshClientModule(), new EnterpriseConfigurationModule()))
        .overrides(props)
        .build(ComputeServiceContext.class);


try {
    ElasticBlockStoreClient ebs = ((AWSEC2Client) context.getProviderSpecificContext().getApi()).getElasticBlockStoreServices();
    ebs.createVolumeFromSnapshotInAvailabilityZone("eu-west-1a", 1, "snap-8b7ffbdd");
}
finally {
    context.close()
}
