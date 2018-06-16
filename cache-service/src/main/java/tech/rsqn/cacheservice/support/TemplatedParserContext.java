package tech.rsqn.cacheservice.support;

import org.springframework.expression.ParserContext;


/**
 * Created by IntelliJ IDEA.
 * User: dev
 * Date: 2/11/11
 *
 * To change this template use File | Settings | File Templates.
 */
public class TemplatedParserContext implements ParserContext {
    public String getExpressionPrefix() {
        return "${";
    }

    public String getExpressionSuffix() {
        return "}";
    }

    public boolean isTemplate() {
        return true;
    }
}
