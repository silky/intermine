<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles"%>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="http://flymine.org/imutil" prefix="imutil"%>

<%-- jQuery.scrollTo relies on inlinetemplate.js! --%>

<div class="wrap">
  <span>Quick Links:</span>
  <div class="menu-wrap">
    <ul class="menu">
      <li>
        <html:link action="/objectDetails.do?id=${object.id}#summary"
        onclick="jQuery('a[name=summary]').scrollTo('slow', 'swing', 0);return false;"
        >Summary</html:link>
      </li>
      <c:forEach items="${categories}" var="aspect">
        <li>
          <c:set var="target" value="${fn:toLowerCase(aspect)}"/>
          <html:link
          action="/objectDetails.do?id=${object.id}#${target}"
          onclick="jQuery('a[name=${target}]').scrollTo('slow', 'swing', -21);return false;"
          >${aspect}</html:link>
        </li>
      </c:forEach>
      <li>
        <html:link action="/objectDetails.do?id=${object.id}#other"
        onclick="jQuery('a[name=other]').scrollTo('slow', 'swing', -21);return false;"
        >Other</html:link>
      </li>
    </ul>
  </div>
  <div class="clear">&nbsp;</div>
</div>