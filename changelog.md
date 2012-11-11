2012-12-10, Version 0.7.5.1
- Disable firewall by default in DefaultOp
- Tested against Scientific Linux 6.2

2012-12-10, Version 0.7.5
- Added support for Ubuntu + Cloud BioLinux (ami-46d4792f) 
- Enabling SSH password-less access by default
- Setting 'bash' as default shell for the SGE main queue (all.q)
- Added configurable SGE 'scheduler' strategy (normal,high,max)
- Better cluster name error handling

2012-11-06 = Version 0.7.4
- Fix problem mounting ephemeral vols
- Fix warning sharing ephemerala vols

2012-10-16, Version 0.7.3
- Upgrate JClouds to version 1.5.2 (to support EC2 instance hi1_4xlarge)
- Added support for EC2 placement-groups (see BlowConfig#placementGroup)
- Better support for ephemeral volumes (if not mounted they are formatted)
- Support for file system type for volumes that require to be formatted
- Fix a bug on home directory creation
- Since this version cluster name in configuration file have to be in lowercase (due to constraint in AWS API)
- SGE now can be launched also with a single node 

2012-10-12, Version 0.7.2.1
- Supporting new device name '/dev/xvd..' mounting volumes
- Disabled firewall by defualt (moved into the DefaultOp)

2012-10-05, Version 0.7.2
- Improved support for Linux Fedora 16 

2012-09-10, Version 0.7.1
- Refactored command parameters API
- HostnameOp renamed to DefaultOp
- Installing cross-platform package manager symlink 'blowpkg'
- Promp user for access credentials, if missing
- Shell history
- Initial Hadoop support

2012-09-01, version 0.7.0
- Session persistence accross restarts
- New configuration file syntax based on Groovy BuilderSupport
- Uniform nodes naming
- Refactored Volume, NFS and SGE operation
- Support for Amazon epheremeral volumes
- Advanced topoplogies configuration
- S3 client integration
- Upgrade to JClouds 1.4.2

2012-07-20, version 0.6.3
- New fcp command to upload/download data command based on Fast Data Transfer - http://monalisa.cern.ch/FDT/
- New 'inbound-ports' configurtation property
- New key-pair management commands
- Added SGE local spool configuration option

2012-06-14, version 0.6.2
- Added 'spool-dir' configuration attribute to SGE configuration
- Fixed issue on Gluster volume format

2012-06-07, version 0.6.1
- Added support for Amazon provided KeyPair
- Fixed issue on 3Cmd op
- Fixed problem resolving environment variables
- Fixed issue deleting a EBS volume
- Upgrade to Typesafe Config 0.4.1
- Added support for existing credential + security group specification
- Fixed issue on 'Exports' operation

2012-0529, version 0.6.0
- Added support for GlusterFS
- Inject BlowSession instance in Operation object
- Inject BlowSession and BlowShell instances in Command objects
- Force session reloading on cluster restarts
- Added CLI option -c to specify the cluster-name to use
- Added CLI option -conf to specify the configuration file to use
- Added confirmation on volume deletion
- Fix issue on deleting volume on termination
- NSF device names are handled automatically and can be omitted in the configuration
- Renamed 'Plugin' to 'Operation'
- Renamed 'Hosts' to 'Hostname'
- Refactored Operations event system
- Enhancement in the log system
- Enhancement in the commands help
- Improved sanity checks on

2012-05-15, version 0.5.5
- Improved exception handling in the plugin system
- Using the OrderedEventBus
- Improved notification for user extensions script errors
- Improved SSH console
- Improved 'listvolumes' command. Not it is possible to show the creation snapshot + filter by instance-id and snapshot-id
- New attribute 'make-snapshot-on-termination' on NFS plugin. It creates a volume snapshot on cluster termination.
- Improved NFS plugin. It ask for confirmation before delete a volume on termination.
- Improved plugin validation system. The validator method can accept the configuration as parameter.

2012-05-14, version 0.5.4
- Added 'createsnapshots' + 'deletesnapshot' commands
- Enanched shell api 
- Better shell command exceptions handling
- Added account-id in the conf file

2012-05-10, version 0.5.3
- Fixed minor issue on SSH console
- Improved conf command
- Fixed minor issues

2012-05-08, version 0.5.2
- SSH key-pair are created automatically if missing 

2012-05-05, version 0.5.1
- Fixed issue user-name, public/private keys parameters 

2012-04-29, version 0.5.0
- Introducing 'shell methods' extension
- Added 'listvolumes' command
- Fixed error in 'checkForValidResponse' method

2012-04-25, version 0.4.4
- Switched to logback as logging subsystem
- Logging to .blow.log file on current directory
- Some fixes

2012-04-24, version 0.4.3
- Integrated SSH pseudo-terminal
- Fixed issue on NFS volume delete

2012-04-19, version 0.4.2
- Refactored directory structure
- Detach EBS volumes moved after cluster termination

2012-04-11, version 0.4.1
- Added GPL license headers
- Added plugin validation feature
- Added RunShellScript plugin
- Added SSH shell executor command

2012-04-06
- The build now creates a self-contained executable script

2012-04-04, version 0.4.0
- Project renamed to GemCloud
- Added 'append' plugin
- Added 's3cmd' plugin
- Added ability to mount EC2 snapshots and delete EBS volumes on termination
- Improved validation of configuration properties
- Improved integration of Maven pom.xml in the build script

2012-03-22, version 0.3.2
- Added One-Jar packaging 

2012-03-22, version 0.3.1
- Plugin can now be any class marked with the annotation @Plugin

2012-03-21, version 0.3
- Added shell actions
- Added plugin eventbus communication system



