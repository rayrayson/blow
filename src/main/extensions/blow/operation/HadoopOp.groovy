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

import blow.BlowConfig
import blow.BlowSession
import blow.events.OnAfterClusterStartedEvent
import blow.events.OnBeforeClusterStartEvent
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.domain.Statements
import blow.events.OnBeforeClusterTerminationEvent
import blow.util.WebHelper
import blow.exception.BlowConfigException

/**
 * Install an Hadoop cluster
 * <p>
 * Typical Hadoop configuration:
 * Master nodes run:
 *   - NameNode
 *   - JobTracker
 *
 *  Worker nodes run:
 *   - DataNode
 *   - TaskTracker
 *
 * <p>
 *  The machine on which bin/start-dfs.sh is run will become the *primary* *NameNode*
 * <p>
 *  The machine on which bin/start-mapred is run will become the *JobTracker* node
 *
 * <p>
 *  The file 'conf/masters' defines defines on which machines Hadoop will start *secondary* *NameNode*s
 *
 *
 * Read more:
 * http://www.michael-noll.com/tutorials/running-hadoop-on-ubuntu-linux-multi-node-cluster/
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Operation('hadoop')
class HadoopOp {

    static final defVersion = "hadoop-1.0.4"

    static final defTarball = "http://apache.rediris.es/hadoop/common/${defVersion}/${defVersion}.tar.gz"

    @Conf
    def version = defVersion

    @Conf
    def tarball = defTarball

    /**
     * Installation path
     */
    @Conf
    def path = "\$HOME/${version}"

    /** Value for the Hadoop {@code dfs.replication} property */
    @Conf
    def replication = 2

    /**
     * Set to {@code true} to format the HDFS file system
     */
    @Conf
    boolean format

    @Conf
    String primaryNode

    @Conf
    String secondaryNode

    @Conf
    String jobTrackerNode

    @Conf
    List slaveNodes

    @Conf
    String javaHome

    @Conf
    primaryNodePort = 54310

    @Conf
    jobTrackerNodePort = 54311



    // ---------------- private section ---------------------------------------

    private BlowSession session

    private List<String> masterNodes

    private int defNamenodeHttpPort = 50070

    private int defDatanodeHttpPort = 50075

    private int defJobtrackerHttpPort = 50030

    private int defTasktrackerHttpPort = 50060



//    def void setPrimaryNode( String value ) {
//        assert value
//
//        int p = value.indexOf(':')
//        if ( p != -1 ) {
//            primaryNode = value.substring(0,p)
//            primaryNodePort = value.substring(p+1)
//        }
//        else {
//            primaryNode = value
//        }
//    }
//
//
//    def void setJobTrackerNode(String value) {
//        assert value
//
//        int p = value.indexOf(':')
//        if ( p != -1 ) {
//            jobTrackerNode = value.substring(0,p)
//            jobTrackerNodePort = value.substring(p+1)
//        }
//        else {
//            jobTrackerNode = value
//        }
//    }
//

    @Validate
    void validate(BlowConfig config) {

        assert config.instanceNumFor(config.masterRole) >0, "The Hadoop op requires ar least one node for the '${config.masterRole}' role"
        assert config.instanceNumFor(config.workersRole) >0, "The Hadoop op requires ar least one node for the '${config.masterRole}' role"


        /*
         * Verify the Hadoop distribution
         *
         * IF the user specify an updated version, make sure to use the correspondant link
         * IF the user has specified a custom link as well do NOTHING
         */
        if( version != defVersion && tarball==defTarball ) {
            tarball =  "http://archive.apache.org/dist/hadoop/core/${version}/${version}.tar.gz"
        }


        if( !WebHelper.checkURLExists(tarball) ) {
            def message = "Unable to verify the Hadoop tarball: ${tarball}\n-- provide a web location from where Blow can download Hadoop using the 'tarball' attribute"
            throw new BlowConfigException(message)
        }

        /*
         * Opens the Web console ports used by Hadoop
         * <p>
         * Readmore
         * http://www.cloudera.com/blog/2009/08/hadoop-default-ports-quick-reference/
         */
        def required = [ defDatanodeHttpPort, defNamenodeHttpPort, defJobtrackerHttpPort, defTasktrackerHttpPort ]
        def added = []
        required.each {
            if ( !config.hasInboundPort(it) ) {
                added << it
            }
        }

        if ( added ) {
            config.inboundPorts.addAll(added)
            log.info "Note: The port ${added} has been added to the list of 'inboundPort' by Hadoop operation"
        }


    }

    @Subscribe
    def void initialize( OnBeforeClusterStartEvent event ) {

        masterNodes = session.listNodesNames(session.conf.masterRole)

        /*
         * if not defined, set the *NameNode* as the 'first' name in the 'masterNodes' list
         */
        if( !primaryNode ) {
            primaryNode = masterNodes[0]
        }

        /*
         * if not defined, set the *JobTracker* as the *second* name in the 'masterNodes' list
         * or fallback to the PrimaryNodeName
         */
        if ( !jobTrackerNode ) {
            if ( masterNodes.size() > 1 ) {
                jobTrackerNode = masterNodes[1]
            }
            else {
                jobTrackerNode = primaryNode
            }
        }

        /*
         * The secondary NameNode
         */
        if( !secondaryNode ) {
            if ( masterNodes.size() > 2 ) {
                secondaryNode = masterNodes[2]
            }
            else if ( masterNodes.size() > 1 ) {
                secondaryNode = masterNodes[1]
            }
            else {
                secondaryNode = primaryNode
            }
        }

        /*
         * The list of slaves
         */
        if ( !slaveNodes ) {
            slaveNodes = session.listNodesNames( session.conf.workersRole )
            // add the primary node to the list of slaves, when there are less than 3 slaves
            if( slaveNodes.size() < 3 ) {
                slaveNodes.add(0, primaryNode)
            }
        }

    }


    /**
     * When the cluster is ready, it starts the Hadoop deployment step
     */

    @Subscribe
    def void deploy( OnAfterClusterStartedEvent event )  {

        log.info "Configuring Hadoop 'master' node "
        /*
         * Deploy and configure the Primary NameNode
         */
        def statementsToRun = Statements.newStatementList(
                Statements.exec(download()),
                Statements.exec(confMaster()),
                Statements.exec(confSlaves()),
                Statements.exec(xmlCoreSite()),
                Statements.exec(xmlHdfsSite()),
                Statements.exec(xmlMapredSite()),
                Statements.exec(setJavaHome()),
                updateBinPath()
        )

        session.runStatementOnNodes(statementsToRun, primaryNode)


        /*
         * Copy RSA private key to all nodes, to make it possible to launch nodes
         * and copy via 'rsync'
         * +
         * export Hadoop PATH
         *
         * Run on *ALL* nodes
         */
        log.info "Finalizing Hadoop deployment"


        /*
         * Deploy Hadoop on all remaining nodes
         */
        session.runScriptOnNodes( syncNodes(), deployNodesList )

        /*
         * Format HDFS
         */
        if ( format ) {
            log.info "Formatting HDFS"
            def fmt = "${path}/bin/hadoop namenode -format"
            session.runScriptOnNodes( fmt .toString(), primaryNode )
        }

        /*
         * OK launch them all
         */
        log.info "Launching Hadoop daemons"
        if( primaryNode == jobTrackerNode ) {
            session.runScriptOnNodes( "${path}/bin/start-all.sh" .toString(), primaryNode )
        }
        else {
            session.runScriptOnNodes( "${path}/bin/start-dfs.sh" .toString(), primaryNode )
            session.runScriptOnNodes( "${path}/bin/start-mapred.sh" .toString(), jobTrackerNode )
        }

        /*
         * Some message
         */
        def node = session.listNodes(primaryNode)?.find()?.getNodeIp()
        def message = """\
        Done! You may navigate one of the adresses below to manage your cluster:
        - Namenode:    http://${node}:${defNamenodeHttpPort}
        - JobTracker:  http://${node}:${defJobtrackerHttpPort}
        - Tasktracker: http://${node}:${defTasktrackerHttpPort}
        """
        .stripIndent()

        log.info message
    }

    @Subscribe
    def void terminate(OnBeforeClusterTerminationEvent event ) {

        log.info "Stopping Hadoop daemons"
        if( primaryNode == jobTrackerNode ) {
            session.runScriptOnNodes( "${path}/bin/stop-all.sh" .toString(), primaryNode )
        }
        else {
            session.runScriptOnNodes( "${path}/bin/stop-dfs.sh" .toString(), primaryNode )
            session.runScriptOnNodes( "${path}/bin/stop-mapred.sh" .toString(), jobTrackerNode )
        }

    }

    /**
     * The list of ALL node names to which copy Hadoop BUT the primary node
     * (from which the distribution is copied)
     *
     * @return
     */
    protected List<String> getDeployNodesList() {

        def applyList = slaveNodes
        if ( applyList.contains(primaryNode) ) {
            applyList.remove(primaryNode)
        }
        if( secondaryNode != primaryNode && !applyList.contains(secondaryNode)) {
            applyList.add(0, secondaryNode)
        }
        if ( jobTrackerNode != primaryNode && !applyList.contains(jobTrackerNode) ) {
            applyList.add(0, jobTrackerNode)
        }

        applyList.unique()
    }

    /**
     * Set the JAVA_HOME variable at the beginning of the 'hadoop-env.sh'
     * configuration file
     *
     * @return
     */
    private String setJavaHome() {

        /*
         * Prepend the current JAVA_HOME in the file 'conf/hadoop-env.sh'
         */
        """\
        XCONF="${path}/conf/hadoop-env.sh"
        XHOME="${javaHome?:''}"
        if [ "\$XHOME" == "" ]; then XHOME=\$JAVA_HOME; fi
        if [ "\$XHOME" == "" ]; then XHOME=\$(readlink -f `which java`) && XHOME=\$(dirname \$XHOME) && XHOME=\$(dirname \$XHOME); fi
        if [ "\$XHOME" == "" ]; then
            echo "Cannot infer the Java home path -- Hadoop installation cannot continue"
            exit 1
        fi
        echo "Defining Hadoop JAVA_HOME=\$XHOME"
        echo "export JAVA_HOME=\"\$XHOME\"" | cat - \$XCONF > conf.tmp  && mv conf.tmp \$XCONF
        """
        .stripIndent()
    }

    /**
     * This script sync the hadoop distribution on the slave node using 'rsync'
     * <p>
     * Note: if something goes wrong rsync wait for some second are retry the sync process
     */
    private String syncNodes () {
        assert path
        assert version

        """\
        sync() {
          rsync -rltEzv ${primaryNode}:${path}/* ${path} --exclude ${version}/logs/ --exclude ${version}/src
        }

        count=0
        MAX=5
        while [ \$count -lt \$MAX ]; do
          count=\$(( \$count + 1 ))
          echo Sync Hadoop try \$count
          sync

          if [[ \$? -eq 0 || \$count -eq \$MAX ]]; then
            break
          else
            sleep \$(( \$count * 5 ))
          fi
        done
        """
        .stripIndent().toString()

    }


    /**
     * Update the system {@code PATH} variable adding the Hadoop bin directory
     *
     */
    private Statement updateBinPath() {
        def update = "export PATH=\"${path}/bin:\$PATH\"" .toString()   // <-- note: without .toString will fail with a cast exception for GString
        Statements.appendFile('~/.bash_profile', [ update ])
    }

    /**
     * Download the Hadoop tarball and unpack it
     * <p>
     * It assumes the tarball is 'tar.gz' compressed and all files
     * are under a folder 'hadoop-xxx'
     *
     * @return
     */
    private String download() {
        assert tarball
        assert version

        """\
        wget -q ${tarball} -O hadoop.tar.gz
        tar xzf hadoop.tar.gz
        mv hadoop-* ${path}
        """
        .stripIndent()
    }

    private String saveTextTo( String text, String fileName ) {
        assert fileName

        String script
        script = "cat > ${fileName} << 'END_OF_FILE'\n"
        script += text + '\n'
        script += 'END_OF_FILE'

        return script
    }

    /**
     * Create the 'conf/masters' configuration file
     *
     * @return
     */
    private String confMaster() {
        assert path
        assert secondaryNode

        saveTextTo( secondaryNode, "${path}/conf/masters" )

    }

    /**
     * Create the 'conf/slaves' configuration file
     *
     * @return
     */
    private String confSlaves() {
        assert path
        assert slaveNodes


        saveTextTo(slaveNodes.join('\n'),  "${path}/conf/slaves" )
    }

    /**
     * The 'core-site.xml' configuration file
     *
     * @return
     */
    private String xmlCoreSite() {
        assert path
        assert primaryNode

        """\
        cat > ${path}/conf/core-site.xml << 'EOF'
        <?xml version="1.0"?>
        <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
        <configuration>
        <property>
          <name>fs.default.name</name>
          <value>hdfs://${primaryNode}:${primaryNodePort}</value>
        </property>
        </configuration>
        EOF
        """
                .stripIndent()

    }

    /**
     * The 'mapred-site.xml' Hadoop configuration file
     *
     * @return
     */
    private String xmlMapredSite() {
        assert path
        assert jobTrackerNode

        """\
        cat > ${path}/conf/mapred-site.xml << 'EOF'
        <?xml version="1.0"?>
        <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
        <configuration>
        <property>
          <name>mapred.job.tracker</name>
          <value>${jobTrackerNode}:${jobTrackerNodePort}</value>
        </property>
        </configuration>
        EOF
        """
                .stripIndent()
    }

    /**
     * The Hadoop 'hdfs-site' configuration file
     * @return
     */
    private String xmlHdfsSite() {
        assert path
        assert replication

        """\
        cat > ${path}/conf/hdfs-site.xml << 'EOF'
        <?xml version="1.0"?>
        <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
        <configuration>
        <property>
          <name>dfs.replication</name>
          <value>${replication}</value>
        </property>
        </configuration>
        EOF
        """
                .stripIndent()
    }



}
