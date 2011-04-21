package org.intermine.web.struts;

/*
 * Copyright (C) 2002-2011 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;
import org.apache.struts.tiles.actions.TilesAction;
import org.intermine.api.InterMineAPI;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.search.Scope;
import org.intermine.api.tag.AspectTagUtil;
import org.intermine.api.template.TemplateManager;
import org.intermine.api.template.TemplateQuery;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.Model;
import org.intermine.pathquery.OrderElement;
import org.intermine.pathquery.Path;
import org.intermine.pathquery.PathException;
import org.intermine.pathquery.PathQuery;
import org.intermine.util.DynamicUtil;
import org.intermine.web.logic.results.ReportObject;
import org.intermine.web.logic.session.SessionMethods;

/**
 * Controller for the template list tile.
 * @author Richard Smith
 */
public class TemplateListController extends TilesAction
{
    private static final Logger LOG = Logger.getLogger(TemplateListController.class);
    /**
     * {@inheritDoc}
     */
    public ActionForward execute(ComponentContext context,
                                 @SuppressWarnings("unused") ActionMapping mapping,
                                 @SuppressWarnings("unused") ActionForm form,
                                 HttpServletRequest request,
                                 @SuppressWarnings("unused") HttpServletResponse response)
        throws Exception {
        final InterMineAPI im = SessionMethods.getInterMineAPI(request.getSession());
        Model model = im.getModel();
        String scope = (String) context.getAttribute("scope");
        String aspect = (String) context.getAttribute("placement");
        ReportObject object = (ReportObject) context.getAttribute("reportObject");

        if (AspectTagUtil.isAspectTag(aspect)) {
            aspect = AspectTagUtil.getAspect(aspect);
        }

        InterMineBag interMineIdBag = (InterMineBag) context.getAttribute("interMineIdBag");
        List<TemplateQuery> templates = null;
        TemplateManager templateManager = im.getTemplateManager();
        Set<String> allClasses = new HashSet<String>();
        if (StringUtils.equals(Scope.GLOBAL, scope)) {
            if (interMineIdBag != null) {
                allClasses.add(interMineIdBag.getType());
                templates = templateManager.getReportPageTemplatesForAspect(aspect, allClasses);
            } else if (object != null) {
                ClassDescriptor thisCld = model.getClassDescriptorByName(DynamicUtil
                        .getFriendlyName(object.getObject().getClass()));
                for (ClassDescriptor cld : model.getClassDescriptorsForClass(thisCld.getType())) {
                    allClasses.add(cld.getUnqualifiedName());
                }
                templates = templateManager.getReportPageTemplatesForAspect(aspect, allClasses);
            } else {
                templates = templateManager.getAspectTemplates(aspect);
            }

        } else if (StringUtils.equals(Scope.USER, scope)) {
            // no user template functionality implemented
        }

        for (TemplateQuery templateQuery : templates) {
            updateView(templateQuery);
        }
        request.setAttribute("templates", templates);

        return null;
    }

    /*
     * Removed from the view all the direct attributes that aren't editable constraints
     */
    private void updateView(TemplateQuery templateQuery) {
        List<String> viewPaths = templateQuery.getView();
        PathQuery pathQuery = templateQuery.getPathQuery();
        String rootClass = null;
        try {
            rootClass = templateQuery.getRootClass();
            for (String viewPath : viewPaths) {
                Path path = pathQuery.makePath(viewPath);
                if (path.getElementClassDescriptors().size() == 1
                    && path.getLastClassDescriptor().getUnqualifiedName().equals(rootClass)) {
                    if (templateQuery.getEditableConstraints(viewPath).isEmpty()) {
                        templateQuery.removeView(viewPath);
                        for (OrderElement oe : templateQuery.getOrderBy()) {
                            if (oe.getOrderPath().equals(viewPath)) {
                                templateQuery.removeOrderBy(viewPath);
                            }
                        }
                    }
                }
            }
        } catch (PathException pe) {
            LOG.error("Error updating the template's view", pe);
        }
    }
}
