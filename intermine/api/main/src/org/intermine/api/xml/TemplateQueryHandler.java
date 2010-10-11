package org.intermine.api.xml;

/*
 * Copyright (C) 2002-2010 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.intermine.api.profile.InterMineBag;
import org.intermine.api.template.SwitchOffAbility;
import org.intermine.api.template.TemplateQuery;
import org.intermine.pathquery.PathConstraint;
import org.intermine.pathquery.PathConstraintLoop;
import org.intermine.pathquery.PathConstraintSubclass;
import org.intermine.pathquery.PathQuery;
import org.intermine.pathquery.PathQueryHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Extension of PathQueryHandler to handle parsing TemplateQueries
 * @author Xavier Watkins
 */
public class TemplateQueryHandler extends PathQueryHandler
{
    Map<String, TemplateQuery> templates;
    String templateName;
    String templateTitle;
    String templateComment;
    Map<PathConstraint, String> constraintDescriptions = new HashMap<PathConstraint, String>();
    List<PathConstraint> editableConstraints = new ArrayList<PathConstraint>();
    Map<PathConstraint, SwitchOffAbility> constraintSwitchables =
        new HashMap<PathConstraint, SwitchOffAbility>();

    /**
     * Constructor
     * @param templates Map from template name to TemplateQuery
     * @param savedBags Map from bag name to bag
     * @param version the version of the XML, an attribute on the profile manager
     */
    public TemplateQueryHandler(Map<String, TemplateQuery> templates,
            Map<String, InterMineBag> savedBags, int version) {
        super(new HashMap<String, PathQuery>(), version);
        this.templates = templates;
        reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs)
        throws SAXException {
        if ("template".equals(qName)) {
            templateName = attrs.getValue("name");
            templateTitle = attrs.getValue("title");
            if (attrs.getValue("description") != null && templateTitle == null) {
                // support old serialisation format: description -> title
                templateTitle = attrs.getValue("description");
            }
            templateComment = attrs.getValue("comment");
        } else if ("constraint".equals(qName)) {
            String path = attrs.getValue("path");
            if (currentNodePath != null) {
                if (path != null) {
                    throw new SAXException("Cannot set path in a constraint inside a node");
                }
                path = currentNodePath;
            }
            String code = attrs.getValue("code");
            String type = attrs.getValue("type");
            if (type != null) {
                // subclass constraint, don't do anything else.
                query.addConstraint(new PathConstraintSubclass(path, type));
                return;
            }
            path = path.replace(':', '.');
            if (path == null) {
                throw new NullPointerException("Null path while processing template "
                        + templateName);
            }
            constraintPath = path;
            constraintAttributes = new HashMap<String, String>();
            for (int i = 0; i < attrs.getLength(); i++) {
                constraintAttributes.put(attrs.getQName(i), attrs.getValue(i));
            }
            constraintValues = new LinkedHashSet<String>();

            PathConstraint constraint = processConstraint(query, constraintPath,
                    constraintAttributes, constraintValues);
            if ((code == null) || (constraint instanceof PathConstraintLoop)) {
                query.addConstraint(constraint);
            } else {
                query.addConstraint(constraint, code);
            }
            String description = constraintAttributes.get("description");
            String editable = constraintAttributes.get("editable");
            if ("true".equals(editable)) {
                editableConstraints.add(constraint);
            }
            constraintDescriptions.put(constraint, description);
            String switchable = constraintAttributes.get("switchable");
            if ("on".equals(switchable)) {
                constraintSwitchables.put(constraint, SwitchOffAbility.ON);
            } else if ("off".equals(switchable)) {
                constraintSwitchables.put(constraint, SwitchOffAbility.OFF);
            }
            constraintPath = null;
        } else {
            super.startElement(uri, localName, qName, attrs);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("template".equals(qName)) {
            TemplateQuery t = new TemplateQuery(templateName, templateTitle, templateComment,
                    query);
            t.setEditableConstraints(editableConstraints);
            for (Map.Entry<PathConstraint, String> entry : constraintDescriptions.entrySet()) {
                t.setConstraintDescription(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<PathConstraint, SwitchOffAbility> entry
                    : constraintSwitchables.entrySet()) {
                t.setSwitchOffAbility(entry.getKey(), entry.getValue());
            }
            templates.put(templateName, t);
            reset();
        } else {
            super.endElement(uri, localName, qName);
        }
    }

    private void reset() {
        templateName = "";
        templateTitle = "";
        templateComment = "";
        editableConstraints.clear();
        constraintDescriptions.clear();
    }
}
