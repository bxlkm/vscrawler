package com.virjar.vscrawler.core.selector.model.selectables;

import com.virjar.vscrawler.core.selector.model.AbstractSelectable;

/**
 * Created by virjar on 17/6/30.
 */
@Deprecated
public class RegexNode extends AbstractSelectable {
    public RegexNode(String baseUrl, String rowText) {
        super(baseUrl, rowText);
    }

    @Override
    public Object createOrGetModel() {
        return null;
    }

    public RegexNode(String rowText) {
        super(rowText);
    }
}