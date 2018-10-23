/**
 * This file is part of Alfresco Bulk Export Tool.
 * <p>
 * Alfresco Bulk Export Tool is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * Alfresco Bulk Export Tool  is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with Alfresco Bulk Export Tool. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.extensions.bulkexport.dao;

import com.ibm.icu.text.SimpleDateFormat;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ActionModel;
import org.alfresco.repo.publishing.PublishingModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionHistory;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;


/**
 * Implementation of {@link AlfrescoExportDao} interface
 *
 * @author Denys Santos (gsdenys@gmail.com)
 * @version 1.0.1
 */
public class AlfrescoExportDaoImpl implements AlfrescoExportDao {

    Log log = LogFactory.getLog(AlfrescoExportDaoImpl.class);

    /** Alfresco {@link ServiceRegistry} to Data Access Object */
    private ServiceRegistry registry;

    private final NodeService nodeService;
    private final FileFolderService service;
    private final NamespacePrefixResolver nsR;
    private final ContentService contentService;
    private final PermissionService permissionService;
    private final VersionService versionService;

    private QName ignoreAspectQname[] =
            {
                    ContentModel.ASPECT_TAGGABLE
            };

    private String ignoreAspectPrefix[] =
            {
                    "app"
            };

    private QName ignorePropertyQname[] =
            {
                    ContentModel.PROP_NODE_DBID,
                    ContentModel.PROP_NODE_UUID,
                    ContentModel.PROP_CATEGORIES,
                    ContentModel.PROP_CONTENT,
                    ContentModel.ASPECT_TAGGABLE
            };

    private String[] ignorePropertyPrefix =
            {
                    "app",
                    "exif"
            };

    private QName[] ignoredType =
            {
                    ContentModel.TYPE_SYSTEM_FOLDER,
                    ContentModel.TYPE_LINK,
                    ContentModel.TYPE_RATING,
                    ActionModel.TYPE_ACTION,
                    ActionModel.TYPE_COMPOSITE_ACTION,
                    PublishingModel.TYPE_PUBLISHING_QUEUE
            };


    /**
     * Data Access Object Builder
     *
     * @param registry Alfresco {@link ServiceRegistry} 
     */
    public AlfrescoExportDaoImpl(ServiceRegistry registry) {
        log.debug("Test debug logging. Congratulation your AMP is working");
        this.registry = registry;

        nodeService = this.registry.getNodeService();
        service = this.registry.getFileFolderService();
        nsR = this.registry.getNamespaceService();
        contentService = this.registry.getContentService();
        permissionService = this.registry.getPermissionService();
        versionService = this.registry.getVersionService();
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getProperties(java.lang.String)
     */
    public Map<QName, Serializable> getProperties(NodeRef nodeRef) throws Exception {
        Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);
        return properties;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getMetadataAsString(java.lang.String)
     */
    public Map<String, String> getPropertiesAsString(NodeRef nodeRef) throws Exception {

        Map<QName, Serializable> properties = this.getProperties(nodeRef);

        Map<String, String> props = new HashMap<String, String>();
        Set<QName> qNameSet = properties.keySet();

        for (QName qName : qNameSet) {
            //case the qname is in ignored type do nothing will do.
            if (this.isPropertyIgnored(qName)) {
                continue;
            }

            Serializable obj = properties.get(qName);
            String name = this.getQnameStringFormat(qName);
            String value = this.formatMetadata(obj);

            //put key value in the property list as <prefixOfProperty:nameOfProperty, valueOfProperty>
            props.put(name, value);
        }

        return props;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getChildren(java.lang.String)
     */
    public List<NodeRef> getChildren(NodeRef nodeRef) throws Exception {
        List<NodeRef> listChildren = new ArrayList<NodeRef>();

        List<ChildAssociationRef> children = nodeService.getChildAssocs(nodeRef);

        for (ChildAssociationRef childAssociationRef : children) {
            NodeRef child = childAssociationRef.getChildRef();

            if (this.isTypeIgnored(nodeService.getType(child))) {
                continue;
            }

            listChildren.add(new NodeRef(child.toString())); // deep copy
        }

        return listChildren;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getFolderChildren(java.lang.String)
     */
    public List<NodeRef> getFolderChildren(NodeRef nodeRef) throws Exception {

        List<FileInfo> folders = service.listFolders(nodeRef);

        List<NodeRef> listChildren = new ArrayList<NodeRef>();

        for (FileInfo fileInfo : folders) {
            listChildren.add(fileInfo.getNodeRef());
        }

        return listChildren;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getFileChildren(java.lang.String)
     */
    public List<NodeRef> getFileChildren(NodeRef nodeRef) throws Exception {
        List<FileInfo> files = service.listFiles(nodeRef);

        List<NodeRef> listChildren = new ArrayList<NodeRef>();

        for (FileInfo fileInfo : files) {
            listChildren.add(fileInfo.getNodeRef());
        }

        return listChildren;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getPath(java.lang.String)
     */
    public String getPath(NodeRef nodeRef) throws Exception {
        //get element Path
        Path path = nodeService.getPath(nodeRef);

        //get element name 
        Serializable name = nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

        //get element Path as String
        String basePath = path.toDisplayPath(nodeService, permissionService);

        return (basePath + "/" + name);
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getContent(java.lang.String)
     */
    public ByteArrayOutputStream getContent(NodeRef nodeRef) throws Exception {
        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
        if (reader == null) {
            // no data for this node
            return null;
        }


        InputStream in = reader.getContentInputStream();
        int size = in.available();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[(size + 100)];
        int sizeOut;

        while ((sizeOut = in.read(buf)) != -1) {
            out.write(buf, 0, sizeOut);
        }

        out.flush();
        out.close();

        in.close();


        return out;
    }

    public boolean getContentAndStoreInFile(NodeRef nodeRef, String outputFileName) throws Exception {
        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
        if (reader == null) {
            // no data for this node
            return false;
        }

        File output = new File(outputFileName);
        reader.getContent(output);

        return true;
    }

    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getProperty(java.lang.String, java.lang.String)
     */
    public String getProperty(NodeRef nodeRef, QName propertyQName) throws Exception {
        Serializable value = nodeService.getProperty(nodeRef, propertyQName);

        return this.formatMetadata(value);
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getType(java.lang.String)
     */
    public String getType(NodeRef nodeRef) throws Exception {
        QName value = nodeService.getType(nodeRef);

        String name = this.getQnameStringFormat(value);

        return name;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getAspects(java.lang.String)
     */
    public List<QName> getAspects(NodeRef nodeRef) throws Exception {
        Set<QName> aspectSet = nodeService.getAspects(nodeRef);
        List<QName> qn = new ArrayList<QName>(aspectSet);

        return qn;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getAspectsAsString(java.lang.String)
     */
    public List<String> getAspectsAsString(NodeRef nodeRef) throws Exception {
        List<QName> qn = this.getAspects(nodeRef);
        List<String> str = new ArrayList<String>();

        for (QName qName : qn) {
            if (this.isAspectIgnored(qName)) {
                continue;
            }

            String name = this.getQnameStringFormat(qName);
            str.add(name);
        }

        return str;
    }


    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#isFolder(java.lang.String)
     */
    public boolean isFolder(NodeRef nodeRef) throws Exception {
        log.debug("isFolder");

        if (info != null) {
            LOG.debug("isFolder got file info getName = " + info.getName());
            LOG.debug("isFolder got file info isFolder = " + info.isFolder());
            LOG.debug("isFolder return isFolder");
        } else {
            LOG.debug("Fileinfo for Noderef is null: " + nodeRef.getId());
        }

        return info.isFolder();
    }

    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getNodeRef(java.lang.String)
     */
    public NodeRef getNodeRef(String nodeRef) {
        try {
            NodeRef nr = new NodeRef(nodeRef);
            return nr;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getNodeRefHistory(java.lang.String)
     */
    public Map<String, NodeRefRevision> getNodeRefHistory(String nodeRef) throws Exception {
        log.debug("getNodeRefHistory(nodeRef) nodeRef = " + nodeRef);
        Map<String, NodeRefRevision> nodes = null;

        NodeRef nr = getNodeRef(nodeRef);
        if (nr != null) {
            VersionHistory history = versionService.getVersionHistory(nr);
            if (history == null) {
                log.debug("getNodeRefHistory(nodeRef) no history available");
                return nodes;
            }

            Collection<Version> availableVersions = history.getAllVersions();

            if (availableVersions == null) {
                log.debug("getNodeRefHistory(nodeRef) no versions found in history");
                return nodes;
            }

            nodes = new HashMap<String, NodeRefRevision>();
            Iterator iterator = availableVersions.iterator();
            while (iterator.hasNext()) {
                Object ov = iterator.next();
                Version v = (Version) ov; // contains storeRef
                String checkInComment = v.getDescription();    // check in comment
                String vlabel = v.getVersionLabel(); // version label eg. 1.1
                NodeRef frozenNodeRef = v.getFrozenStateNodeRef();   // this contains the revisioned versions
                NodeRef versionedNodeRef = v.getVersionedNodeRef();  // this is allways the latest revision
                String frozenNodRef = frozenNodeRef.toString();
                String headNodeRef = versionedNodeRef.toString();

                // this contains a list of all attributes for the Item. We may not need it since we dig them out at a store item id level.
                Map<String, Serializable> versionProps = v.getVersionProperties();
                NodeRefRevision revision = new NodeRefRevision();
                revision.comment = checkInComment;
                revision.node = frozenNodeRef;

                nodes.put(vlabel, revision);
                //
                // we need to get the comment history as well because this is not available when we get content data and properties....
                log.debug("getNodeRefHistory(nodeRef) v = " + v.toString());
            }
        }
        return nodes;
    }


    public boolean isNodeIgnored(String nodeRef) {
        log.debug("isNodeIgnored");
        NodeRef nr = getNodeRef(nodeRef);

        QName value = nodeService.getType(nr);

        log.debug("isNodeIgnored got service type");
        return isTypeIgnored(value);
    }

    // #######################################################################################
    // ####                              PRIVATE METHODS                                   ### 
    // #######################################################################################

    /**
     * Verify if the type qname is ignored 
     *
     * @param qName
     * @return {@link Boolean}
     */
    private boolean isPropertyIgnored(QName qName) {
        //verify if qname is in ignored
        for (QName qn : this.ignorePropertyQname) {
            if (qn.equals(qName)) {
                return true;
            }
        }

        //verify if qname prefix is in ignored
        //String prefix = qName.getPrefixString();
        String prefix = qName.getPrefixedQName(nsR).getPrefixString();
        for (String str : this.ignorePropertyPrefix) {

            //str.equalsIgnoreCase(prefix)

            if (prefix.startsWith(str)) {
                return true;
            }
        }

        return false;
    }


    /**
     * Verify if the aspect qname is ignored 
     *
     * @param qName
     * @return {@link Boolean}
     */
    private boolean isAspectIgnored(QName qName) {
        //verify if qname is in ignored
        for (QName qn : this.ignoreAspectQname) {
            if (qn.equals(qName)) {
                return true;
            }
        }

        //verify if qname prefix is in ignored
        //String prefix = qName.getPrefixString();
        String prefix = qName.getPrefixedQName(nsR).getPrefixString();
        for (String str : this.ignoreAspectPrefix) {
            if (prefix.startsWith(str)) {
                return true;
            }
        }

        return false;
    }


    /**
     * Verify if the tipe qname is ignored 
     *
     * @param qName
     * @return {@link Boolean}
     */
    private boolean isTypeIgnored(QName qName) {
        //verify if qname is in ignored
        for (QName qn : this.ignoredType) {
            if (qn.equals(qName)) {
                LOG.debug("nodeIsIgnored" + nodeRef);
                return true;
            }
        }

        return false;
    }


    /**
     * Return Qname in String Format
     *
     * @param qName
     * @return {@link String}
     */
    private String getQnameStringFormat(QName qName) throws Exception {
        return qName.getPrefixedQName(nsR).getPrefixString();
    }


    /**
     * Format metadata guided by Bulk-Import format
     *
     * @param obj
     * @return {@link String}
     */
    private String formatMetadata(Serializable obj) {
        String returnValue = "";

        if (obj != null) {
            if (obj instanceof Date) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");

                Date date = (Date) obj;
                returnValue = format.format(date);
                returnValue = returnValue.substring(0, 26) + ":" + returnValue.substring(26);
            } else {

                //
                // TODO: Format data to all bulk-import data format (list as example)
                //

                returnValue = obj.toString();
            }
        }

        return returnValue;
    }
}
