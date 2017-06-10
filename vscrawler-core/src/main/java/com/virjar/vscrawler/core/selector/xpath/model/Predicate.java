package com.virjar.vscrawler.core.selector.xpath.model;

/*
 * Copyright 2014 Wang Haomiao<et.tw@163.com> Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

import com.virjar.vscrawler.core.selector.xpath.core.parse.expression.SyntaxNode;
import com.virjar.vscrawler.core.selector.xpath.util.XpathUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * xpath语法节点的谓语部分，即要满足的限定条件
 * 
 * @author github.com/zhegexiaohuozi [seimimaster@gmail.com]
 */
@Slf4j
public class Predicate {

    private SyntaxNode syntaxNode;
    private String predicateStr;

    public Predicate(String predicateStr, SyntaxNode syntaxNode) {
        this.predicateStr = predicateStr;
        this.syntaxNode = syntaxNode;
    }

    public boolean isValid(Element element) {
        Object ret = syntaxNode.calc(JXNode.e(element));
        if (ret == null) {
            return false;
        }

        if (ret instanceof Number) {
            int i = ((Number) ret).intValue();
            return XpathUtil.getElIndexInSameTags(element) == i;
        }

        if (ret instanceof Boolean) {
            return (boolean) ret;
        }

        if (ret instanceof CharSequence) {
            String s = ret.toString();
            Boolean booleanValue = BooleanUtils.toBooleanObject(s);
            if (booleanValue != null) {
                return booleanValue;
            }
            return StringUtils.isNotBlank(s);
        }

        log.warn("can not recognize predicate expression calc result:" + ret);
        return false;
    }
}