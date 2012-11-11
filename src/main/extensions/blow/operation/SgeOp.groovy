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
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import blow.util.WebHelper

/**
 * Handles Sun Grid Engine deployment and configuration 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
@Operation("sge")
class SgeOp {

	@Conf
	String clusterName = "cloud_sge"
	
	@Conf
	String path = "/opt/sge6"
	
	@Conf
	String qmasterPort = "6444"

	@Conf
	String execdPort = "6445"
	
	@Conf 
	String cell = "default"
	
	@Conf
	String adminEmail = "none@none.edue"
	
	@Conf
	String binaryZipFile = "https://s3.amazonaws.com/cbcrg-lab/sge6.zip"
	
	@Conf
	String sourcesTarball = "http://sourceforge.net/projects/gridscheduler/files/GE2011.11/GE2011.11.tar.gz/download"

    /**
     * The of the installation. It could be of the following:
     * - compile: compile the SGE from the source, deploy and configure it
     * - deploy: deploy SGE using the binaries tarball
     * - config: just configure it, the binaries have to exist in the specified root folder
     */
	@Conf
	String installationMode = "deploy"
	
	@Conf
	String temp = "/tmp"

    /**
     * The SGE spool directory. It could be possible to enter a local folder to improve cluster performance
     * <p>
     * Read more about local spool dir here
     * http://gridscheduler.sourceforge.net/howto/nfsreduce.html
     */
    @Conf
    String spool

    @Conf
    String scheduler = 'normal'


    /** The current {@link BlowSession} */
    private BlowSession session

    /*
      * A blank-separated string containing the hostnames on which install and run the SGE nodes
      */
	private String nodes = ""

    /** The current user */
    private String user

    private String worker

    private String master

    private static SCHED_VALUES = ["normal":1, "high":2, "max":3]

    @Validate
    def validation( BlowConfig config ) {
        assert clusterName
        assert path
        assert qmasterPort
        assert qmasterPort.isInteger()
        assert execdPort
        assert execdPort.isInteger()
        assert cell
        assert scheduler in SCHED_VALUES.keySet()

        /*
         * Make sure that the 'roles' defined matches the component 'topology'
         */
        assert config.instanceNumFor(config.masterRole) == 1, "The SGE op requires the '${config.masterRole}' role to declare exactly one node"


        /*
         * Check availability of downloadable stuff
         */
        if( installationMode == "compile" ) {
            def message="Cannot access SGE sources distribution at URL: ${sourcesTarball} -- please provide a valid web location by providing the attribute 'sourcesTarball' in the Hadoop operation"
            assert WebHelper.checkURLExists(sourcesTarball), message
        }
        else if( installationMode == "deploy" ) {
            def message="Cannot access SGE binaries distribution at URL: ${binaryZipFile} -- please provide a valid web location by providing the attribute 'binaryZipFile' in the Hadoop operation"
            assert WebHelper.checkURLExists(binaryZipFile), message
        }

    }

	@Subscribe
	public void configureSge( OnAfterClusterStartedEvent event ) {
        log.info "Configuring OpenGridEngine (SGE)"

		TraceHelper.debugTime("SGE configuration") { configureTask() }

    }
	
	protected void configureTask() {

        user = session.conf.userName
        master = session.conf.masterRole
        worker = session.conf.workersRole

        // the list of nodes that make up the cluster
		nodes = session.listNodesNames().join(" ")

        // if not defined use the default spool path
        if( !spool ) {
            spool = "${path}/${cell}/spool"
        }

		/*
		 * start the installation
		 */
		TraceHelper.debugTime("SGE copying conf file", { copySgeConfigFileToMaster() })
		TraceHelper.debugTime("SGE installing '${master}' node", { installMasterNode() })
		TraceHelper.debugTime("SGE installing '${worker}' nodes", { installWorkersNodes() } )

	} 
	

	
	protected copySgeConfigFileToMaster() {
        assert master, "Missing 'master' node in SGE configuration"
        log.debug "copySgeConfigFileToMaster"

		session.copyToNodes( confTemplate(), "/tmp/sge.conf", master )
	}

    /**
     * Runs the SGE master node installation script.
     *
     */
	protected void installMasterNode() {
		
		def script = "";

        if( installationMode == "compile" ) {
            script = scriptDownloadAndCompile()
        }
        else if( installationMode == "deploy" ) {
            script = scriptDownloadBinaries()
        }
        else if( installationMode != "config" ) {
            log.warn "Unknown SGE installation type: '${installationMode}'"
        }

        script += "\n" + scriptInstallMaster();


        session.runScriptOnNodes(script, master, true)

	}  

    /**
     * Run the SGE executor daemon on the workers node
     *
     */
	protected void installWorkersNodes() {

        if( session.conf.instanceNumFor(session.conf.workersRole) < 1 ) {
            log.debug "No SGE workers required -- skip configuration"
            return
        }

		session.runScriptOnNodes(scriptInstallWorker(), worker, true)
	}
	
	
	private String scriptSshConf() {
		"""\
		#
		# Create password-less login
		#
		ssh-keygen -f ~/.ssh/id_rsa -N ''
		cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
		chmod 600 ~/.ssh/authorized_keys

		echo "Host *" >> ~/.ssh/config
		echo "  StrictHostKeyChecking no" >> ~/.ssh/config
		echo "  UserKnownHostsFile /dev/null" >> ~/.ssh/config
		chmod 600 ~/.ssh/config
		""" 
		.stripIndent()
	} 
	
	protected String scriptDownloadAndCompile() {
		"""\
		#
		# Installing required component 
		#
		blowpkg install -y csh
		blowpkg -y groupinstall 'Development Tools' 'Development Libraries'
		
		#
		# Downloading and compiling OGE 
		#
		cd /tmp
		wget -q "${sourcesTarball}"
		tar -xvf GE2011.11.tar.gz 
		rm GE2011.11.tar.gz 
		cd GE2011.11/source
		
		./aimk -no-java -no-jni -no-secure -spool-classic -no-dump -no-qmon -only-depend
		./scripts/zerodepend
		./aimk -no-java -no-jni -no-secure -spool-classic -no-dump -no-qmon depend
		./aimk -no-java -no-jni -no-secure -spool-classic -no-dump -no-qmon
		
		#
		# Install to targetHost directory
		#
		export SGE_ROOT="${path}"
		mkdir -p "${path}"
		echo "Y" | scripts/distinst -all -local -noexit
		
		# Adding fake qmon 
		touch "${path}/bin/linux-x64/qmon"
		touch "${path}/utilbin/linux-x64/qmon"
		""" 
		.stripIndent()
	} 
	
	protected String scriptDownloadBinaries() {
	
		assert temp
		assert binaryZipFile
		assert path
		
		"""\
		#
		# Download and unzip 
		# 
		cd ${temp}
		wget -q -O sge6.zip ${binaryZipFile}
		unzip sge6.zip
		rm sge6.zip

		#
		# Install to targetHost directory
		#
		export SGE_ROOT="${path}"
		[ -d ${path} ] && rm -rf ${path}
		mkdir -p ${path}
		mv ./sge6/* ${path}
		rm -rf ./sge6
		chown -R $user ${path}

		""" 
		.stripIndent()	
		
	} 
	
	protected String scriptInstallMaster() {
		
		assert path, "Variable 'root' cannot be empty"
		assert cell, "Variable 'cell' cannot be empty"
        assert user, "Variable 'user' cannot be empty"
        assert spool, "Variable 'spool' cannot be empty"

		"""\
		# Install csh
		if ! command -v csh  &>/dev/null; then blowpkg install -y csh; fi

		# Create the spool directory
		[ -d '${spool}' ] || mkdir -p '${spool}'
		chown -R $user '${spool}'

		#
		# Run the SGE the master node and the execd daemons 
		# 
		cd ${path}
		cp /tmp/sge.conf .
		[ -d ${path}/${cell} ] && rm -rf ${path}/${cell}
		./inst_sge -m -x -auto ./sge.conf
		sleep 1

		#
		# Add to the '.bash_profile'
		#
		source ${path}/${cell}/common/settings.sh
		echo "source ${path}/${cell}/common/settings.sh" >> ~${user}/.bash_profile
		
		# define 'bash' as the default shel
		qconf -rattr queue shell /bin/bash all.q
		"""
		.stripIndent()
		
	} 
	
	
	protected String scriptInstallWorker() {
		
		assert path, "Variable 'root' cannot be empty"
		assert cell, "Variable 'cell' cannot be empty"
        assert user, "Variable 'user' cannot be empty"
        assert spool, "Variable 'spool' cannot be empty"

		"""\
		# Install csh
		if ! command -v csh  &>/dev/null; then blowpkg install -y csh;  fi

		# Create the spool directory
		[ -d '${spool}' ] || mkdir -p '${spool}'
		[ `stat -f -c %T '${spool}'` != "nfs" ] && chown -R $user '${spool}'

		#
		#  Install the 'execd' on worker nodes
		#
		export SGE_ROOT="${path}"
		cd "${path}"
		./inst_sge -x -auto ${path}/sge.conf
		sleep 1

		#
		# Add to the '.bash_profile'
		#
		source ${path}/${cell}/common/settings.sh
		echo "source ${path}/${cell}/common/settings.sh" >> ~${user}/.bash_profile
		"""
		.stripIndent()
		
	} 
	
	protected String confTemplate()  {
		assert clusterName, "Provide a valid 'cluster-name' in the SGE configuration"
		assert path, "Provide a valid install 'root' path in the SGE configuration"
		assert cell, "Provide a valid 'cell' name in the SGE configuration (default)"
		assert qmasterPort.isInteger() , "Provide a valid 'qmaster-port' value (6444)" 
		assert execdPort,  "Provide a valid 'execd-port' value in the SGE configuration (6445)"
		assert nodes, "The SGE nodes cannot be empty "
        assert user
        assert spool
		assert adminEmail
        assert scheduler

        def schedConf = SCHED_VALUES[scheduler]

		"""\
		SGE_CLUSTER_NAME="${clusterName}"
		SGE_ROOT="${path}"
		SGE_QMASTER_PORT="${qmasterPort}"
		SGE_EXECD_PORT="${execdPort}"
		SGE_ENABLE_SMF="false"
		CELL_NAME="${cell}"
		ADMIN_USER="${user}"
		EXECD_SPOOL_DIR="${spool}"
		QMASTER_SPOOL_DIR="${spool}/qmaster"
		EXECD_SPOOL_DIR_LOCAL="${spool}/execd"
		GID_RANGE="20000-20100"
		SPOOLING_METHOD="classic"
		DB_SPOOLING_SERVER="none"
		DB_SPOOLING_DIR="${path}/${cell}/spooldb"
		PAR_EXECD_INST_COUNT="20"
		ADMIN_HOST_LIST="${nodes}"
		SUBMIT_HOST_LIST="${nodes}"
		EXEC_HOST_LIST="${nodes}"

		HOSTNAME_RESOLVING="true"
		SHELL_NAME="ssh"
		COPY_COMMAND="scp"
		DEFAULT_DOMAIN="none"
		ADMIN_MAIL="${adminEmail}"
		# If true, the rc scripts (sgemaster, sgeexecd, sgebdb) will be added, 
		# to start automatically during boottime
		ADD_TO_RC="true"
		SET_FILE_PERMS="true"
		RESCHEDULE_JOBS="wait"
		SCHEDD_CONF="${schedConf}"
		SHADOW_HOST=""
		EXEC_HOST_LIST_RM=""
		REMOVE_RC="true"
		WINDOWS_SUPPORT="false"
		WIN_ADMIN_NAME="Administrator"
		WIN_DOMAIN_ACCESS="false"
		CSP_RECREATE="false"
		CSP_COPY_CERTS="false"
		CSP_COUNTRY_CODE="--"
		CSP_STATE="--"
		CSP_LOCATION="--"
		CSP_ORGA="--"
		CSP_ORGA_UNIT="--"
		CSP_MAIL_ADDRESS="none@none.edu"
		"""	 
		.stripIndent()
		
	}

}
