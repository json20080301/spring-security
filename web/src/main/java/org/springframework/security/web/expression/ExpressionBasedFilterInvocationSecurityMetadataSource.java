package org.springframework.security.web.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.util.UrlMatcher;
import org.springframework.security.web.intercept.DefaultFilterInvocationSecurityMetadataSource;
import org.springframework.security.web.intercept.RequestKey;
import org.springframework.util.Assert;

/**
 * Expression-based <tt>FilterInvocationSecurityMetadataSource</tt>.
 *
 * @author Luke Taylor
 * @version $Id$
 * @since 2.5
 */
public final class ExpressionBasedFilterInvocationSecurityMetadataSource extends DefaultFilterInvocationSecurityMetadataSource {
    private final static Log logger = LogFactory.getLog(ExpressionBasedFilterInvocationSecurityMetadataSource.class);

    public ExpressionBasedFilterInvocationSecurityMetadataSource(UrlMatcher urlMatcher,
            LinkedHashMap<RequestKey, List<ConfigAttribute>> requestMap, WebSecurityExpressionHandler expressionHandler) {
        super(urlMatcher, processMap(requestMap, expressionHandler.getExpressionParser()));
        Assert.notNull(expressionHandler, "A non-null SecurityExpressionHandler is required");
    }

    private static LinkedHashMap<RequestKey, List<ConfigAttribute>> processMap(
            LinkedHashMap<RequestKey, List<ConfigAttribute>> requestMap, ExpressionParser parser) {
        Assert.notNull(parser, "SecurityExpressionHandler returned a null parser object");

        LinkedHashMap<RequestKey, List<ConfigAttribute>> requestToExpressionAttributesMap =
            new LinkedHashMap<RequestKey, List<ConfigAttribute>>(requestMap);

        for (Map.Entry<RequestKey, List<ConfigAttribute>> entry : requestMap.entrySet()) {
            RequestKey request = entry.getKey();
            Assert.isTrue(entry.getValue().size() == 1, "Expected a single expression attribute for " + request);
            ArrayList<ConfigAttribute> attributes = new ArrayList<ConfigAttribute>(1);
            String expression = entry.getValue().get(0).getAttribute();
            logger.debug("Adding web access control expression '" + expression + "', for " + request);
            try {
                attributes.add(new WebExpressionConfigAttribute(parser.parseExpression(expression)));
            } catch (ParseException e) {
                throw new IllegalArgumentException("Failed to parse expression '" + expression + "'");
            }

            requestToExpressionAttributesMap.put(request, attributes);
        }

        return requestToExpressionAttributesMap;
    }

}
