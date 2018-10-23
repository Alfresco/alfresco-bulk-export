package org.alfresco.extensions.bulkexport.controler;

import org.alfresco.extensions.bulkexport.dao.AlfrescoExportDao;
import org.alfresco.extensions.bulkexport.dao.NodeRefRevision;
import org.alfresco.extensions.bulkexport.model.FileFolder;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This thread class manages the output of nodes on the filesystem.
 *
 * @author Simon Girardin
 */
public class NodeExportTask implements Callable<String> {

    Log log = LogFactory.getLog(NodeExportTask.class);

    /**
     * Flag indicating if versions are exported
     */
    private boolean exportVersions;

    /**
     * If true the the head revision will be named, eg. if head revision is 1.4 then filename will contain the revision.
     * This behaviour is not how the bulk importer expects revisions
     */
    private boolean revisionHead;

    /**
     * Data Access Object
     */
    private AlfrescoExportDao dao;

    /**
     * File and folder manager
     */
    private FileFolder fileFolder;

    /**
     * Nodes this thread has to export
     */
    private List<NodeRef> nodesToExport;

    /**
     * Task Number for keeping logs accurate
     */
    private int taskNumber;

    NodeExportTask(List<NodeRef> nodesToExport, boolean exportVersions, boolean revisionHead, AlfrescoExportDao dao, FileFolder fileFolder, int taskNumber) {
        this.dao = dao;
        this.fileFolder = fileFolder;
        this.nodesToExport = nodesToExport;
        this.exportVersions = exportVersions;
        this.revisionHead = revisionHead;
        this.taskNumber = taskNumber;
    }

    /**
     * Create file (Document and Bulk XML Meta data)
     *
     * @param file
     * @throws Exception
     */
    private void createFile(NodeRef headNode, NodeRef file, String revision, boolean isHeadRevision) throws Exception {
        String path = null;
        if (revision == null) {
            log.error("createFile (headNode: " + headNode.toString() + " , filenode: )" + file.toString() + " , revision: " + revision + ")");
            throw new Exception("revision for node was not found");
        }

        path = this.dao.getPath(headNode) + "." + revision;

        // if we are exporting using the revisions compatible with alfresco bulk import then we do not number the head(most recent) revisoon
        if (!revisionHead && isHeadRevision) {
            path = this.dao.getPath(headNode);
        }

        doCreateFile(file, path);
    }

    private void createFile(NodeRef file) throws Exception {
        String path = null;
        path = this.dao.getPath(file);
        doCreateFile(file, path);
    }

    private void doCreateFile(NodeRef file, String path) throws Exception {
        //get Informations
        LOG.debug("doCreateFile (" + file.getId() + ")");

        // need these variables out of the try scope for debugging purposes when the exception is thrown
        String type = null;
        List<String> aspects = null;
        Map<String, String> properties = null;

        try {
            String fname = this.fileFolder.createFullPath(path);
            log.debug("doCreateFile file =" + fname);
            if (this.dao.getContentAndStoreInFile(file, fname) == false) {
                LOG.debug("doCreateFile ignore this file: " + fname);
                return;
            }
            type = this.dao.getType(file);
            aspects = this.dao.getAspectsAsString(file);
            properties = this.dao.getPropertiesAsString(file);

            //Create Files
            this.fileFolder.insertFileProperties(type, aspects, properties, path);
            type = null;
            properties = null;
            aspects = null;
        } catch (Exception e) {
            // for debugging purposes
            log.error("doCreateFile failed for noderef = " + file.toString());
            throw e;
        }
    }


    /**
     * Create Folder and XML Metadata
     *
     * @param folder
     * @throws Exception
     */
    private void createFolder(NodeRef folder) throws Exception {
        //Get Data
        String path = this.dao.getPath(folder);
        log.debug("createFolder path=" + path);
        String type = this.dao.getType(folder);
        log.debug("createFolder type=" + type);
        List<String> aspects = this.dao.getAspectsAsString(folder);
        Map<String, String> properties = this.dao.getPropertiesAsString(folder);

        //Create Folder and XMl Metadata
        this.fileFolder.createFolder(path);
        this.fileFolder.insertFileProperties(type, aspects, properties, path);
    }

    private void exportHeadRevision(NodeRef nodeRef) throws Exception {
        this.createFile(nodeRef);
    }

    private void exportFullRevisionHistory(NodeRef nodeRef) throws Exception {
        Map<String, NodeRefRevision> nodes = this.dao.getNodeRefHistory(nodeRef.toString());
        if (nodes != null) {
            List sortedKeys = new ArrayList(nodes.keySet());

            Collections.sort(sortedKeys, new VersionNumberComparator());
            if (sortedKeys.size() < 1) {
                throw new Exception("no revisions available");
            }

            String headRevision = (String) sortedKeys.get(sortedKeys.size() - 1);

            for (String revision : nodes.keySet()) {
                NodeRefRevision nodeRevision = nodes.get(revision);
                this.createFile(nodeRef, nodeRevision.node, revision, headRevision == revision);
            }
        } else {
            // no revision history so lets just create the most recent revision
            log.debug("execute (noderef) no revision history found, dump node as head revision");
            this.createFile(nodeRef, nodeRef, "1.0", true);
        }
    }

    @Override
    public String call() throws Exception {
        AuthenticationUtil.clearCurrentSecurityContext();
        AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();

        int logCount = nodesToExport.size();
        log.info("Running task " + taskNumber + " will export " + logCount + " nodes");
        final int NODES_TO_PROCESS = 100;
        for (NodeRef nodeRef : nodesToExport) {
            try {
                LOG.debug("Handling in task NodeRef: " + nodeRef.getId());
                logCount--;
                if (this.dao.isFolder(nodeRef)) {
                    log.debug("NodeRef is folder: " + nodeRef.getId());
                    this.createFolder(nodeRef);
                } else {
                    LOG.debug("NodeRef is document: " + nodeRef.getId());
                    if (exportVersions) {
                        exportFullRevisionHistory(nodeRef);
                    } else {
                        exportHeadRevision(nodeRef);
                    }
                }
                if (logCount % NODES_TO_PROCESS == 0) {
                    log.info("Task " + taskNumber + " has remaining nodes to process " + logCount);
                }
            } catch (InterruptedException e) {
                LOG.info(Thread.currentThread().getName() + " interrupted");
            } catch (Exception e) {
                LOG.error("Error in task:" + taskNumber + " on Node: " + nodeRef.getId(), e);
            }
        }

        AuthenticationUtil.clearCurrentSecurityContext();
        return "Task " + taskNumber + " is finished";
    }
}