# Alfresco Bulk Export Tool #
A bulk filesystem export tool for the open source [Alfresco](http://www.alfresco.com) CMS.

This module has as main objective provides a simple way to export Alfresco storaged content (with properties) to a file system.  To start this system is used a simple HTTP/GET call to a webscript that just can be initialized by system administrator.

The format of filesystem choosed is described by Alfresco Bulk filesystem import, that can be found in [Bulk Import Project Page](https://github.com/pmonks/alfresco-bulk-import/wiki).

A smal detail that need to be mencioned is the fact that file exportion will be done in server machine (even called by remote machine), else,  you also can map a remote directory in the server end export content to there.

Please don't hesitate to contact the project owner if you'd like to contribute!

# Prerequisites #
Please make sure you are running a version of Alfresco that the tool has been developed for. As of the time of writing, the tool has been tested on alfresco 5.0, it should work on Alfresco v4.0+. The tool uses Java 1.7 features so will not work on versions of alfresco that do not support at least java 1.7.

# Usage #
This module is started by a simple webscript call. To initiate the exportation you just use this URL in a browser:

http://{host}:{port}/alfresco/service/extensions/bulkexport/export?nodeRef={noderef}&base={base}&ignoreExported={ignoreExported?}&exportVersions=true&revisionHead=false&useNodeCache=true&

where:
* **{host}:** is the host of your installation.
* **{port}:** is the port used by Alfresco.
* **{noderef}:** is an Alfresco node reference that you want to export. Like:
   _workspace://`SpacesStore`/c494aff5-bedf-40fa-8d0d-2aebcd583579_
* **{base}:** is a base path of your target folder (in the Alfresco Server). Like: _/home/gsdenys/export_ or _C:/export_.
* **{ignoreExported?}:** parameter **optional**. when it is true, the system will ignore all Alfresco nodes already exported. The default is _false_.
* exportVersion if true exports all revisions of a node - parameter **optional**, The default is _false_.
* revisionHead if true (and exportVersion=true) then files are exported with head (latest) revision numbered, if set to false then the default numbering scheme used by the Alfresco Bulk Import tool is used (head revision is not numbered) - parameter **optional**, only used if exportVersion set, The default is _false_.
* useNodeCache if true then a list of nodes to export is cached to the export area for future repeated use. Sometimes useful for large exports of data due to the transaction cache being full - parameter **optional**, The default is _false_.
* nbOfThreads number of threads in the thread pool if none is given the default value is 1
* exportChunkSize is the number of Nodes handled by each Task iteration. Default value is 10

When the export is ended you will see in browser a message _"Process finished Successfully"_. Once this message is printed, look-up your content in the Alfresco Server in the {base} directory.

The exporter will write progress to the Alfresco Log file as well as any issues it may have. Issues will also be reported on the web interface.

# Installation Steps #
The following steps describe how to download and install the Alfresco Bulk Filesystem Import Tool:

  1. Download the latest AMP file containing the tool from [Release](https://github.com/gsdenys/alfresco-bulk-export/releases)
  2. Shutdown your Alfresco instance
  3. Make a backup of the original alfresco.war file. On Tomcat, this is located in ${ALFRESCO\_HOME}/tomcat/webapps
  4. Use the Alfresco [Module Management Tool](http://wiki.alfresco.com/wiki/Module_Management_Tool) to install the AMP file obtained in step 1
  5. Restart Alfresco, watching the log carefully for errors

# Logging #
The plugin uses the standard alfresco log4j mechanism, the following modules are configured in the amp to the following values:
log4j.logger.org.alfresco.extensions.bulkexport.controler.Engine=INFO
log4j.logger.org.alfresco.extensions.bulkexport.dao.AlfrescoExportDaoImpl=ERROR
log4j.logger.org.alfresco.extensions.bulkexport.model.FileFolder=ERROR
log4j.logger.org.alfresco.extensions.bulkexport.Export=INFO
