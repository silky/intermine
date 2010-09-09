package org.intermine.web.logic.pathqueryresult;

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
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.api.profile.InterMineBag;
import org.intermine.metadata.AttributeDescriptor;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.query.ConstraintOp;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryCollectionReference;
import org.intermine.objectstore.query.QueryField;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OuterJoinStatus;
import org.intermine.pathquery.Path;
import org.intermine.pathquery.PathException;
import org.intermine.pathquery.PathQuery;
import org.intermine.util.CollectionUtil;
import org.intermine.util.DynamicUtil;
import org.intermine.util.TypeUtil;
import org.intermine.web.logic.config.FieldConfig;
import org.intermine.web.logic.config.FieldConfigHelper;
import org.intermine.web.logic.config.WebConfig;

/**
 * Helper for everything related to PathQueryResults
 *
 * @author Xavier Watkins
 */
public final class PathQueryResultHelper
{
    private PathQueryResultHelper() {
    }

    private static final Logger LOG = Logger.getLogger(PathQueryResultHelper.class);

    /**
     * Return a list of string paths that are defined as WebConfig to be shown in results.  This
     * will include only attributes of the given class and not follow references.  Optionally
     * provide a prefix to for creating a view for references/collections.
     * @param type the class name to create a view for
     * @param model the model
     * @param webConfig we configuration
     * @param prefix a path to prefix the class
     * @return the configured view paths for the class
     */
    public static List<String> getDefaultViewForClass(String type, Model model, WebConfig webConfig,
            String prefix) {
        List<String> view = new ArrayList<String>();
        ClassDescriptor cld = model.getClassDescriptorByName(type);
        List<FieldConfig> fieldConfigs = FieldConfigHelper.getClassFieldConfigs(webConfig, cld);


        if (!StringUtils.isEmpty(prefix)) {
            try {
                // we can't add a subclass constraint, type must be same as the end of the prefix
                Path prefixPath = new Path(model, prefix);
                String prefixEndType = TypeUtil.unqualifiedName(prefixPath.getEndType().getName());
                if (!prefixEndType.equals(type)) {
                    throw new IllegalArgumentException("Mismatch between end type of prefix: "
                            + prefixEndType + " and type parameter: " + type);
                }
            } catch (PathException e) {
                LOG.error("Invalid path configured in webconfig for class: " + type);
            }
        } else {
            prefix = type;
        }

        for (FieldConfig fieldConfig : fieldConfigs) {
            String relPath = fieldConfig.getFieldExpr();
            try {
                Path path = new Path(model, prefix + "." + relPath);
                if (path.isOnlyAttribute()) {
                    view.add(path.getNoConstraintsString());
                }
            } catch (PathException e) {
                LOG.error("Invalid path configured in webconfig for class: " + type);
            }
        }
        if (view.size() == 0) {
            for (AttributeDescriptor att : cld.getAllAttributeDescriptors()) {
                view.add(prefix + "." + att.getName());
            }
        }
        return view;
    }


    public static PathQuery getQueryWithDefaultView(String type, Model model, WebConfig webConfig,
            String prefix) {
        PathQuery query = new PathQuery(model);
        ClassDescriptor cld = model.getClassDescriptorByName(type);
        List<FieldConfig> fieldConfigs = FieldConfigHelper.getClassFieldConfigs(webConfig, cld);

        if (prefix == null || prefix.length() <= 0) {
            try {
                // if the type is different to the end of the prefix path, add a subclass constraint
                Path prefixPath = new Path(model, prefix);
                String prefixEndType = TypeUtil.unqualifiedName(prefixPath.getEndType().getName());
                if (!prefixEndType.equals(type)) {
                    query.addConstraint(Constraints.type(prefix, type));
                }
            } catch (PathException e) {
                LOG.error("Invalid path configured in webconfig for class: " + type);
            }
            prefix = type;
        }

        for (FieldConfig fieldConfig : fieldConfigs) {
            if (fieldConfig.getShowInResults()) {
                String relPath = fieldConfig.getFieldExpr();
                try {
                    Path path = query.makePath(relPath);
                    if (!path.isOnlyAttribute()) {
                        query.setOuterJoinStatus(path.getNoConstraintsString(),
                                OuterJoinStatus.OUTER);
                    }
                    query.addView(path.getNoConstraintsString());
                } catch (PathException e) {
                    LOG.error("Invalid path configured in webconfig for class: " + type);
                }
            }
        }
        if (query.getView().size() == 0) {
            for (AttributeDescriptor att : cld.getAllAttributeDescriptors()) {
                query.addView(prefix + "." + att.getName());
            }
        }
        return query;
    }

    /**
     * Create a PathQuery to get the contents of an InterMineBag
     *
     * @param imBag the bag
     * @param webConfig the WebConfig
     * @param model the Model
     * @return a PathQuery
     */
    public static PathQuery makePathQueryForBag(InterMineBag imBag, WebConfig webConfig,
            Model model) {
        PathQuery query = new PathQuery(model);
        query.addViews(getDefaultViewForClass(imBag.getType(), model, webConfig, null));
        query.addConstraint(Constraints.in(imBag.getType(), imBag.getName()));
        return query;
    }

    /**
     * Create a PathQuery to get results for a collection of items from an InterMineObject
     *
     * @param webConfig the WebConfig
     * @param os the production ObjectStore
     * @param object the InterMineObject
     * @param referencedClassName the collection type
     * @param field the name of the field for the collection in the InterMineObject
     * @return a PathQuery
     */
    public static PathQuery makePathQueryForCollection(WebConfig webConfig, ObjectStore os,
            InterMineObject object,
            String referencedClassName, String field) {
        String className = TypeUtil.unqualifiedName(DynamicUtil.getSimpleClassName(object
                .getClass()));
        Path path;
        try {
            path = new Path(os.getModel(), className + "." + field);
        } catch (PathException e) {
            throw new IllegalArgumentException("Could not build path for \"" + className + "."
                    + field);
        }
        List<Class<?>> types = new ArrayList<Class<?>>();
        if (path.endIsCollection()) {
            types = queryForTypesInCollection(object, field, os);
        } else if (path.endIsReference()) {
            types.add(path.getLastClassDescriptor().getType());
        }
        return makePathQueryForCollectionForClass(webConfig, os.getModel(), object, field, types);
    }

    // find the subclasses that exist in the given collection
    private static List<Class<?>> queryForTypesInCollection(InterMineObject object, String field,
            ObjectStore os) {
        List<Class<?>> typesInCollection = new ArrayList<Class<?>>();
        Query query = new Query();
        QueryClass qc = new QueryClass(TypeUtil.getElementType(object.getClass(), field));
        query.addFrom(qc);
        query.addToSelect(new QueryField(qc, "class"));
        query.setDistinct(true);
        query.setConstraint(new ContainsConstraint(new QueryCollectionReference(object, field),
                ConstraintOp.CONTAINS, qc));
        for (Object o : os.executeSingleton(query)) {
            typesInCollection.add((Class<?>) o);
        }
        return typesInCollection;
    }

    /**
     * Called by makePathQueryForCollection
     *
     * @param webConfig the webConfig
     * @param model the object model
     * @param object the InterMineObject
     * @param field the name of the field for the collection in the InterMineObject
     * @param sr the list of classes and subclasses
     * @return a PathQuery
     */
    private static PathQuery makePathQueryForCollectionForClass(WebConfig webConfig, Model model,
            InterMineObject object, String field, List<Class<?>> sr) {
        Class<?> commonClass = CollectionUtil.findCommonSuperclass(sr);
        String typeOfCollection =
            TypeUtil.unqualifiedName(DynamicUtil.getSimpleClassName(commonClass));
        String startClass = TypeUtil.unqualifiedName(DynamicUtil.getSimpleClassName(object
                .getClass()));
        String collectionPath = startClass + "." + field;

        PathQuery pathQuery = getQueryWithDefaultView(typeOfCollection, model, webConfig,
                collectionPath);
        pathQuery.addConstraint(Constraints.eq(startClass + ".id", object.getId().toString()));
        pathQuery.addConstraint(Constraints.type(collectionPath, typeOfCollection));

        return pathQuery;
    }
}
