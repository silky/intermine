package org.intermine.web.logic.bag;

/*
 * Copyright (C) 2002-2008 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A handler for turning XML bags data into an InterMineIdBag.
 *
 * @author Mark Woodbridge
 * @author Kim Rutherford
 */
public class InterMineBagHandler extends DefaultHandler
{
    private static final Logger LOG = Logger.getLogger(InterMineBagHandler.class);

    private ObjectStoreWriter uosw;
    private ObjectStoreWriter osw;
    private Map bags;
    private Integer userId;
    private Model model;

    private String bagName;
    private String bagType;
    private String bagDescription;
    private InterMineBag bag;
    private Map idToObjectMap;
    private IdUpgrader idUpgrader;
    private int elementsInOldBag;
    private Set<Integer> bagContents;

    /**
     * Create a new InterMineBagHandler object.
     *
     * @param uosw UserProfile ObjectStoreWriter
     * @param osw ObjectStoreWriter used to resolve object ids and write to the objectstore bag
     * @param bags Map from bag name to InterMineIdBag - results are added to this Map
     * @param userId the id of the user
     * @param idUpgrader bag object id upgrader
     * @param idToObjectMap a Map from id to InterMineObject. This is used to create template
     * objects to pass to createPKQuery() so that old bags can be used with new ObjectStores.
     */
    public InterMineBagHandler(ObjectStoreWriter uosw, ObjectStoreWriter osw, Map bags,
            Integer userId, Map idToObjectMap, IdUpgrader idUpgrader) {
        this.uosw = uosw;
        this.osw = osw;
        this.bags = bags;
        this.userId = userId;
        this.idUpgrader = idUpgrader;
        this.idToObjectMap = idToObjectMap;
        this.model = osw.getModel();
    }

    /**
     * {@inheritDoc}
     */
    public void startElement(@SuppressWarnings("unused") String uri,
                             @SuppressWarnings("unused") String localName,
                             String qName,
            Attributes attrs) throws SAXException {
        try {
            if (qName.equals("bag")) {
                bagContents = new HashSet();
                bagName = attrs.getValue("name");
                bagType = attrs.getValue("type");
                bagDescription = attrs.getValue("description");
                Date dateCreated;
                try {
                    dateCreated = new Date(Long.parseLong(attrs.getValue("date-created")));
                } catch (NumberFormatException e) {
                    dateCreated = null;
                }
                // only upgrade bags whose type is still in the model
                String bagClsName = model.getPackageName() + "." + bagType;
                if (model.hasClassDescriptor(bagClsName)) {
                    bag = new InterMineBag(bagName, bagType, bagDescription,
                                           dateCreated, osw.getObjectStore(), userId, uosw);
                } else {
                    LOG.warn("Not upgrading bag: " + bagName + " for user: " + userId
                             + " - " + bagType + " no longer in model.");
                }
            }

            if (qName.equals("bagElement") && bag != null) {
                elementsInOldBag++;
                Integer id = new Integer(attrs.getValue("id"));

                if (osw.getObjectById(id) == null && idToObjectMap.containsKey(id)) {
                    // the id isn't in the database and we have an Item representing the object from
                    // a previous database
                    InterMineObject oldObject = (InterMineObject) idToObjectMap.get(id);

                    Set newIds = idUpgrader.getNewIds(oldObject, osw);
                    Iterator newIdIter = newIds.iterator();
                    while (newIdIter.hasNext()) {
                        bagContents.add((Integer) newIdIter.next());
                    }
                } else {
                    bagContents.add(id);
                }
            }
        } catch (ObjectStoreException e) {
            throw new SAXException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void endElement(@SuppressWarnings("unused") String uri,
                           @SuppressWarnings("unused") String localName,
                           String qName) throws SAXException {
        try {
            if (qName.equals("bag")) {
                if (bag != null && !bagContents.isEmpty()) {
                    osw.addAllToBag(bag.getOsb(), bagContents);
                    bags.put(bagName, bag);
                }
                LOG.debug("XML bag \"" + bagName + "\" contained " + elementsInOldBag
                          + " elements, created bag with " + (bag == null ? "null"
                              : "" + bag.size()) + " elements");
                bag = null;
                elementsInOldBag = 0;
            }
        } catch (ObjectStoreException e) {
            throw new SAXException(e);
        }
    }
}
