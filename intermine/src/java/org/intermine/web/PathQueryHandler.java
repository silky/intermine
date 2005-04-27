package org.intermine.web;

/*
 * Copyright (C) 2002-2005 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.intermine.metadata.Model;
import org.intermine.objectstore.query.BagConstraint;
import org.intermine.objectstore.query.ConstraintOp;
import org.intermine.util.StringUtil;
import org.intermine.util.TypeUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extension of DefaultHandler to handle parsing PathQuery objects
 * @author Mark Woodbridge
 * @author Kim Rutherford
 */
class PathQueryHandler extends DefaultHandler
{
    Map queries;
    String queryName;
    PathQuery query;
    PathNode node;

    /**
     * Constructor
     * @param queries Map from query name to PathQuery
     */
    public PathQueryHandler(Map queries) {
        this.queries = queries;
    }

    /**
     * @see DefaultHandler#startElement
     */
    public void startElement(String uri, String localName, String qName, Attributes attrs)
        throws SAXException {
        if (qName.equals("query")) {
            queryName = attrs.getValue("name");
            Model model;
            try {
                model = Model.getInstanceByName(attrs.getValue("model"));
            } catch (Exception e) {
                throw new SAXException(e);
            }
            query = new PathQuery(model);
            if (attrs.getValue("view") != null) {
                query.setView(StringUtil.tokenize(attrs.getValue("view")));
            }
        }
        if (qName.equals("node")) {
            node = query.addNode(attrs.getValue("path"));
            if (attrs.getValue("type") != null) {
                node.setType(attrs.getValue("type"));
            }
        }
        if (qName.equals("constraint")) {
            int opIndex = toStrings(ConstraintOp.getValues()).indexOf(attrs.getValue("op"));
            ConstraintOp constraintOp = ConstraintOp.getOpForIndex(new Integer(opIndex));
            Object constraintValue;
            // If we know that the query is not valid, don't resolve the type of
            // the node as it may not resolve correctly
            if (node.isReference() || BagConstraint.VALID_OPS.contains(constraintOp)
                    || !query.isValid()) {
                constraintValue = attrs.getValue("value");
            } else {
                constraintValue = TypeUtil.stringToObject(MainHelper.getClass(node.getType()),
                                                          attrs.getValue("value"));
            }
            String editable = attrs.getValue("editable");
            boolean editableFlag = false;
            if (editable != null && editable.equals("true")) {
                editableFlag = true;
            }
            String description = attrs.getValue("description");
            String identifier = attrs.getValue("identifier");
            node.getConstraints().add(new Constraint(constraintOp, constraintValue,
                                                     editableFlag, description, identifier));
        }
    }
    
    /**
     * @see DefaultHandler#endElement
     */
    public void endElement(String uri, String localName, String qName) {
        if (qName.equals("query")) {
            queries.put(queryName, query);
        }
    }
    
    /**
     * Convert a List of Objects to a List of Strings using toString
     * @param list the Object List
     * @return the String list
     */
    protected List toStrings(List list) {
        List strings = new ArrayList();
        for (Iterator i = list.iterator(); i.hasNext();) {
            strings.add(i.next().toString());
        }
        return strings;
    }
}