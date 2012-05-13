2012-05-14, version 05.4
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



